package dev.betterchats.core.data;

import java.util.UUID;

/**
 * Backend de almacenamiento de {@link PlayerData}. Las implementaciones son
 * bloqueantes: {@link PlayerDataManager} las llama siempre desde su executor
 * de E/S, nunca desde el hilo principal.
 */
public interface StorageProvider {

    /** Carga los datos; si no existen devuelve un PlayerData vacio. */
    PlayerData load(UUID uuid) throws Exception;

    void save(PlayerData data) throws Exception;

    default void close() {
    }
}
