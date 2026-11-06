package dev.betterchats.core.data;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cache de {@link PlayerData} de los jugadores conectados. Toda la E/S va por
 * un executor dedicado de un solo hilo (orden garantizado: un guardado al
 * salir nunca adelanta a una carga posterior del mismo jugador).
 */
public final class PlayerDataManager {

    private final StorageProvider storage;
    private final Logger logger;
    private final ExecutorService io;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public PlayerDataManager(StorageProvider storage, Logger logger) {
        this.storage = storage;
        this.logger = logger;
        this.io = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "BetterChats-IO");
            t.setDaemon(true);
            return t;
        });
    }

    public ExecutorService io() {
        return io;
    }

    /** Carga asincrona (al conectarse). */
    public CompletableFuture<PlayerData> loadAsync(UUID uuid) {
        PlayerData cached = cache.get(uuid);
        if (cached != null) return CompletableFuture.completedFuture(cached);
        return CompletableFuture.supplyAsync(() -> {
            PlayerData existing = cache.get(uuid);
            if (existing != null) return existing;
            PlayerData data;
            try {
                data = storage.load(uuid);
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "[BetterChats] No se pudieron cargar los datos de " + uuid
                        + "; se usan datos vacios (no se sobrescribiran)", ex);
                data = new PlayerData(uuid);
            }
            PlayerData prev = cache.putIfAbsent(uuid, data);
            return prev != null ? prev : data;
        }, io);
    }

    /**
     * Datos de un jugador. Si no estan en cache (jugador desconectado y una
     * llamada de API), se cargan bloqueando: evitar en el hilo principal.
     */
    public PlayerData get(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data != null) return data;
        return loadAsync(uuid).join();
    }

    /** Datos en cache o null, sin E/S (para el pipeline de chat). */
    public PlayerData cached(UUID uuid) {
        return cache.get(uuid);
    }

    /** Al salir: guarda si hay cambios y libera la cache. */
    public void unload(UUID uuid) {
        PlayerData data = cache.remove(uuid);
        if (data != null && data.isDirty()) io.execute(() -> saveNow(data));
    }

    /** Autoguardado: datos con cambios; los de jugadores desconectados se liberan. */
    public void autosave(java.util.function.Predicate<UUID> online) {
        for (PlayerData data : cache.values()) {
            if (data.isDirty()) io.execute(() -> saveNow(data));
            if (!online.test(data.uuid())) {
                // Cargado por una llamada de API para un jugador offline.
                cache.remove(data.uuid(), data);
            }
        }
    }

    /** Guarda todo y espera (onDisable). */
    public void shutdown() {
        for (PlayerData data : cache.values()) {
            if (data.isDirty()) io.execute(() -> saveNow(data));
        }
        io.shutdown();
        try {
            if (!io.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warning("[BetterChats] El guardado tardo mas de 10 s; algunos datos pueden no haberse escrito.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        storage.close();
        cache.clear();
    }

    private void saveNow(PlayerData data) {
        try {
            data.markClean();
            storage.save(data);
        } catch (Exception ex) {
            data.markDirty();
            logger.log(Level.SEVERE, "[BetterChats] No se pudieron guardar los datos de " + data.uuid(), ex);
        }
    }
}
