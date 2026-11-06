package dev.betterchats.core.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.betterchats.core.util.AtomicFiles;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Un archivo por jugador: {@code playerdata/<uuid>.json}. */
public final class JsonStorageProvider implements StorageProvider {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path folder;

    public JsonStorageProvider(Path dataFolder) {
        this.folder = dataFolder.resolve("playerdata");
    }

    @Override
    public PlayerData load(UUID uuid) throws Exception {
        PlayerData data = new PlayerData(uuid);
        Path file = folder.resolve(uuid + ".json");
        if (!Files.exists(file)) return data;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            if (o.has("activeTeam") && !o.get("activeTeam").isJsonNull()) {
                data.activeTeam(o.get("activeTeam").getAsString());
            }
            if (o.has("ignored")) {
                for (JsonElement e : o.getAsJsonArray("ignored")) {
                    try {
                        data.ignore(UUID.fromString(e.getAsString()));
                    } catch (IllegalArgumentException ignored) {
                        // entrada corrupta
                    }
                }
            }
            if (o.has("spyChannels")) {
                for (JsonElement e : o.getAsJsonArray("spyChannels")) data.setSpying(e.getAsString(), true);
            }
            if (o.has("chatColor") && !o.get("chatColor").isJsonNull()) {
                data.chatColor(o.get("chatColor").getAsString());
            }
            if (o.has("chatFormats")) {
                for (JsonElement e : o.getAsJsonArray("chatFormats")) data.setChatFormat(e.getAsString(), true);
            }
        }
        data.markClean();
        return data;
    }

    @Override
    public void save(PlayerData data) throws Exception {
        JsonObject o = new JsonObject();
        o.addProperty("dataVersion", 1);
        if (data.activeTeam() != null) o.addProperty("activeTeam", data.activeTeam());
        JsonArray ignored = new JsonArray();
        data.ignored().forEach(u -> ignored.add(u.toString()));
        o.add("ignored", ignored);
        JsonArray spy = new JsonArray();
        data.spyChannels().forEach(spy::add);
        o.add("spyChannels", spy);
        if (data.chatColor() != null) o.addProperty("chatColor", data.chatColor());
        JsonArray formats = new JsonArray();
        data.chatFormats().forEach(formats::add);
        o.add("chatFormats", formats);
        AtomicFiles.writeString(folder.resolve(data.uuid() + ".json"), GSON.toJson(o));
    }
}
