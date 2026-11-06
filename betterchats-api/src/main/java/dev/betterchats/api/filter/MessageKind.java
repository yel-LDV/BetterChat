package dev.betterchats.api.filter;

import dev.betterchats.api.ApiStatus;

/** Tipo de mensaje que pasa por los filtros. */
@ApiStatus.Stable
public enum MessageKind {
    /** Mensaje en un chat/equipo. */
    TEAM,
    /** Mensaje privado. */
    PRIVATE
}
