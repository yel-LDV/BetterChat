package dev.betterchats.core.team;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.betterchats.core.util.AtomicFiles;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Membresias de equipos (origen de verdad de {@code getMembers()}), en
 * {@code data/memberships.json}. Se guarda entera porque es pequena y asi
 * {@code getMembers()} funciona tambien con jugadores desconectados.
 */
public final class MembershipStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final Logger logger;
    private final Map<String, Set<UUID>> members = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public MembershipStore(Path dataFolder, Logger logger) {
        this.file = dataFolder.resolve("data").resolve("memberships.json");
        this.logger = logger;
    }

    public void load() {
        members.clear();
        if (!Files.exists(file)) return;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                Set<UUID> set = ConcurrentHashMap.newKeySet();
                for (JsonElement el : e.getValue().getAsJsonArray()) {
                    try {
                        set.add(UUID.fromString(el.getAsString()));
                    } catch (IllegalArgumentException ignored) {
                        // UUID corrupto: se descarta
                    }
                }
                members.put(e.getKey(), set);
            }
        } catch (IOException | RuntimeException ex) {
            logger.log(Level.SEVERE, "[BetterChats] No se pudo leer " + file + "; se empieza sin membresias", ex);
        }
    }

    /** Guarda si hubo cambios. Llamar desde el executor de E/S. */
    public synchronized void saveIfDirty() {
        if (!dirty) return;
        dirty = false;
        JsonObject root = new JsonObject();
        members.forEach((team, set) -> {
            if (set.isEmpty()) return;
            JsonArray arr = new JsonArray();
            set.forEach(u -> arr.add(u.toString()));
            root.add(team, arr);
        });
        try {
            AtomicFiles.writeString(file, GSON.toJson(root));
        } catch (IOException ex) {
            dirty = true;
            logger.log(Level.SEVERE, "[BetterChats] No se pudo guardar " + file, ex);
        }
    }

    public Set<UUID> members(String team) {
        Set<UUID> set = members.get(team);
        return set == null ? Set.of() : Set.copyOf(set);
    }

    public boolean isMember(String team, UUID player) {
        Set<UUID> set = members.get(team);
        return set != null && set.contains(player);
    }

    public boolean add(String team, UUID player) {
        boolean changed = members.computeIfAbsent(team, k -> ConcurrentHashMap.newKeySet()).add(player);
        if (changed) dirty = true;
        return changed;
    }

    public boolean remove(String team, UUID player) {
        Set<UUID> set = members.get(team);
        boolean changed = set != null && set.remove(player);
        if (changed) dirty = true;
        return changed;
    }

    public Set<UUID> removeTeam(String team) {
        Set<UUID> removed = members.remove(team);
        if (removed != null) dirty = true;
        return removed == null ? Set.of() : Set.copyOf(removed);
    }
}
