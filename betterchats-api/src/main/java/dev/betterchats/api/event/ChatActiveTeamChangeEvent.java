package dev.betterchats.api.event;

import dev.betterchats.api.ApiStatus;
import dev.betterchats.api.ChatTeam;

import java.util.UUID;

/**
 * Antes de cambiar el chat activo de un jugador. Los cambios forzados
 * ({@link Reason#isForced()}) no se pueden cancelar: el jugador ya no tiene
 * acceso al chat anterior.
 */
@ApiStatus.Stable
public final class ChatActiveTeamChangeEvent extends BetterChatsEvent implements Cancellable {

    public enum Reason {
        COMMAND(false),
        API(false),
        PERMISSION_LOST(true),
        TEAM_DELETED(true),
        LEFT_TEAM(true);

        private final boolean forced;

        Reason(boolean forced) {
            this.forced = forced;
        }

        public boolean isForced() {
            return forced;
        }
    }

    private final UUID player;
    private final ChatTeam previous;
    private final ChatTeam next;
    private final Reason reason;
    private boolean cancelled;

    public ChatActiveTeamChangeEvent(UUID player, ChatTeam previous, ChatTeam next, Reason reason) {
        this.player = player;
        this.previous = previous;
        this.next = next;
        this.reason = reason;
    }

    public UUID getPlayer() { return player; }
    public ChatTeam getPrevious() { return previous; }
    public ChatTeam getNext() { return next; }
    public Reason getReason() { return reason; }

    @Override public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) {
        if (!reason.isForced()) this.cancelled = cancelled;
    }
}
