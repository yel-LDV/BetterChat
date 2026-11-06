package dev.betterchats.api.event;

import dev.betterchats.api.ApiStatus;
import dev.betterchats.api.ChatTeam;

import java.util.Set;
import java.util.UUID;

/** Antes de eliminar un equipo. Se dispara en el hilo que llama. */
@ApiStatus.Stable
public final class ChatTeamDeleteEvent extends BetterChatsEvent implements Cancellable {

    private final ChatTeam team;
    private final Set<UUID> members;
    private boolean cancelled;

    public ChatTeamDeleteEvent(ChatTeam team, Set<UUID> members) {
        this.team = team;
        this.members = members;
    }

    public ChatTeam getTeam() { return team; }
    /** Miembros en el momento de eliminar (copia inmutable). */
    public Set<UUID> getMembers() { return members; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
