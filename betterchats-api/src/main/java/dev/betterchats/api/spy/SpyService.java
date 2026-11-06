package dev.betterchats.api.spy;

import dev.betterchats.api.ApiStatus;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Modo spy compartido: un unico {@code /spy} para ver los mensajes privados
 * de BetterChats y los canales que registren otros plugins.
 */
@ApiStatus.Experimental
public interface SpyService {

    /** Registra (o reemplaza) un canal. */
    void registerChannel(SpyChannel channel);

    void unregisterChannel(String channelId);

    Optional<SpyChannel> getChannel(String channelId);

    Collection<SpyChannel> getChannels();

    /**
     * Reenvia un mensaje a quienes espian su canal, tienen el permiso y no lo
     * recibieron ya. Seguro desde cualquier hilo. Si el canal no esta
     * registrado el mensaje se descarta.
     */
    void dispatch(SpyMessage message);

    boolean isSpying(UUID player, String channelId);

    void setSpying(UUID player, String channelId, boolean spying);
}
