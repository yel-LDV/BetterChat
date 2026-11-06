package dev.betterchats.api.event;

import dev.betterchats.api.ApiStatus;
import dev.betterchats.api.spy.SpyMessage;

import java.util.Set;
import java.util.UUID;

/**
 * Antes de mostrar un mensaje espiado al staff. {@link #getSpies()} es
 * modificable: quita a alguien para que no lo vea.
 */
@ApiStatus.Experimental
public final class SpyMessageEvent extends BetterChatsEvent implements Cancellable {

    private final SpyMessage message;
    private final Set<UUID> spies;
    private boolean cancelled;

    public SpyMessageEvent(SpyMessage message, Set<UUID> spies) {
        this.message = message;
        this.spies = spies;
    }

    public SpyMessage getMessage() { return message; }
    public Set<UUID> getSpies() { return spies; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
