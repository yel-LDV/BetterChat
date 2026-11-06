package dev.betterchats.api.event;

import dev.betterchats.api.ApiStatus;

/** Suscripcion activa en el {@link EventBus}. */
@ApiStatus.Stable
public interface Subscription {

    void unsubscribe();

    boolean isActive();
}
