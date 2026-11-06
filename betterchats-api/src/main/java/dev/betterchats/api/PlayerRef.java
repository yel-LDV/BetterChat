package dev.betterchats.api;

import java.util.Objects;
import java.util.UUID;

/** Referencia inmutable a un jugador (UUID + nombre en el momento del evento). */
@ApiStatus.Stable
public final class PlayerRef {

    private final UUID uuid;
    private final String name;

    public PlayerRef(UUID uuid, String name) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.name = Objects.requireNonNull(name, "name");
    }

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }

    @Override
    public boolean equals(Object o) {
        return o instanceof PlayerRef && ((PlayerRef) o).uuid.equals(uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }

    @Override
    public String toString() {
        return name + "(" + uuid + ")";
    }
}
