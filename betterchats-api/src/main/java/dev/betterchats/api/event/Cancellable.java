package dev.betterchats.api.event;

import dev.betterchats.api.ApiStatus;

@ApiStatus.Stable
public interface Cancellable {

    boolean isCancelled();

    void setCancelled(boolean cancelled);
}
