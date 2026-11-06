package dev.betterchats.legacy.listener;

import dev.betterchats.core.BetterChatsCore;
import dev.betterchats.core.platform.ChatPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/** Ciclo de vida: carga asincrona al entrar, guardado y limpieza al salir. */
public final class PlayerListener implements Listener {

    private final BetterChatsCore core;
    private final Function<Player, ChatPlayer> join;
    private final Consumer<UUID> quit;
    private final Runnable pluginsChanged;

    public PlayerListener(BetterChatsCore core, Function<Player, ChatPlayer> join, Consumer<UUID> quit,
                          Runnable pluginsChanged) {
        this.core = core;
        this.join = join;
        this.quit = quit;
        this.pluginsChanged = pluginsChanged;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            core.onPreLogin(event.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        core.onJoin(join.apply(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        core.onQuit(id);
        quit.accept(id);
    }

    /** Las suscripciones al bus de un plugin que se deshabilita se quitan solas. */
    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        core.events().unsubscribeAll(event.getPlugin());
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        pluginsChanged.run();
    }
}
