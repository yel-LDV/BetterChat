package dev.betterchats.core.team;

import dev.betterchats.api.TeamDefinition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Equipos cargados. Las lecturas no bloquean (mapa inmutable que se reemplaza
 * entero), asi que el pipeline asincrono nunca ve un estado a medias.
 */
public final class TeamRegistry {

    private final MembershipStore memberships;
    private volatile Map<String, TeamImpl> teams = Map.of();
    private volatile TeamImpl defaultTeam;

    public TeamRegistry(MembershipStore memberships) {
        this.memberships = memberships;
    }

    /**
     * Reemplaza los equipos de archivo (recarga). Se conservan los creados por
     * API que no son persistentes, salvo que un archivo use el mismo id.
     */
    public synchronized void replaceFileTeams(List<TeamDefinition> fileTeams) {
        Map<String, TeamImpl> next = new LinkedHashMap<>();
        for (TeamDefinition d : fileTeams) next.put(d.getId(), new TeamImpl(d, memberships));
        for (TeamImpl t : teams.values()) {
            if (!t.isPersistent() && !next.containsKey(t.getId())) next.put(t.getId(), t);
        }
        publish(next);
    }

    public synchronized boolean add(TeamDefinition def) {
        if (teams.containsKey(def.getId())) return false;
        Map<String, TeamImpl> next = new LinkedHashMap<>(teams);
        next.put(def.getId(), new TeamImpl(def, memberships));
        publish(next);
        return true;
    }

    public synchronized Optional<TeamImpl> remove(String id) {
        TeamImpl t = teams.get(id);
        if (t == null || t.isDefault()) return Optional.empty();
        Map<String, TeamImpl> next = new LinkedHashMap<>(teams);
        next.remove(id);
        publish(next);
        return Optional.of(t);
    }

    private void publish(Map<String, TeamImpl> next) {
        TeamImpl def = null;
        for (TeamImpl t : next.values()) {
            if (t.isDefault()) {
                def = t;
                break;
            }
        }
        if (def == null) throw new IllegalStateException("No hay chat por defecto");
        this.teams = Map.copyOf(next);
        this.defaultTeam = def;
    }

    public Optional<TeamImpl> get(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(teams.get(id));
    }

    public TeamImpl defaultTeam() {
        return defaultTeam;
    }

    /** Ordenados por prioridad descendente y luego por id. */
    public List<TeamImpl> all() {
        List<TeamImpl> list = new ArrayList<>(teams.values());
        list.sort(Comparator.comparingInt(TeamImpl::getPriority).reversed().thenComparing(TeamImpl::getId));
        return list;
    }
}
