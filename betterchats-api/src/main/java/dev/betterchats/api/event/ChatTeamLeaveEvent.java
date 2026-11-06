package dev.betterchats.api.event;

import dev.betterchats.api.ApiStatus;
import dev.betterchats.api.ChatTeam;

import java.util.UUID;

/**
 * Antes de que un jugador salga de un equipo. Con causa
 * {@link MembershipCause#TEAM_DELETED} no se puede cancelar.
 */
@ApiStatus.Stable
public final class ChatTeamLeaveEvent extends BetterChatsEvent implements Cancellable {

    private final UUID player;
    private final ChatTeam team;
    private final MembershipCause cause;
    private boolean cancelled;

    public ChatTeamLeaveEvent(UUID player, ChatTeam team, MembershipCause cause) {
        this.player = player;
        this.team = team;
        this.cause = cause;
    }

    public UUID getPlayer() { return player; }
    public ChatTeam getTeam() { return team; }
    public MembershipCause getCause() { return cause; }

    @Override public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) {
        if (cause != MembershipCause.TEAM_DELETED) this.cancelled = cancelled;
    }
}
