package dev.betterchats.core.platform;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/** Acceso a los jugadores conectados. Seguro desde cualquier hilo. */
public interface PlayerProvider {

    Optional<ChatPlayer> get(UUID uuid);

    /** Busca por nombre exacto (sin distinguir mayusculas). */
    Optional<ChatPlayer> getExact(String name);

    Collection<ChatPlayer> online();

    /** UUID de un jugador (conectado o no) por nombre, si la plataforma lo conoce. */
    Optional<UUID> lookupUuid(String name);

    /** Nombre conocido de un UUID (para listas de ignorados con jugadores offline). */
    Optional<String> lookupName(UUID uuid);
}
