package dev.betterchats.core.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.betterchats.api.RateLimit;
import dev.betterchats.api.SoundSettings;
import dev.betterchats.api.TeamDefinition;
import dev.betterchats.core.format.LegacyColors;
import dev.betterchats.core.format.TagCatalog;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee y valida TODA la configuracion. Si hay algun error devuelve un
 * resultado sin bundle y el core conserva la configuracion anterior.
 */
public final class ConfigLoader {

    public static final Set<String> TEAM_PLACEHOLDERS = Set.of(
            "playerName", "displayName", "team", "player_location", "world", "x", "y", "z", "message");
    public static final Set<String> PM_PLACEHOLDERS = Set.of("sender", "receiver", "message");
    public static final Set<String> SPY_PLACEHOLDERS = Set.of("sender", "target", "message", "channel");

    private static final Pattern BRACE = Pattern.compile("\\{([A-Za-z0-9_]+)}");
    private static final Pattern TEAM_ID = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final List<String> DEFAULT_TEAM_FILES = List.of("default.json", "staff.json");

    /** Resultado de una carga. {@code bundle} es null si hubo errores. */
    public record Result(ConfigBundle bundle, List<String> errors, List<String> warnings) {
        public boolean success() {
            return bundle != null;
        }
    }

    private final Path folder;
    private final Function<String, InputStream> resources;
    private final Function<String, String> soundValidator;

    /**
     * @param resources      lee recursos empaquetados (defaults/..., lang/...)
     * @param soundValidator devuelve null si la clave es valida o el problema
     */
    public ConfigLoader(Path folder, Function<String, InputStream> resources, Function<String, String> soundValidator) {
        this.folder = folder;
        this.resources = resources;
        this.soundValidator = soundValidator;
    }

    public Result load() {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        try {
            copyDefaults();
        } catch (IOException ex) {
            errors.add("No se pudieron crear los archivos por defecto en " + folder + ": " + ex.getMessage());
            return new Result(null, errors, warnings);
        }

        MainConfig main = null;
        PrivateMessagesConfig pm = null;
        SpyConfig spy = null;
        List<TeamDefinition> teams = List.of();
        Lang lang = null;

        JsonObject mainJson = read(folder.resolve("config.json"), "config.json", errors);
        if (mainJson != null) {
            try {
                JsonObject defaults = readResource("defaults/config.json");
                ConfigMigrator.migrate(folder.resolve("config.json"), mainJson, defaults, errors, warnings);
            } catch (IOException ex) {
                errors.add("config.json -> configVersion: no se pudo migrar (" + ex.getMessage() + ")");
            }
            main = parseMain(mainJson, new JsonFields("config.json", errors, warnings));
        }

        JsonObject pmJson = read(folder.resolve("private_messages.json"), "private_messages.json", errors);
        if (pmJson != null) {
            pm = parsePrivateMessages(pmJson, new JsonFields("private_messages.json", errors, warnings), main);
        }

        JsonObject spyJson = read(folder.resolve("spy.json"), "spy.json", errors);
        if (spyJson != null) {
            spy = parseSpy(spyJson, new JsonFields("spy.json", errors, warnings), main);
        }

        ChatColorsConfig colors = null;
        JsonObject colorsJson = read(folder.resolve("chat_colors.json"), "chat_colors.json", errors);
        if (colorsJson != null) {
            colors = parseChatColors(colorsJson, new JsonFields("chat_colors.json", errors, warnings));
        }

        if (main != null) {
            teams = loadTeams(main, errors, warnings);
            lang = loadLang(main.language(), main.chat().legacyColorsInFormats(), errors, warnings);
        }

        if (!errors.isEmpty() || main == null || pm == null || spy == null || colors == null || lang == null) {
            return new Result(null, errors, warnings);
        }
        return new Result(new ConfigBundle(main, pm, spy, colors, teams, lang), errors, warnings);
    }

    // ------------------------------------------------------------- defaults

