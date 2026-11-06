package dev.betterchats.core.team;

import dev.betterchats.api.ChatTeam;
import dev.betterchats.api.RateLimit;
import dev.betterchats.api.SoundSettings;
import dev.betterchats.api.TeamDefinition;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Equipo cargado: definicion inmutable + miembros desde {@link MembershipStore}. */
public final class TeamImpl implements ChatTeam {

    private final TeamDefinition def;
    private final MembershipStore memberships;

    public TeamImpl(TeamDefinition def, MembershipStore memberships) {
        this.def = def;
        this.memberships = memberships;
    }

    public TeamDefinition definition() {
        return def;
    }

    @Override public String getId() { return def.getId(); }
    @Override public String getDisplayName() { return def.getDisplayName(); }
    @Override public boolean isDefault() { return def.isDefault(); }
    @Override public boolean isStaffOnly() { return def.isStaffOnly(); }
    @Override public Optional<String> getPermission() { return Optional.ofNullable(def.getPermission()); }
    @Override public boolean hasImplicitMembers() { return def.hasImplicitMembers(); }
    @Override public Set<UUID> getMembers() { return memberships.members(def.getId()); }
    @Override public SoundSettings getSoundConfig() { return def.getSound(); }
    @Override public String getFormat() { return def.getFormat(); }
    @Override public int getPriority() { return def.getPriority(); }
    @Override public Set<String> getAllowedPlayerTags() { return def.getAllowedPlayerTags(); }
    @Override public Optional<RateLimit> getRateLimit() { return Optional.ofNullable(def.getRateLimit()); }
    @Override public boolean isPersistent() { return def.isPersistent(); }

    public boolean isMember(UUID player) {
        return memberships.isMember(def.getId(), player);
    }

    @Override
    public String toString() {
        return "ChatTeam{" + def.getId() + "}";
    }
}
