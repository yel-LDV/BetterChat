package dev.betterchats.core.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lectura tipada de un JsonObject que acumula errores con la ruta exacta del
 * campo, para mensajes como
 * {@code chats/staff.json -> sound.pitch: debe estar entre 0.5 y 2.0 (valor: 3.0)}.
 */
final class JsonFields {

    private final String file;
    private final List<String> errors;
    private final List<String> warnings;

    JsonFields(String file, List<String> errors, List<String> warnings) {
        this.file = file;
        this.errors = errors;
        this.warnings = warnings;
    }

    void error(String path, String problem) {
        errors.add(file + " -> " + path + ": " + problem);
    }

    void warn(String path, String problem) {
        warnings.add(file + " -> " + path + ": " + problem);
    }

    JsonObject object(JsonObject parent, String key, String path) {
        JsonElement e = parent.get(key);
        if (e == null || e.isJsonNull()) return new JsonObject();
        if (!e.isJsonObject()) {
            error(path(path, key), "debe ser un objeto { ... }");
            return new JsonObject();
        }
        return e.getAsJsonObject();
    }

    String string(JsonObject o, String key, String path, String def) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) return def;
        if (!e.isJsonPrimitive() || !((JsonPrimitive) e).isString()) {
            error(path(path, key), "debe ser un texto entre comillas (valor: " + e + ")");
            return def;
        }
        return e.getAsString();
    }

    String requiredString(JsonObject o, String key, String path) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) {
            error(path(path, key), "campo obligatorio ausente");
            return "";
        }
        return string(o, key, path, "");
    }

    boolean bool(JsonObject o, String key, String path, boolean def) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) return def;
        if (!e.isJsonPrimitive() || !((JsonPrimitive) e).isBoolean()) {
            error(path(path, key), "debe ser true o false (valor: " + e + ")");
            return def;
        }
        return e.getAsBoolean();
    }

    int integer(JsonObject o, String key, String path, int def, int min, int max) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) return def;
        if (!e.isJsonPrimitive() || !((JsonPrimitive) e).isNumber()) {
            error(path(path, key), "debe ser un numero (valor: " + e + ")");
            return def;
        }
        double d = e.getAsDouble();
        if (d != Math.rint(d)) {
            error(path(path, key), "debe ser un numero entero (valor: " + e + ")");
            return def;
        }
        int v = (int) d;
        if (v < min || v > max) {
            error(path(path, key), "debe estar entre " + min + " y " + max + " (valor: " + v + ")");
            return def;
        }
        return v;
    }

    float decimal(JsonObject o, String key, String path, float def, float min, float max) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) return def;
        if (!e.isJsonPrimitive() || !((JsonPrimitive) e).isNumber()) {
            error(path(path, key), "debe ser un numero (valor: " + e + ")");
            return def;
        }
        float v = e.getAsFloat();
        if (v < min || v > max) {
            error(path(path, key), "debe estar entre " + min + " y " + max + " (valor: " + v + ")");
            return def;
        }
        return v;
    }

    List<String> stringList(JsonObject o, String key, String path, List<String> def) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) return def;
        if (!e.isJsonArray()) {
            error(path(path, key), "debe ser una lista [\"a\", \"b\"] (valor: " + e + ")");
            return def;
        }
        List<String> out = new ArrayList<>();
        JsonArray arr = e.getAsJsonArray();
        for (int i = 0; i < arr.size(); i++) {
            JsonElement item = arr.get(i);
            if (!item.isJsonPrimitive() || !((JsonPrimitive) item).isString()) {
                error(path(path, key) + "[" + i + "]", "debe ser un texto (valor: " + item + ")");
                continue;
            }
            out.add(item.getAsString());
        }
        return Collections.unmodifiableList(out);
    }

    Map<String, JsonObject> objectMap(JsonObject o, String key, String path) {
        Map<String, JsonObject> out = new LinkedHashMap<>();
        JsonObject obj = object(o, key, path);
        for (Map.Entry<String, JsonElement> en : obj.entrySet()) {
            if (!en.getValue().isJsonObject()) {
                error(path(path, key) + "." + en.getKey(), "debe ser un objeto { ... }");
                continue;
            }
            out.put(en.getKey(), en.getValue().getAsJsonObject());
        }
        return out;
    }

    static String path(String parent, String key) {
        return parent == null || parent.isEmpty() ? key : parent + "." + key;
    }
}
