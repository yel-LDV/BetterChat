package dev.betterchats.core.command;

import dev.betterchats.core.BetterChatsCore;
import dev.betterchats.core.config.ConfigBundle;
import dev.betterchats.core.platform.CommandSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Registra los comandos con los alias de la configuracion mediante el
 * {@link dev.betterchats.core.platform.CommandRegistrar} (en tiempo de
 * ejecucion, no en plugin.yml).
 */
public final class CommandService {

    private final BetterChatsCore core;

    public CommandService(BetterChatsCore core) {
        this.core = core;
    }

    /** Hilo principal. */
    public void registerAll() {
        ConfigBundle cfg = core.config();
        boolean takeOver = cfg.main().takeOverExistingCommands();
        List<String> skipped = new ArrayList<>();

        register(spec(cfg.main().commandLabels("admin", List.of("betterchats", "bchats")),
                "betterchats.admin", "Administra BetterChats", takeOver, new AdminCommand(core)), skipped);
        register(spec(cfg.main().commandLabels("chat", List.of("chat", "channel", "ch")),
                null, "Cambia de chat o escribe en uno", takeOver, new ChatCommand(core)), skipped);
        register(spec(cfg.main().commandLabels("ignore", List.of("ignore")),
                "betterchats.ignore", "Ignora a un jugador", takeOver, new IgnoreCommand(core, true)), skipped);
        register(spec(cfg.main().commandLabels("unignore", List.of("unignore")),
                "betterchats.ignore", "Deja de ignorar a un jugador", takeOver, new IgnoreCommand(core, false)), skipped);

        var pm = cfg.privateMessages();
        if (pm.enabled()) {
            register(spec(pm.commandAliases(), "betterchats.msg", "Mensaje privado",
                    pm.takeOverExistingCommands(), new MsgCommand(core, false)), skipped);
            register(spec(pm.replyCommandAliases(), "betterchats.msg", "Responde al ultimo mensaje privado",
                    pm.takeOverExistingCommands(), new MsgCommand(core, true)), skipped);
        }
        if (cfg.spy().enabled()) {
            register(spec(cfg.spy().commandAliases(), null, "Modo spy del staff", takeOver,
                    new SpyCommand(core)), skipped);
        }
        if (cfg.chatColors().enabled()) {
            register(spec(cfg.chatColors().commandAliases(), "betterchats.chatcolor", "Colores y formatos de chat",
                    takeOver, new ChatColorCommand(core)), skipped);
        }
        if (!skipped.isEmpty()) {
            core.platform().logger().info("[BetterChats] Alias ocupados por otros plugins (se respetan; usa "
                    + "/betterchats:<alias> o activa takeOverExistingCommands): " + skipped);
        }
    }

    public void unregisterAll() {
        core.platform().commands().unregisterAll();
    }

    private void register(CommandSpec spec, List<String> skipped) {
        List<String> registered = core.platform().commands().register(spec);
        List<String> all = new ArrayList<>();
        all.add(spec.name());
        all.addAll(spec.aliases());
        for (String label : all) {
            if (!registered.contains(label.toLowerCase(java.util.Locale.ROOT))) skipped.add(label);
        }
    }

    private static CommandSpec spec(List<String> labels, String permission, String description, boolean takeOver,
                                    CommandSpec.CoreCommand executor) {
        return new CommandSpec(labels.get(0), labels.subList(1, labels.size()), permission, description, takeOver,
                executor);
    }
}
