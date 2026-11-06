package dev.betterchats.api;

import dev.betterchats.api.event.EventBus;
import dev.betterchats.api.filter.ChatFilter;
import dev.betterchats.api.spy.SpyService;
import dev.betterchats.api.text.MessageToken;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Punto de entrada de la API de BetterChats.
 *
 * <p>Se publica en el ServicesManager de Bukkit:</p>
 * <pre>{@code
 * RegisteredServiceProvider<BetterChatsAPI> rsp =
 *     Bukkit.getServicesManager().getRegistration(BetterChatsAPI.class);
 * BetterChatsAPI api = rsp.getProvider();
 * }</pre>
 *
 * <p>Todos los metodos son seguros desde cualquier hilo salvo que se indique lo
 * contrario. Ningun metodo expone tipos de Bukkit.</p>
 */
@ApiStatus.Stable
public interface BetterChatsAPI {

    /** Version de la API (semver), p. ej. {@code "1.0.0"}. */
    String getApiVersion();

    // ------------------------------------------------------------------ equipos

    /**
     * Crea un equipo en tiempo de ejecucion. Si {@link TeamDefinition#isPersistent()}
     * es true tambien se guarda en {@code chats/<id>.json}.
     *
     * @return el equipo creado, o vacio si ya existia, no es valido o un listener
     *         cancelo {@link dev.betterchats.api.event.ChatTeamCreateEvent}.
     */
    Optional<ChatTeam> createTeam(TeamDefinition definition);

    /** Elimina un equipo. El equipo por defecto no se puede eliminar. */
    boolean deleteTeam(String teamId);

    Optional<ChatTeam> getTeam(String teamId);

    /** Equipos ordenados por prioridad descendente. */
    Collection<ChatTeam> getTeams();

    ChatTeam getDefaultTeam();

    // ---------------------------------------------------------------- jugadores

    /** Chat activo del jugador (el por defecto si no eligio otro). */
    ChatTeam getPlayerTeam(UUID player);

    /** Cambia el chat activo. Falla si el jugador no tiene acceso o un listener lo cancela. */
    boolean setPlayerActiveTeam(UUID player, String teamId);

    /** Agrega al jugador como miembro del equipo. */
    boolean joinPlayerTeam(UUID player, String teamId);

    /** Quita al jugador del equipo (y lo devuelve al chat por defecto si estaba activo). */
    boolean kickPlayerFromTeam(UUID player, String teamId);

    // ------------------------------------------------------- mensajes privados

    /**
     * Envia un mensaje privado aplicando todas las reglas de BetterChats
     * (ignorados, vanish, filtros, formato seguro, sonido y spy).
     */
    PrivateMessageResult sendPrivateMessage(UUID sender, UUID receiver, String message);

    Optional<UUID> getLastConversationPartner(UUID player);

    /** true si {@code player} tiene ignorado a {@code target}. */
    boolean isIgnoring(UUID player, UUID target);

    boolean ignore(UUID player, UUID target);

    boolean unignore(UUID player, UUID target);

    Set<UUID> getIgnored(UUID player);

    // ------------------------------------------------------------ extensiones

    /** Bus de eventos de BetterChats. */
    EventBus getEventBus();

    /** Servicio de espionaje (spy) para staff, compartido con otros plugins. */
    @ApiStatus.Experimental
    SpyService getSpy();

    /**
     * Registra un filtro de chat. Menor {@code priority} se ejecuta antes.
     * Los filtros incluidos (rate limit, duplicados, longitud) usan 100-300.
     */
    void registerFilter(ChatFilter filter, int priority);

    void unregisterFilter(ChatFilter filter);

    /**
     * Registra un token que se reemplaza dentro de los mensajes de los jugadores
     * por un componente enriquecido (p. ej. {@code [item]}).
     */
    @ApiStatus.Experimental
    void registerToken(MessageToken token);

    @ApiStatus.Experimental
    void unregisterToken(String tokenId);

    // ---------------------------------------------------------------- utilidad

    /** Recarga transaccional de la configuracion. */
    ReloadResult reload();
}
