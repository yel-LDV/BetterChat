package dev.betterchats.core.platform;

import dev.betterchats.api.SoundSettings;

/** Reproduce sonidos por clave String (namespaced). */
public interface SoundPlayer {

    /** Debe llamarse desde {@link Scheduler#runForPlayer}. */
    void play(ChatPlayer player, SoundSettings sound);

    /**
     * Valida la clave al cargar la configuracion.
     *
     * @return null si es valida, o un texto con el problema/sugerencia.
     */
    String validate(String key);
}
