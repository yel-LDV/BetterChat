package dev.betterchats.example;

import dev.betterchats.api.BetterChatsAPI;
import dev.betterchats.api.PrivateMessageResult;
import dev.betterchats.api.SoundSettings;
import dev.betterchats.api.TeamDefinition;
import dev.betterchats.api.event.ChatMessageEvent;
import dev.betterchats.api.event.EventPriority;
import dev.betterchats.api.filter.ChatFilter;
import dev.betterchats.api.filter.FilterResult;
import dev.betterchats.api.spy.SpyChannel;
import dev.betterchats.api.spy.SpyMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;

/**
 * Demuestra la API: crea un equipo en tiempo de ejecucion, registra un filtro,
 * escucha ChatMessageEvent, envia PMs y publica en un canal de spy propio.
 */
public final class ExamplePlugin extends JavaPlugin {

    private BetterChatsAPI api;
    private final ChatFilter noShouting = ctx -> {
        String m = ctx.getMessage();
        long upper = m.chars().filter(Character::isUpperCase).count();
        return m.length() >= 8 && upper * 10 >= m.length() * 7
                ? FilterResult.modify(m.toLowerCase()) : FilterResult.allow();
    };

    @Override
    public void onEnable() {
        RegisteredServiceProvider<BetterChatsAPI> rsp = Bukkit.getServicesManager().getRegistration(BetterChatsAPI.class);
        if (rsp == null) {
            getLogger().severe("BetterChats no esta disponible");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        api = rsp.getProvider();
        getLogger().info("BetterChats API " + api.getApiVersion());

        api.createTeam(TeamDefinition.builder("example")
                .displayName("<green>[Ejemplo]")
                .format("<green>[Ejemplo] <white>{playerName}<gray>: {message}")
                .allowTag("color")
                .sound(new SoundSettings(true, "entity.item.pickup", 1f, 1f))
                .build());

        api.registerFilter(noShouting, 500);
        api.getEventBus().subscribe(this, ChatMessageEvent.class, EventPriority.MONITOR,
                e -> getLogger().fine(e.getSender().getName() + " -> " + e.getTeam().getId() + ": " + e.getMessage()));
        api.getSpy().registerChannel(new SpyChannel("example.pm", "Ejemplo",
                null, "<dark_gray>[Spy·Ejemplo] <gray>{sender} → {target}: <white>{message}"));
    }

    @Override
    public void onDisable() {
        if (api == null) return;
        api.unregisterFilter(noShouting);
        api.getSpy().unregisterChannel("example.pm");
        api.deleteTeam("example");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player) || args.length == 0) return false;
        Player p = (Player) sender;
        if (args[0].equalsIgnoreCase("join")) {
            api.joinPlayerTeam(p.getUniqueId(), "example");
            api.setPlayerActiveTeam(p.getUniqueId(), "example");
            p.sendMessage("Ahora escribes en el chat de ejemplo.");
            return true;
        }
        if (args[0].equalsIgnoreCase("pm") && args.length >= 3) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) return false;
            String msg = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            PrivateMessageResult r = api.sendPrivateMessage(p.getUniqueId(), target.getUniqueId(), msg);
            if (r == PrivateMessageResult.SENT) {
                api.getSpy().dispatch(SpyMessage.builder("example.pm")
                        .sender(p.getUniqueId(), p.getName()).target(target.getName()).message(msg)
                        .alreadyReceived(p.getUniqueId()).alreadyReceived(target.getUniqueId()).build());
            }
            return true;
        }
        return false;
    }
}
