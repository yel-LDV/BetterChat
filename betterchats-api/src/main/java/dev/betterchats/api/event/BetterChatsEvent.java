package dev.betterchats.api.event;

import dev.betterchats.api.ApiStatus;

/**
 * Base de todos los eventos de BetterChats. Se publican en el
 * {@link EventBus} de la API, no en el de Bukkit, para no exponer tipos de
 * plataforma. Cada evento documenta en que hilo se dispara.
 */
@ApiStatus.Stable
public abstract class BetterChatsEvent {
}