    private void copyDefaults() throws IOException {
        Files.createDirectories(folder);
        copyIfMissing("defaults/config.json", folder.resolve("config.json"));
        copyIfMissing("defaults/private_messages.json", folder.resolve("private_messages.json"));
        copyIfMissing("defaults/spy.json", folder.resolve("spy.json"));
        copyIfMissing("defaults/chat_colors.json", folder.resolve("chat_colors.json"));
        Path chats = folder.resolve("chats");
        if (!Files.isDirectory(chats)) {
            Files.createDirectories(chats);
            for (String f : DEFAULT_TEAM_FILES) {
                copyIfMissing("defaults/chats/" + f, chats.resolve(f));
            }
        }
        Path langDir = folder.resolve("lang");
        Files.createDirectories(langDir);
        copyIfMissing("lang/es.json", langDir.resolve("es.json"));
        copyIfMissing("lang/en.json", langDir.resolve("en.json"));
    }

    private void copyIfMissing(String resource, Path target) throws IOException {
        if (Files.exists(target)) return;
        try (InputStream in = resources.apply(resource)) {
            if (in == null) throw new IOException("recurso " + resource + " no encontrado en el jar");
            Files.copy(in, target);
        }
    }

    private JsonObject readResource(String path) throws IOException {
        try (InputStream in = resources.apply(path)) {
            if (in == null) return new JsonObject();
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(r).getAsJsonObject();
            }
        }
    }

    private JsonObject read(Path file, String display, List<String> errors) {
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(r);
            if (!el.isJsonObject()) {
                errors.add(display + " -> (raiz): el archivo debe ser un objeto JSON { ... }");
                return null;
            }
            return el.getAsJsonObject();
        } catch (JsonParseException ex) {
            String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
            errors.add(display + " -> JSON mal formado: " + msg
                    + " (revisa comas, comillas y llaves cerca de esa linea)");
            return null;
        } catch (IOException ex) {
            errors.add(display + " -> no se pudo leer: " + ex.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------ config.json

    private MainConfig parseMain(JsonObject o, JsonFields f) {
        int version = f.integer(o, "configVersion", "", ConfigMigrator.CURRENT_VERSION, 0, 1000);
        String language = f.string(o, "language", "", "es").toLowerCase(Locale.ROOT);

        JsonObject p = f.object(o, "persistence", "");
        String type = f.string(p, "type", "persistence", "json").toLowerCase(Locale.ROOT);
        if (!type.equals("json")) {
            f.error("persistence.type", "tipo '" + type + "' no soportado en esta version (usa \"json\")");
        }
        int autosave = f.integer(p, "autosaveSeconds", "persistence", 300, 10, 86400);

        JsonObject c = f.object(o, "chat", "");
        String rawStrategy = f.string(c, "strategy", "chat", "HYBRID").toUpperCase(Locale.ROOT);
        MainConfig.Strategy strategy;
        try {
            strategy = MainConfig.Strategy.valueOf(rawStrategy);
        } catch (IllegalArgumentException ex) {
            f.error("chat.strategy", "valor '" + rawStrategy + "' invalido (usa RENDERER, CANCEL o HYBRID)");
            strategy = MainConfig.Strategy.HYBRID;
        }
        boolean fallback = f.bool(c, "fallbackToDefaultOnPermissionLoss", "chat", true);
        String lost = f.string(c, "permissionLostMessage", "chat", "<red>Ya no tienes acceso a ese chat.");
        checkMiniMessage(f, "chat.permissionLostMessage", lost, false, Set.of());
        String priority = f.string(c, "listenerPriority", "chat", "NORMAL").toUpperCase(Locale.ROOT);
        if (!Set.of("LOWEST", "LOW", "NORMAL", "HIGH", "HIGHEST").contains(priority)) {
            f.error("chat.listenerPriority", "valor '" + priority + "' invalido (LOWEST, LOW, NORMAL, HIGH o HIGHEST)");
            priority = "NORMAL";
        }
        boolean legacy = f.bool(c, "legacyColorsInFormats", "chat", true);

        JsonObject s = f.object(o, "security", "");
        Set<String> defTags = tagSet(f, f.stringList(s, "defaultAllowedPlayerTags", "security", List.of()),
                "security.defaultAllowedPlayerTags");
        String bypass = f.string(s, "bypassPermission", "security", "betterchats.tags.all");
        boolean blockLookups = f.bool(s, "blockLookupPatterns", "security", true);

        JsonObject a = f.object(o, "antiSpam", "");
        boolean asEnabled = f.bool(a, "enabled", "antiSpam", true);
        int dup = f.integer(a, "duplicateCooldownSeconds", "antiSpam", 3, 0, 3600);
        int maxLen = f.integer(a, "maxLength", "antiSpam", 256, 1, 32767);
        String asBypass = f.string(a, "bypassPermission", "antiSpam", "betterchats.bypass.antispam");

        JsonObject sn = f.object(o, "snapshot", "");
        int refresh = f.integer(sn, "refreshTicks", "snapshot", 20, 1, 12000);
        List<String> syncPh = f.stringList(sn, "placeholders", "snapshot", List.of());
        for (int i = 0; i < syncPh.size(); i++) {
            String ph = syncPh.get(i);
            if (!(ph.startsWith("%") && ph.endsWith("%") && ph.length() > 2)) {
                f.error("snapshot.placeholders[" + i + "]", "debe tener la forma %placeholder% (valor: " + ph + ")");
            }
        }

        Map<String, List<String>> commands = new LinkedHashMap<>();
        JsonObject cmds = f.object(o, "commands", "");
        for (String key : List.of("admin", "chat", "ignore", "unignore")) {
            List<String> labels = f.stringList(cmds, key, "commands", List.of());
            for (String l : labels) {
                if (!l.matches("[a-zA-Z0-9_-]+")) {
                    f.error("commands." + key, "alias '" + l + "' invalido (solo letras, numeros, _ y -)");
                }
            }
            commands.put(key, labels);
        }
        boolean takeOver = f.bool(cmds, "takeOverExistingCommands", "commands", false);

        boolean devAutoReload = f.bool(o, "devAutoReload", "", false);

        return new MainConfig(version, language, new MainConfig.Persistence(type, autosave),
                new MainConfig.Chat(strategy, fallback, lost, priority, legacy),
                new MainConfig.Security(defTags, bypass, blockLookups),
                new MainConfig.AntiSpam(asEnabled, dup, maxLen, asBypass),
                new MainConfig.Snapshot(refresh, syncPh), commands, takeOver, devAutoReload);
    }

    // -------------------------------------------------- private_messages.json

    private PrivateMessagesConfig parsePrivateMessages(JsonObject o, JsonFields f, MainConfig main) {
        boolean legacy = main == null || main.chat().legacyColorsInFormats();
        boolean enabled = f.bool(o, "enabled", "", true);
        List<String> aliases = f.stringList(o, "commandAliases", "", List.of("msg", "whisper", "tell", "w"));
        List<String> reply = f.stringList(o, "replyCommandAliases", "", List.of("r", "reply"));
        if (enabled && aliases.isEmpty()) f.error("commandAliases", "debe tener al menos un alias");
        if (enabled && reply.isEmpty()) f.error("replyCommandAliases", "debe tener al menos un alias");
        boolean takeOver = f.bool(o, "takeOverExistingCommands", "", false);

        String in = deprecatedPm(f, "formatIncoming", f.requiredString(o, "formatIncoming", ""));
        String out = deprecatedPm(f, "formatOutgoing", f.requiredString(o, "formatOutgoing", ""));
        checkTemplate(f, "formatIncoming", in, PM_PLACEHOLDERS, legacy, true);
        checkTemplate(f, "formatOutgoing", out, PM_PLACEHOLDERS, legacy, true);

        SoundSettings sound = parseSound(f, f.object(o, "sound", ""), "sound");
        boolean vanish = f.bool(o, "respectVanish", "", true);
        String vanishSee = f.string(o, "vanishSeePermission", "", "betterchats.vanish.see");
        boolean self = f.bool(o, "allowSelfMessage", "", false);
        boolean notifyIgnored = f.bool(o, "notifyIgnoredSender", "", false);
        RateLimit rateLimit = null;
        if (o.has("rateLimit") && !o.get("rateLimit").isJsonNull()) {
            JsonObject rl = f.object(o, "rateLimit", "");
            rateLimit = new RateLimit(f.integer(rl, "messages", "rateLimit", 5, 1, 1000),
                    f.integer(rl, "perSeconds", "rateLimit", 10, 1, 3600));
        }

        JsonObject sp = f.object(o, "spy", "");
        boolean spyEnabled = f.bool(sp, "enabled", "spy", true);
        String spyPerm = f.string(sp, "permission", "spy", "betterchats.spy");
        String spyFormat = deprecatedPm(f, "spy.format", f.string(sp, "format", "spy",
                "<dark_gray>[Spy] <gray>{sender} → {receiver}: <white>{message}"));
        checkTemplate(f, "spy.format", spyFormat, PM_PLACEHOLDERS, legacy, false);

        return new PrivateMessagesConfig(enabled, aliases, reply, takeOver, in, out, sound, vanish, vanishSee,
                self, notifyIgnored, rateLimit, new PrivateMessagesConfig.Spy(spyEnabled, spyPerm, spyFormat));
    }

    /** {target} y {responser} eran alias ambiguos del plan original: se aceptan con aviso. */
    private static String deprecatedPm(JsonFields f, String path, String template) {
        String out = template;
        if (out.contains("{target}")) {
            f.warn(path, "{target} esta obsoleto, usa {receiver}");
            out = out.replace("{target}", "{receiver}");
        }
        if (out.contains("{responser}")) {
            f.warn(path, "{responser} esta obsoleto, usa {sender}");
            out = out.replace("{responser}", "{sender}");
        }
        return out;
    }

    // ---------------------------------------------------------------- spy.json

    private SpyConfig parseSpy(JsonObject o, JsonFields f, MainConfig main) {
        boolean legacy = main == null || main.chat().legacyColorsInFormats();
        boolean enabled = f.bool(o, "enabled", "", true);
        List<String> aliases = f.stringList(o, "commandAliases", "", List.of("spy", "socialspy"));
        boolean log = f.bool(o, "logToConsole", "", false);
        boolean restore = f.bool(o, "restoreOnJoin", "", true);
        Map<String, SpyConfig.ChannelOverride> channels = new LinkedHashMap<>();
        for (Map.Entry<String, JsonObject> e : f.objectMap(o, "channels", "").entrySet()) {
            String id = e.getKey().toLowerCase(Locale.ROOT);
            String path = "channels." + e.getKey();
            JsonObject ch = e.getValue();
            Boolean chEnabled = ch.has("enabled") ? f.bool(ch, "enabled", path, true) : null;
            String perm = ch.has("permission") ? f.string(ch, "permission", path, null) : null;
            String format = ch.has("format") ? f.string(ch, "format", path, null) : null;
            if (format != null) {
                // Los canales de otros plugins pueden traer placeholders propios: solo avisamos.
                checkTemplate(f, path + ".format", format, null, legacy, false);
            }
            channels.put(id, new SpyConfig.ChannelOverride(chEnabled, perm, format));
        }
        return new SpyConfig(enabled, aliases, log, restore, channels);
    }

    // --------------------------------------------------------- chat_colors.json

    private ChatColorsConfig parseChatColors(JsonObject o, JsonFields f) {
        boolean enabled = f.bool(o, "enabled", "", true);
        List<String> aliases = f.stringList(o, "commandAliases", "", List.of("chatcolor", "colores", "chatcolors"));
        if (enabled && aliases.isEmpty()) f.error("commandAliases", "debe tener al menos un alias");
        String title = f.string(o, "title", "", "&8Colores de chat");
        int rows = f.integer(o, "rows", "", 6, 1, 6);
        int size = rows * 9;
        boolean pm = f.bool(o, "applyToPrivateMessages", "", true);

        JsonObject status = f.object(o, "status", "");
        String selected = f.string(status, "selected", "status", "&a▶ Seleccionado");
        String available = f.string(status, "available", "status", "&eClick para usarlo");
        String locked = f.string(status, "locked", "status", "&c✖ Bloqueado");

        Set<Integer> usedSlots = new HashSet<>();
        Set<String> ids = new HashSet<>();
        ChatColorsConfig.Item filler = o.has("filler") && !o.get("filler").isJsonNull()
                ? parseItem(f, f.object(o, "filler", ""), "filler", size, null) : null;
        ChatColorsConfig.Item reset = parseItem(f, f.object(o, "reset", ""), "reset", size, usedSlots);

        List<ChatColorsConfig.ColorOption> colors = new ArrayList<>();
        JsonElement colorsEl = o.get("colors");
        if (colorsEl != null && colorsEl.isJsonArray()) {
            for (int i = 0; i < colorsEl.getAsJsonArray().size(); i++) {
                String path = "colors[" + i + "]";
                JsonElement el = colorsEl.getAsJsonArray().get(i);
                if (!el.isJsonObject()) {
                    f.error(path, "debe ser un objeto { ... }");
                    continue;
                }
                JsonObject c = el.getAsJsonObject();
                String id = f.requiredString(c, "id", path).toLowerCase(Locale.ROOT);
                if (!id.isEmpty() && !ids.add(id)) f.error(path + ".id", "'" + id + "' repetido");
                String perm = f.string(c, "permission", path, "betterchats.chatcolor." + id);
                String colorRaw = f.string(c, "color", path, null);
                String mm = f.string(c, "miniMessage", path, null);
                net.kyori.adventure.text.format.TextColor color = null;
                if (colorRaw != null) {
                    color = parseColor(colorRaw);
                    if (color == null) {
                        f.error(path + ".color", "color '" + colorRaw + "' invalido (usa red, &c o #RRGGBB)");
                    }
                }
                if (mm != null) checkMiniMessage(f, path + ".miniMessage", mm + "x", false, Set.of());
                if (colorRaw == null && mm == null) f.error(path, "necesita \"color\" o \"miniMessage\"");
                ChatColorsConfig.Item item = parseItem(f, c, path, size, usedSlots);
                colors.add(new ChatColorsConfig.ColorOption(id, item, perm, color, mm));
            }
        } else if (colorsEl != null) {
            f.error("colors", "debe ser una lista [ ... ]");
        }

        List<ChatColorsConfig.FormatOption> formats = new ArrayList<>();
        JsonElement formatsEl = o.get("formats");
        if (formatsEl != null && formatsEl.isJsonArray()) {
            for (int i = 0; i < formatsEl.getAsJsonArray().size(); i++) {
                String path = "formats[" + i + "]";
                JsonElement el = formatsEl.getAsJsonArray().get(i);
                if (!el.isJsonObject()) {
                    f.error(path, "debe ser un objeto { ... }");
                    continue;
                }
                JsonObject c = el.getAsJsonObject();
                String id = f.requiredString(c, "id", path).toLowerCase(Locale.ROOT);
                if (!id.isEmpty() && !ids.add(id)) f.error(path + ".id", "'" + id + "' repetido");
                String perm = f.string(c, "permission", path, "betterchats.chatcolor.format." + id);
                String decoRaw = f.requiredString(c, "decoration", path);
                net.kyori.adventure.text.format.TextDecoration deco =
                        net.kyori.adventure.text.format.TextDecoration.NAMES.value(
                                TagCatalog.normalize(decoRaw));
                if (deco == null) {
                    f.error(path + ".decoration", "'" + decoRaw
                            + "' invalida (bold, italic, underlined, strikethrough u obfuscated)");
                }
                ChatColorsConfig.Item item = parseItem(f, c, path, size, usedSlots);
                formats.add(new ChatColorsConfig.FormatOption(id, item, perm, deco));
            }
        } else if (formatsEl != null) {
            f.error("formats", "debe ser una lista [ ... ]");
        }

        return new ChatColorsConfig(enabled, aliases, title, rows, pm, filler, reset, selected, available, locked,
                colors, formats);
    }

    private ChatColorsConfig.Item parseItem(JsonFields f, JsonObject o, String path, int size, Set<Integer> usedSlots) {
        String material = f.string(o, "material", path, "STONE").toUpperCase(Locale.ROOT);
        int data = f.integer(o, "data", path, 0, 0, 32767);
        int slot = usedSlots == null ? -1 : f.integer(o, "slot", path, 0, 0, size - 1);
        if (usedSlots != null && !usedSlots.add(slot)) {
            f.error(path + ".slot", "el slot " + slot + " ya lo usa otro icono");
        }
        String name = f.string(o, "name", path, " ");
        List<String> lore = f.stringList(o, "lore", path, List.of());
        return new ChatColorsConfig.Item(material, data, slot, name, lore);
    }

    /** "red", "&c", "§c" o "#RRGGBB". */
    static net.kyori.adventure.text.format.TextColor parseColor(String raw) {
        String s = raw.trim();
        if (s.length() == 2 && (s.charAt(0) == '&' || s.charAt(0) == '§')) {
            int idx = "0123456789abcdef".indexOf(Character.toLowerCase(s.charAt(1)));
            if (idx < 0) return null;
            String[] names = {"black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold",
                    "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"};
            s = names[idx];
        }
        if (s.startsWith("#")) return net.kyori.adventure.text.format.TextColor.fromHexString(s);
        return net.kyori.adventure.text.format.NamedTextColor.NAMES.value(s.toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------ chats/

    private List<TeamDefinition> loadTeams(MainConfig main, List<String> errors, List<String> warnings) {
        Path chats = folder.resolve("chats");
        List<TeamDefinition> out = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        List<String> defaults = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(chats, "*.json")) {
            List<Path> files = new ArrayList<>();
            ds.forEach(files::add);
            files.sort(null);
            for (Path file : files) {
                String display = "chats/" + file.getFileName();
                JsonObject json = read(file, display, errors);
                if (json == null) continue;
                String fileId = file.getFileName().toString();
                fileId = fileId.substring(0, fileId.length() - ".json".length());
                TeamDefinition def = parseTeam(json, fileId, new JsonFields(display, errors, warnings),
                        main.chat().legacyColorsInFormats());
                if (def == null) continue;
                if (!ids.add(def.getId())) {
                    errors.add(display + " -> id: '" + def.getId() + "' repetido en otro archivo");
                    continue;
                }
                if (def.isDefault()) defaults.add(display);
                out.add(def);
            }
        } catch (IOException ex) {
            errors.add("chats/ -> no se pudo leer la carpeta: " + ex.getMessage());
        }
        if (defaults.isEmpty()) {
            errors.add("chats/ -> isDefault: ningun chat tiene \"isDefault\": true (debe haber exactamente uno)");
        } else if (defaults.size() > 1) {
            errors.add("chats/ -> isDefault: hay " + defaults.size() + " chats por defecto " + defaults
                    + " (debe haber exactamente uno)");
        }
        return out;
    }

    TeamDefinition parseTeam(JsonObject o, String fileId, JsonFields f, boolean legacy) {
        String id = f.requiredString(o, "id", "");
        if (!id.isEmpty() && !id.equals(fileId)) {
            f.error("id", "'" + id + "' debe coincidir con el nombre del archivo ('" + fileId + "')");
        }
        if (!TEAM_ID.matcher(fileId).matches()) {
            f.error("id", "'" + fileId + "' invalido: usa minusculas, numeros, _ o - (maximo 32)");
            return null;
        }
        String displayName = f.string(o, "displayName", "", fileId);
        checkMiniMessage(f, "displayName", legacy ? LegacyColors.toMiniMessage(displayName) : displayName, false, Set.of());
        boolean isDefault = f.bool(o, "isDefault", "", false);
        boolean staffOnly = f.bool(o, "isStaffOnly", "", false);
        String permission = f.string(o, "permission", "", null);
        if (permission != null && permission.isBlank()) permission = null;
        if (permission != null && permission.contains(" ")) {
            f.error("permission", "no puede contener espacios (valor: '" + permission + "')");
        }
        if (staffOnly && permission == null) {
            permission = "betterchats.team." + fileId;
            f.warn("permission", "isStaffOnly sin permiso: se usa '" + permission + "'");
        }
        if (isDefault && (staffOnly || permission != null)) {
            f.error("isDefault", "el chat por defecto no puede ser isStaffOnly ni pedir permiso");
        }
        boolean implicit = f.bool(o, "implicitMembers", "", staffOnly);
        int priority = f.integer(o, "priority", "", 0, -10000, 10000);
        String format = f.requiredString(o, "format", "");
        checkTemplate(f, "format", format, TEAM_PLACEHOLDERS, legacy, true);
        Set<String> tags = tagSet(f, f.stringList(o, "allowedPlayerTags", "", List.of()), "allowedPlayerTags");
        SoundSettings sound = parseSound(f, f.object(o, "sound", ""), "sound");

        RateLimit rateLimit = null;
        if (o.has("rateLimit") && !o.get("rateLimit").isJsonNull()) {
            JsonObject rl = f.object(o, "rateLimit", "");
            int messages = f.integer(rl, "messages", "rateLimit", 5, 1, 1000);
            int per = f.integer(rl, "perSeconds", "rateLimit", 10, 1, 3600);
            rateLimit = new RateLimit(messages, per);
        }

        TeamDefinition.Builder b = TeamDefinition.builder(fileId)
                .displayName(displayName).isDefault(isDefault).staffOnly(staffOnly).permission(permission)
                .implicitMembers(implicit).priority(priority).format(format).sound(sound)
                .rateLimit(rateLimit).persistent(true);
        tags.forEach(b::allowTag);
        return b.build();
    }

    // ----------------------------------------------------------------- comunes

    private SoundSettings parseSound(JsonFields f, JsonObject s, String path) {
        boolean enabled = f.bool(s, "enabled", path, false);
        String key = f.string(s, "sound", path, "");
        float volume = f.decimal(s, "volume", path, 1.0f, 0.0f, 10.0f);
        float pitch = f.decimal(s, "pitch", path, 1.0f, 0.5f, 2.0f);
        if (enabled) {
            if (key.isBlank()) {
                f.error(path + ".sound", "esta activado pero no tiene clave de sonido");
            } else {
                String problem = soundValidator.apply(key);
                if (problem != null) f.warn(path + ".sound", problem);
            }
        }
        return new SoundSettings(enabled, key, volume, pitch);
    }

    private Set<String> tagSet(JsonFields f, List<String> raw, String path) {
        Set<String> out = new LinkedHashSet<>();
        for (String t : raw) {
            String n = TagCatalog.normalize(t);
            if (!TagCatalog.isKnown(n)) {
                f.warn(path, "etiqueta desconocida '" + t + "' (se ignora)");
                continue;
            }
            out.add(n);
        }
        return Set.copyOf(out);
    }

    /**
     * Valida una plantilla: placeholders conocidos, {message} presente si se pide
     * y que MiniMessage la pueda leer.
     */
    private static void checkTemplate(JsonFields f, String path, String template, Set<String> known,
                                      boolean legacy, boolean requireMessage) {
        if (template.isEmpty()) return;
        if (requireMessage && !template.contains("{message}")) {
            f.error(path, "la plantilla debe incluir {message}");
        }
        Set<String> found = new LinkedHashSet<>();
        Matcher m = BRACE.matcher(template);
        while (m.find()) found.add(m.group(1));
        if (known != null) {
            for (String ph : found) {
                if (!known.contains(ph)) {
                    f.warn(path, "placeholder {" + ph + "} desconocido (disponibles: " + known + ")");
                }
            }
        }
        checkMiniMessage(f, path, legacy ? LegacyColors.toMiniMessage(template) : template, true, found);
    }

    private static void checkMiniMessage(JsonFields f, String path, String template, boolean hasBraces,
                                         Set<String> placeholders) {
        String probe = template;
        if (hasBraces) {
            for (String ph : placeholders) probe = probe.replace("{" + ph + "}", "x");
        }
        try {
            MiniMessage.miniMessage().deserialize(probe);
        } catch (RuntimeException ex) {
            f.error(path, "MiniMessage no puede leer la plantilla: " + ex.getMessage());
        }
    }

    // -------------------------------------------------------------------- lang

    private Lang loadLang(String language, boolean legacyColors, List<String> errors, List<String> warnings) {
        String lang = language;
        JsonObject bundled;
        try {
            bundled = readResource("lang/" + lang + ".json");
            if (bundled.size() == 0) {
                warnings.add("config.json -> language: idioma '" + lang + "' sin traduccion incluida; se usa 'es'");
                lang = "es";
                bundled = readResource("lang/es.json");
            }
        } catch (IOException ex) {
            errors.add("lang/" + lang + ".json -> no se pudo leer el idioma incluido: " + ex.getMessage());
            return null;
        }
        Map<String, String> messages = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : bundled.entrySet()) {
            if (e.getValue().isJsonPrimitive()) messages.put(e.getKey(), e.getValue().getAsString());
        }
        Path user = folder.resolve("lang").resolve(lang + ".json");
        if (Files.exists(user)) {
            JsonObject custom = read(user, "lang/" + lang + ".json", errors);
            if (custom != null) {
                JsonFields f = new JsonFields("lang/" + lang + ".json", errors, warnings);
                for (Map.Entry<String, JsonElement> e : custom.entrySet()) {
                    if (!e.getValue().isJsonPrimitive()) {
                        f.error(e.getKey(), "debe ser un texto");
                        continue;
                    }
                    String v = e.getValue().getAsString();
                    checkMiniMessage(f, e.getKey(), v, true, braces(v));
                    messages.put(e.getKey(), v);
                }
            }
        }
        return new Lang(messages, legacyColors);
    }

    private static Set<String> braces(String s) {
        Set<String> out = new HashSet<>();
        Matcher m = BRACE.matcher(s);
        while (m.find()) out.add(m.group(1));
        return out;
    }
}
