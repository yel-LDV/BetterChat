package dev.betterchats.api.text;

import dev.betterchats.api.ApiStatus;
import dev.betterchats.api.PlayerRef;

/**
 * Token que se sustituye dentro del mensaje de un jugador por un
 * {@link RichText}, por ejemplo {@code [item]} -> item en mano con hover.
 *
 * <p>{@link #render(PlayerRef)} se llama en el hilo asincrono del chat (o en el
 * del comando para mensajes privados). Lee solo estado del propio jugador.</p>
 */
@ApiStatus.Experimental
public interface MessageToken {

    String getId();

    /** Texto literal que dispara el token, p. ej. {@code [item]}. */
    String getTrigger();

    default boolean isCaseInsensitive() {
        return true;
    }

    /** Permiso para usarlo, o null si cualquiera puede. */
    default String getPermission() {
        return null;
    }

    /** Maximo de sustituciones por mensaje (evita spam de hovers). */
    default int getMaxPerMessage() {
        return 3;
    }

    RichText render(PlayerRef player);
}
