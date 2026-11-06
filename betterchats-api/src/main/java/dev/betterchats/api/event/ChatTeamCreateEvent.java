package dev.betterchats.api.event;

import dev.betterchats.api.ApiStatus;
import dev.betterchats.api.TeamDefinition;

/** Antes de crear un equipo via API o comando. Se dispara en el hilo que llama. */
@ApiStatus.Stable
public final class ChatTeamCreateEvent extends BetterChatsEvent implements Cancellable {

    private final TeamDefinition definition;
    private boolean cancelled;

    public ChatTeamCreateEvent(TeamDefinition definition) {
        this.definition = definition;
    }

    public TeamDefinition getDefinition() { return definition; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
