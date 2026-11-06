package dev.betterchats.legacy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.betterchats.core.platform.ChatPlayer;
import dev.betterchats.core.platform.PlayerProvider;
import dev.betterchats.core.util.AtomicFiles;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.entity.Player;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Jugadores conectados en un mapa concurrente propio (en 1.8 la lista de
 * Bukkit no es segura de recorrer desde el hilo del chat) y una cache
 * nombre &lt;-&gt; UUID en {@code data/names.json} para /ignore de jugadores
 * desconectados sin consultar a Mojang.
 */
final class BukkitPlayerProvider implements PlayerProvider {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final BukkitAudiences audiences;
    private final Logger logger;
    private final Path namesFile;
    private final Map<UUID, BukkitChatPlayer> online = new ConcurrentHashMap<>();
    private final Map<String, UUID> uuidByName = new ConcurrentHashMap<>();
    private final Map<UUID, String> nameByUuid = new ConcurrentHashMap<>();
    private volatile boolean namesDirty;

    BukkitPlayerProvider(BukkitAudiences audiences, Path dataFolder, Logger logger) {
        this.audiences = audiences;
        this.logger = logger;
        this.namesFile = dataFolder.resolve("data").resolve("names.json");
        loadNames();
    }

    BukkitChatPlayer add(Player player) {
        BukkitChatPlayer cp = new BukkitChatPlayer(player, audiences);
        online.put(player.getUniqueId(), cp);
        String lower = player.getName().toLowerCase(Locale.ROOT);
        UUID prev = uuidByName.put(lower, player.getUniqueId());
        String prevName = nameByUuid.put(player.getUniqueId(), player.getName());
        if (!player.getUniqueId().equals(prev) || !player.getName().equals(prevName)) {
            if (prevName != null && !prevName.equalsIgnoreCase(player.getName())) {
                uuidByName.remove(prevName.toLowerCase(Locale.ROOT), player.getUniqueId());
            }
            namesDirty = true;
        }
        return cp;
    }

    void remove(UUID uuid) {
        online.remove(uuid);
    }

    BukkitChatPlayer wrap(Player player) {
        BukkitChatPlayer cp = online.get(player.getUniqueId());
        return cp != null && cp.bukkit() == player ? cp : add(player);
    }

    @Override
    public Optional<ChatPlayer> get(UUID uuid) {
        return Optional.ofNullable(online.get(uuid));
    }

    @Override
    public Optional<ChatPlayer> getExact(String name) {
        for (BukkitChatPlayer p : online.values()) {
            if (p.name().equalsIgnoreCase(name)) return Optional.of(p);
        }
        return Optional.empty();
    }

    @Override
    public Collection<ChatPlayer> online() {
        return List.copyOf(online.values());
    }

    @Override
    public Optional<UUID> lookupUuid(String name) {
        return Optional.ofNullable(uuidByName.get(name.toLowerCase(Locale.ROOT)));
    }

    @Override
    public Optional<String> lookupName(UUID uuid) {
        return Optional.ofNullable(nameByUuid.get(uuid));
    }

    private void loadNames() {
        if (!Files.exists(namesFile)) return;
        try (Reader r = Files.newBufferedReader(namesFile, StandardCharsets.UTF_8)) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                try {
                    UUID id = UUID.fromString(e.getKey());
                    String name = e.getValue().getAsString();
                    nameByUuid.put(id, name);
                    uuidByName.put(name.toLowerCase(Locale.ROOT), id);
                } catch (IllegalArgumentException ignored) {
                    // entrada corrupta
                }
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "[BetterChats] No se pudo leer " + namesFile, ex);
        }
    }

    /** Llamar desde el executor de E/S. */
    void saveNamesIfDirty() {
        if (!namesDirty) return;
        namesDirty = false;
        JsonObject o = new JsonObject();
        nameByUuid.forEach((id, name) -> o.addProperty(id.toString(), name));
        try {
            AtomicFiles.writeString(namesFile, GSON.toJson(o));
        } catch (Exception ex) {
            namesDirty = true;
            logger.log(Level.WARNING, "[BetterChats] No se pudo guardar " + namesFile, ex);
        }
    }
}
