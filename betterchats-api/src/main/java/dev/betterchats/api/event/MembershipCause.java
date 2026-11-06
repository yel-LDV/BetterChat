package dev.betterchats.api.event;

import dev.betterchats.api.ApiStatus;

/** Por que un jugador entra o sale de un equipo. */
@ApiStatus.Stable
public enum MembershipCause {
    API,
    COMMAND,
    KICK,
    TEAM_DELETED
}
