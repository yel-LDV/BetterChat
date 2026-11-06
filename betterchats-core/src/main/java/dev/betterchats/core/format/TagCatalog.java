package dev.betterchats.core.format;

import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Catalogo de etiquetas MiniMessage que un jugador puede llegar a usar, con su
 * resolutor y los grupos ({@code color}, {@code format}) que aceptan
 * {@code allowedPlayerTags} y los permisos {@code betterchats.tags.*}.
 */
public final class TagCatalog {

    /** Etiqueta -> resolutor. */
    public static final Map<String, TagResolver> RESOLVERS;

    /** Grupos que se expanden a varias etiquetas. */
    public static final Map<String, Set<String>> GROUPS = Map.of(
            "color", Set.of("color", "gradient", "rainbow", "transition", "reset"),
            "format", Set.of("bold", "italic", "underlined", "strikethrough"));

    /**
     * Nunca se conceden por la lista del chat ni por la de config.json: solo con
     * su permiso explicito ({@code betterchats.tags.click}...) o con
     * {@code betterchats.tags.all}. Son las que permiten ejecutar comandos,
     * suplantar texto o leer datos del cliente.
     */
    public static final Set<String> PROTECTED = Set.of(
            "click", "hover", "insertion", "font", "keybind", "selector", "score", "nbt", "translatable");

    static {
        Map<String, TagResolver> m = new LinkedHashMap<>();
        m.put("color", StandardTags.color());
        m.put("gradient", StandardTags.gradient());
        m.put("rainbow", StandardTags.rainbow());
        m.put("transition", StandardTags.transition());
        m.put("reset", StandardTags.reset());
        m.put("bold", StandardTags.decorations(TextDecoration.BOLD));
        m.put("italic", StandardTags.decorations(TextDecoration.ITALIC));
        m.put("underlined", StandardTags.decorations(TextDecoration.UNDERLINED));
        m.put("strikethrough", StandardTags.decorations(TextDecoration.STRIKETHROUGH));
        m.put("obfuscated", StandardTags.decorations(TextDecoration.OBFUSCATED));
        m.put("newline", StandardTags.newline());
        m.put("click", StandardTags.clickEvent());
        m.put("hover", StandardTags.hoverEvent());
        m.put("insertion", StandardTags.insertion());
        m.put("font", StandardTags.font());
        m.put("keybind", StandardTags.keybind());
        m.put("selector", StandardTags.selector());
        m.put("score", StandardTags.score());
        m.put("nbt", StandardTags.nbt());
        m.put("translatable", StandardTags.translatable());
        RESOLVERS = Map.copyOf(m);
    }

    /** Orden estable para claves de cache. */
    public static final List<String> ORDER = List.of("color", "gradient", "rainbow", "transition", "reset",
            "bold", "italic", "underlined", "strikethrough", "obfuscated", "newline", "click", "hover",
            "insertion", "font", "keybind", "selector", "score", "nbt", "translatable");

    private TagCatalog() {
    }

    /** true si el nombre es una etiqueta o un grupo conocido. */
    public static boolean isKnown(String name) {
        String n = normalize(name);
        return RESOLVERS.containsKey(n) || GROUPS.containsKey(n);
    }

    /** Acepta alias habituales: key -> keybind, underline -> underlined... */
    public static String normalize(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT).trim();
        return switch (n) {
            case "key" -> "keybind";
            case "underline", "u" -> "underlined";
            case "b" -> "bold";
            case "i", "em" -> "italic";
            case "st" -> "strikethrough";
            case "obf" -> "obfuscated";
            case "colour", "c" -> "color";
            case "br" -> "newline";
            case "lang", "tr", "translate" -> "translatable";
            default -> n;
        };
    }
}
