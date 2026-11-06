package dev.betterchats.api;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Un chat (equipo). Las instancias son vistas de solo lectura: los cambios se
 * hacen a traves de {@link BetterChatsAPI}.
 */
@ApiStatus.Stable
public interface ChatTeam {

    String getId();

    /** Nombre visible en MiniMessage. */
    String getDisplayName();

    boolean isDefault();

    boolean isStaffOnly();

    /** Permiso requerido para leer y escribir, si lo hay. */
    Optional<String> getPermission();

    /**
     * true si cualquiera con el permiso es miembro sin tener que unirse
     * (lo habitual en un chat de staff).
     */
    boolean hasImplicitMembers();

    /** Copia inmutable de los miembros explicitos. */
    Set<UUID> getMembers();

    SoundSettings getSoundConfig();

    /** Plantilla MiniMessage del formato. */
    String getFormat();

    int getPriority();

    /** Etiquetas MiniMessage que el jugador puede usar en este chat. */
    Set<String> getAllowedPlayerTags();

    Optional<RateLimit> getRateLimit();

    /** true si se guarda en disco ({@code chats/<id>.json}). */
    boolean isPersistent();
}
