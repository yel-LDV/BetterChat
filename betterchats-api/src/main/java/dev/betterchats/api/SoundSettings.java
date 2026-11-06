package dev.betterchats.api;

import java.util.Objects;

/**
 * Sonido que se reproduce al recibir un mensaje. {@link #getSound()} es una
 * clave namespaced (p. ej. {@code entity.experience_orb.pickup}); cada
 * adaptador la traduce a la de su version.
 */
@ApiStatus.Stable
public final class SoundSettings {

    private static final SoundSettings DISABLED = new SoundSettings(false, "", 1f, 1f);

    private final boolean enabled;
    private final String sound;
    private final float volume;
    private final float pitch;

    public SoundSettings(boolean enabled, String sound, float volume, float pitch) {
        this.enabled = enabled;
        this.sound = Objects.requireNonNull(sound, "sound");
        this.volume = volume;
        this.pitch = pitch;
    }

    public static SoundSettings disabled() {
        return DISABLED;
    }

    public boolean isEnabled() { return enabled; }
    public String getSound() { return sound; }
    public float getVolume() { return volume; }
    public float getPitch() { return pitch; }

    @Override
    public String toString() {
        return "SoundSettings{" + (enabled ? sound + " v=" + volume + " p=" + pitch : "disabled") + "}";
    }
}
