package dev.betterchats.core.pm;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Ultimo interlocutor de cada jugador para /reply. Solo memoria. */
public final class ConversationTracker {

    private final Map<UUID, UUID> last = new ConcurrentHashMap<>();

    /** Actualiza en ambos sentidos. */
    public void record(UUID a, UUID b) {
        last.put(a, b);
        last.put(b, a);
    }

    public Optional<UUID> lastPartner(UUID player) {
        return Optional.ofNullable(last.get(player));
    }

    /** Al salir: se borra su entrada y las que apuntan a el. */
    public void forget(UUID player) {
        last.remove(player);
        last.values().removeIf(player::equals);
    }
}
