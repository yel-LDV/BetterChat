package dev.betterchats.legacy;

import dev.betterchats.core.platform.ChatPlayer;
import dev.betterchats.core.platform.CommandRegistrar;
import dev.betterchats.core.platform.CommandSource;
import dev.betterchats.core.platform.CommandSpec;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registra comandos en el CommandMap en tiempo de ejecucion. Cada etiqueta
 * queda tambien como {@code betterchats:<etiqueta>}. Si otra la tiene y
 * {@code takeOver} es false, se respeta la del otro plugin.
 */
final class BukkitCommandRegistrar implements CommandRegistrar {

    static final String PREFIX = "betterchats";

    private final BukkitAudiences audiences;
    private final BukkitPlayerProvider players;
    private final Supplier<Component> noPermission;
    private final Logger logger;
    private final List<Registered> registered = new ArrayList<>();

    private record Registered(String label, Command command) {
    }

    BukkitCommandRegistrar(BukkitAudiences audiences, BukkitPlayerProvider players,
                           Supplier<Component> noPermission, Logger logger) {
        this.audiences = audiences;
        this.players = players;
        this.noPermission = noPermission;
        this.logger = logger;
    }

    @Override
    public synchronized List<String> register(CommandSpec spec) {
        CommandMap map = commandMap();
        Map<String, Command> known = knownCommands(map);
        if (map == null || known == null) return List.of();

        DynamicCommand cmd = new DynamicCommand(spec);
        List<String> labels = new ArrayList<>();
        labels.add(spec.name().toLowerCase(Locale.ROOT));
        for (String a : spec.aliases()) labels.add(a.toLowerCase(Locale.ROOT));

        // Lo que habia antes, para devolverle a otros plugins sus alias.
        Map<String, Command> before = new HashMap<>();
        for (String label : labels) before.put(label, known.get(label));

        // Registro por la via normal: asi el fork inicializa command.timings
        // (Paper/RecoilSpigot lo usan en dispatch y fallan con NPE si es null).
        try {
            map.register(PREFIX, cmd);
        } catch (RuntimeException ex) {
            logger.log(Level.WARNING, "[BetterChats] CommandMap.register fallo para /" + spec.name(), ex);
            cmd.register(map);
        }
        ensureTimings(cmd);

        List<String> ok = new ArrayList<>();
        for (String label : labels) {
            put(known, PREFIX + ":" + label, cmd);
            Command prev = before.get(label);
            if (prev != null && !isOurs(prev) && !spec.takeOver()) {
                known.put(label, prev);
                continue;
            }
            put(known, label, cmd);
            ok.add(label);
        }
        return ok;
    }

    /**
     * Respaldo por si register() no relleno el campo timings: lo crea con la
     * clase de timings que tenga el fork (co.aikar en Paper, CustomTimingsHandler
     * en Spigot). Si el campo no existe, no hace falta.
     */
    private void ensureTimings(Command cmd) {
        Field field;
        try {
            field = Command.class.getField("timings");
        } catch (NoSuchFieldException ex) {
            return;
        }
        try {
            if (field.get(cmd) != null) return;
            Object timing = null;
            try {
                Class<?> tm = Class.forName("co.aikar.timings.TimingsManager");
                timing = tm.getMethod("getCommandTiming", String.class, Command.class).invoke(null, PREFIX, cmd);
            } catch (ReflectiveOperationException ignored) {
                // no es un fork con co.aikar
            }
            if (timing == null && field.getType().getName().equals("org.spigotmc.CustomTimingsHandler")) {
                timing = field.getType().getConstructor(String.class).newInstance("** Command: " + cmd.getName());
            }
            if (timing != null) field.set(cmd, timing);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            logger.log(Level.WARNING, "[BetterChats] No se pudo inicializar timings de /" + cmd.getName(), ex);
        }
    }

    private void put(Map<String, Command> known, String label, Command cmd) {
        known.put(label, cmd);
        registered.add(new Registered(label, cmd));
    }

    private boolean isOurs(Command c) {
        return c instanceof DynamicCommand;
    }

    @Override
    public synchronized void unregisterAll() {
        CommandMap map = commandMap();
        Map<String, Command> known = knownCommands(map);
        if (known != null) {
            // Incluye las etiquetas que register() haya puesto por su cuenta.
            java.util.Set<Command> ours = new java.util.HashSet<>();
            for (Registered r : registered) ours.add(r.command());
            known.values().removeIf(ours::contains);
        }
        for (Registered r : registered) {
            if (map != null) r.command().unregister(map);
        }
        registered.clear();
    }

    private CommandMap commandMap() {
        try {
            return (CommandMap) Bukkit.getServer().getClass().getMethod("getCommandMap").invoke(Bukkit.getServer());
        } catch (ReflectiveOperationException ex) {
            logger.log(Level.SEVERE, "[BetterChats] No se pudo acceder al CommandMap; los comandos no funcionaran", ex);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Command> knownCommands(CommandMap map) {
        if (!(map instanceof SimpleCommandMap)) return null;
        try {
            Field f = SimpleCommandMap.class.getDeclaredField("knownCommands");
            f.setAccessible(true);
            return (Map<String, Command>) f.get(map);
        } catch (ReflectiveOperationException ex) {
            logger.log(Level.SEVERE, "[BetterChats] No se pudo leer knownCommands", ex);
            return null;
        }
    }

    /** Comando de Bukkit que delega en la logica del core. */
    private final class DynamicCommand extends Command {

        private final CommandSpec spec;

        DynamicCommand(CommandSpec spec) {
            super(spec.name(), spec.description(), "/" + spec.name(), spec.aliases());
            this.spec = spec;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            CommandSource source = source(sender);
            if (spec.permission() != null && !sender.hasPermission(spec.permission())) {
                source.sendMessage(noPermission.get());
                return true;
            }
            try {
                spec.executor().execute(source, label.contains(":") ? label.substring(label.indexOf(':') + 1) : label, args);
            } catch (Throwable ex) {
                logger.log(Level.SEVERE, "[BetterChats] Error ejecutando /" + label, ex);
            }
            return true;
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
            if (spec.permission() != null && !sender.hasPermission(spec.permission())) return List.of();
            try {
                return new ArrayList<>(spec.executor().complete(source(sender), args));
            } catch (Throwable ex) {
                return List.of();
            }
        }
    }

    private CommandSource source(CommandSender sender) {
        Optional<ChatPlayer> player = sender instanceof Player p
                ? Optional.of(players.wrap(p)) : Optional.empty();
        return new CommandSource() {
            @Override
            public String name() {
                return sender.getName();
            }

            @Override
            public boolean hasPermission(String permission) {
                return sender.hasPermission(permission);
            }

            @Override
            public void sendMessage(Component message) {
                audiences.sender(sender).sendMessage(message);
            }

            @Override
            public Optional<ChatPlayer> player() {
                return player;
            }
        };
    }
}
