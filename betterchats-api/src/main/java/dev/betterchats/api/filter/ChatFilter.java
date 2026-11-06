package dev.betterchats.api.filter;

import dev.betterchats.api.ApiStatus;

/**
 * Filtro de chat (anti-spam, censura, etc.). Se ejecuta en el hilo asincrono
 * del chat, por lo que debe ser thread-safe y no tocar el mundo.
 */
@ApiStatus.Stable
@FunctionalInterface
public interface ChatFilter {

    FilterResult filter(ChatContext context);

    /** Nombre para logs y depuracion. */
    default String getName() {
        return getClass().getSimpleName();
    }
}
