package dev.betterchats.legacy.listener;

import dev.betterchats.core.BetterChatsCore;
import dev.betterchats.core.ChatService;
import dev.betterchats.core.config.MainConfig;
import dev.betterchats.core.platform.ChatPlayer;
import dev.betterchats.core.util.Texts;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * Captura AsyncPlayerChatEvent y entrega segun la estrategia (seccion 2.3):
 *
 * <ul>
 *   <li><b>RENDERER</b>: el evento sigue vivo (logs, DiscordSRV y demas lo ven);
 *       se filtran los destinatarios y se fija el formato con la linea ya
 *       renderizada. En 1.8 no hay ChatRenderer, asi que el hover/clic se pierde.</li>
 *   <li><b>CANCEL</b>: se cancela y se envian componentes propios.</li>
 *   <li><b>HYBRID</b>: RENDERER para el chat por defecto sin hover/clic; CANCEL
 *       para chats privados/staff (que otros plugins no deben ver) y mensajes
 *       con tokens como [item].</li>
 * </ul>
 *
 * <p>Se registra con {@code ignoreCancelled}: si otro plugin ya desvio el
 * mensaje (p. ej. el chat de clan de vClans en LOWEST) no se toca.</p>
 */
public final class ChatListener implements Listener {

    private final Plugin plugin;
    private final BetterChatsCore core;
    private final Function<Player, ChatPlayer> wrap;

    public ChatListener(Plugin plugin, BetterChatsCore core, Function<Player, ChatPlayer> wrap) {
        this.plugin = plugin;
        this.core = core;
        this.wrap = wrap;
    }

    /** (Re)registra el listener con la prioridad de config.json. */
    public void register() {
        HandlerList.unregisterAll(this);
        EventPriority priority = EventPriority.valueOf(core.config().main().chat().listenerPriority());
        Bukkit.getPluginManager().registerEvent(AsyncPlayerChatEvent.class, this, priority,
                (listener, event) -> {
                    if (event instanceof AsyncPlayerChatEvent chat) handle(chat);
                }, plugin, true);
    }

    private void handle(AsyncPlayerChatEvent event) {
        ChatPlayer sender = wrap.apply(event.getPlayer());
        ChatService.Outcome out;
        try {
            out = core.chat().process(sender, event.getMessage());
        } catch (RuntimeException ex) {
            // Ante un fallo inesperado no se deja pasar el mensaje sin formato seguro.
            plugin.getLogger().log(Level.SEVERE, "[BetterChats] Error procesando el chat de " + sender.name(), ex);
            event.setCancelled(true);
            return;
        }
        if (out.cancelled()) {
            event.setCancelled(true);
            return;
        }

        MainConfig.Strategy strategy = core.config().main().chat().strategy();
        boolean renderer = strategy == MainConfig.Strategy.RENDERER
                || (strategy == MainConfig.Strategy.HYBRID && out.team().isDefault() && !out.rich());

        if (renderer && applyRenderer(event, out)) {
            core.chat().playSounds(sender, out);
            return;
        }

        event.setCancelled(true);
        for (ChatPlayer p : out.recipients()) p.sendMessage(out.line());
        core.platform().sendConsole(out.line());
        core.chat().playSounds(sender, out);
    }

    /** @return false si no se pudo (destinatarios inmutables): se cae a CANCEL. */
    private boolean applyRenderer(AsyncPlayerChatEvent event, ChatService.Outcome out) {
        Set<UUID> allowed = new HashSet<>();
        for (ChatPlayer p : out.recipients()) allowed.add(p.uuid());
        try {
            event.getRecipients().removeIf(p -> !allowed.contains(p.getUniqueId()));
        } catch (UnsupportedOperationException ex) {
            return false;
        }
        // La linea ya esta completa: el formato no lleva %1$s/%2$s, solo hay que escapar '%'.
        event.setFormat(Texts.legacy(out.line()).replace("%", "%%"));
        return true;
    }
}
