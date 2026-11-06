package dev.betterchats.api.event;

import dev.betterchats.api.ApiStatus;
import dev.betterchats.api.PlayerRef;

import java.util.Objects;

/** Antes de enviar un mensaje privado de BetterChats. El mensaje es modificable. */
@ApiStatus.Stable
public final class PrivateMessageEvent extends BetterChatsEvent implements Cancellable {

    private final PlayerRef sender;
    private final PlayerRef receiver;
    private String message;
    private boolean cancelled;

    public PrivateMessageEvent(PlayerRef sender, PlayerRef receiver, String message) {
        this.sender = sender;
        this.receiver = receiver;
        this.message = message;
    }

    public PlayerRef getSender() { return sender; }
    public PlayerRef getReceiver() { return receiver; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = Objects.requireNonNull(message, "message"); }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
