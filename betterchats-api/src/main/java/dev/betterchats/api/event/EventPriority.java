package dev.betterchats.api.event;

import dev.betterchats.api.ApiStatus;

/**
 * Prioridades habituales para {@link EventBus#subscribe}. Menor valor se
 * ejecuta antes; {@link #MONITOR} es solo para observar el resultado final.
 */
@ApiStatus.Stable
public final class EventPriority {

    public static final int LOWEST = -200;
    public static final int LOW = -100;
    public static final int NORMAL = 0;
    public static final int HIGH = 100;
    public static final int HIGHEST = 200;
    public static final int MONITOR = 1000;

    private EventPriority() {
    }
}
