package dev.betterchats.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Migracion de config.json entre versiones ({@code configVersion}). Antes de
 * tocar el archivo se guarda una copia en {@code backups/}.
 *
 * <p>Cada version nueva agrega un paso en {@link #step(int, JsonObject, JsonObject)}.
 * Ademas, en cualquier migracion se agregan las claves nuevas que traiga el
 * archivo por defecto, sin pisar las del usuario.</p>
 */
public final class ConfigMigrator {

    public static final int CURRENT_VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private ConfigMigrator() {
    }

    /**
     * @return true si el archivo se migro y reescribio.
     */
    static boolean migrate(Path file, JsonObject root, JsonObject defaults, List<String> errors,
                           List<String> warnings) throws IOException {
        int version = root.has("configVersion") && root.get("configVersion").isJsonPrimitive()
                ? root.get("configVersion").getAsInt() : 0;
        if (version > CURRENT_VERSION) {
            errors.add(file.getFileName() + " -> configVersion: version " + version
                    + " no compatible (esta version de BetterChats entiende hasta " + CURRENT_VERSION
                    + "). Actualiza el plugin o restaura una copia de backups/.");
            return false;
        }
        if (version == CURRENT_VERSION) return false;

        Path backups = file.getParent().resolve("backups");
        Files.createDirectories(backups);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path backup = backups.resolve(file.getFileName() + "." + stamp + ".bak");
        Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);

        for (int v = version; v < CURRENT_VERSION; v++) {
            step(v, root, defaults);
        }
        mergeMissing(root, defaults);
        root.addProperty("configVersion", CURRENT_VERSION);
        Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        warnings.add(file.getFileName() + " -> configVersion: migrado de " + version + " a " + CURRENT_VERSION
                + " (copia en backups/" + backup.getFileName() + ")");
        return true;
    }

    /** Paso de la version {@code from} a {@code from + 1}. */
    private static void step(int from, JsonObject root, JsonObject defaults) {
        if (from == 0) {
            // v0 = archivo sin configVersion (borradores del plan): solo faltaban
            // secciones, que agrega mergeMissing.
            return;
        }
    }

    /** Agrega recursivamente las claves de {@code defaults} que no esten en {@code target}. */
    static void mergeMissing(JsonObject target, JsonObject defaults) {
        for (Map.Entry<String, JsonElement> e : defaults.entrySet()) {
            JsonElement mine = target.get(e.getKey());
            if (mine == null) {
                target.add(e.getKey(), e.getValue().deepCopy());
            } else if (mine.isJsonObject() && e.getValue().isJsonObject()) {
                mergeMissing(mine.getAsJsonObject(), e.getValue().getAsJsonObject());
            }
        }
    }
}
