package dev.betterchats.api.event;

import dev.betterchats.api.ApiStatus;
import dev.betterchats.api.ChatTeam;
import dev.betterchats.api.PlayerRef;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Antes de formatear y entregar un mensaje de chat. Se dispara en el hilo
 * asincrono del chat: no toques el mundo desde aqui.
 *
 * <p>El mensaje y los destinatarios son modificables.</p>
 */
@ApiStatus.Stable
public final class ChatMessageEvent extends BetterChatsEvent implements Cancellable {

    private final PlayerRef sender;
    private final ChatTeam team;
    private final Set<UUID> recipients;
    private String message;
    private boolean cancelled;

    public ChatMessageEvent(PlayerRef sender, ChatTeam team, String message, Set<UUID> recipients) {
        this.sender = sender;
        this.team = team;
        this.message = message;
        this.recipients = recipients;
    }

    public PlayerRef getSender() { return sender; }
    public ChatTeam getTeam() { return team; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = Objects.requireNonNull(message, "message"); }
    /** Conjunto mutable de destinatarios. */
    public Set<UUID> getRecipients() { return recipients; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
