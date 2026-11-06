package dev.betterchats.api;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Definicion inmutable de un equipo, usada para crearlo via
 * {@link BetterChatsAPI#createTeam(TeamDefinition)}.
 */
@ApiStatus.Stable
public final class TeamDefinition {

    private final String id;
    private final String displayName;
    private final boolean isDefault;
    private final boolean staffOnly;
    private final String permission;
    private final boolean implicitMembers;
    private final int priority;
    private final String format;
    private final Set<String> allowedPlayerTags;
    private final SoundSettings sound;
    private final RateLimit rateLimit;
    private final boolean persistent;

    private TeamDefinition(Builder b) {
        this.id = b.id;
        this.displayName = b.displayName == null ? b.id : b.displayName;
        this.isDefault = b.isDefault;
        this.staffOnly = b.staffOnly;
        this.permission = b.permission;
        this.implicitMembers = b.implicitMembers;
        this.priority = b.priority;
        this.format = b.format;
        this.allowedPlayerTags = Collections.unmodifiableSet(new LinkedHashSet<String>(b.allowedPlayerTags));
        this.sound = b.sound;
        this.rateLimit = b.rateLimit;
        this.persistent = b.persistent;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public boolean isDefault() { return isDefault; }
    public boolean isStaffOnly() { return staffOnly; }
    /** Puede ser null. */
    public String getPermission() { return permission; }
    public boolean hasImplicitMembers() { return implicitMembers; }
    public int getPriority() { return priority; }
    public String getFormat() { return format; }
    public Set<String> getAllowedPlayerTags() { return allowedPlayerTags; }
    public SoundSettings getSound() { return sound; }
    /** Puede ser null. */
    public RateLimit getRateLimit() { return rateLimit; }
    public boolean isPersistent() { return persistent; }

    public static final class Builder {
        private final String id;
        private String displayName;
        private boolean isDefault;
        private boolean staffOnly;
        private String permission;
        private boolean implicitMembers;
        private int priority;
        private String format = "<gray>{playerName} <dark_gray>» <white>{message}";
        private final Set<String> allowedPlayerTags = new LinkedHashSet<String>();
        private SoundSettings sound = SoundSettings.disabled();
        private RateLimit rateLimit;
        private boolean persistent;

        private Builder(String id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public Builder displayName(String v) { this.displayName = v; return this; }
        public Builder isDefault(boolean v) { this.isDefault = v; return this; }
        public Builder staffOnly(boolean v) { this.staffOnly = v; return this; }
        public Builder permission(String v) { this.permission = v; return this; }
        public Builder implicitMembers(boolean v) { this.implicitMembers = v; return this; }
        public Builder priority(int v) { this.priority = v; return this; }
        public Builder format(String v) { this.format = Objects.requireNonNull(v, "format"); return this; }
        public Builder allowTag(String tag) { this.allowedPlayerTags.add(tag); return this; }
        public Builder sound(SoundSettings v) { this.sound = Objects.requireNonNull(v, "sound"); return this; }
        public Builder rateLimit(RateLimit v) { this.rateLimit = v; return this; }
        public Builder persistent(boolean v) { this.persistent = v; return this; }

        public TeamDefinition build() {
            return new TeamDefinition(this);
        }
    }
}
