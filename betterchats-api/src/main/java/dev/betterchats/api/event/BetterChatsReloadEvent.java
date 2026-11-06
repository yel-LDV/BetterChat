package dev.betterchats.api.event;

import dev.betterchats.api.ApiStatus;
import dev.betterchats.api.ReloadResult;

/** Tras recargar la configuracion (con exito o no). No cancelable. */
@ApiStatus.Stable
public final class BetterChatsReloadEvent extends BetterChatsEvent {

    private final ReloadResult result;

    public BetterChatsReloadEvent(ReloadResult result) {
        this.result = result;
    }

    public ReloadResult getResult() { return result; }
}
