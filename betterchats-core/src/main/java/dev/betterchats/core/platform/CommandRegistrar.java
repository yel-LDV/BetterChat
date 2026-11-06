package dev.betterchats.core.platform;

import java.util.List;

/** Registra comandos y alias en tiempo de ejecucion (no solo en plugin.yml). */
public interface CommandRegistrar {

    /**
     * Registra el comando.
     *
     * @return las etiquetas que quedaron registradas (sin las ocupadas por otros plugins).
     */
    List<String> register(CommandSpec spec);

    /** Quita todos los comandos registrados por BetterChats. */
    void unregisterAll();
}
