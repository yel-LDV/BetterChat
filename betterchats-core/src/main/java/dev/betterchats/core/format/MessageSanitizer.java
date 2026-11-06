package dev.betterchats.core.format;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Convierte el texto de un jugador en un Component SEGURO.
 *
 * <ul>
 *   <li>Sin etiquetas permitidas: texto plano literal ({@code Component.text}).</li>
 *   <li>Con etiquetas: un MiniMessage restringido que solo conoce esas
 *       etiquetas; cualquier otra ({@code <click>}, {@code <hover>}...) queda
 *       como texto literal.</li>
 *   <li>El caracter {@code §} se elimina siempre (no se pueden colar codigos
 *       legacy crudos).</li>
 * </ul>
 */
public final class MessageSanitizer {

    private final Map<String, MiniMessage> cache = new ConcurrentHashMap<>();

    /**
     * @param legacyCodes si true, los {@code &x} cuya etiqueta este permitida se
     *                    convierten (comodidad en 1.8); los demas quedan literales
     */
    public Component sanitize(String raw, Set<String> allowedTags, boolean legacyCodes) {
        String text = clean(raw);
        if (allowedTags.isEmpty()) {
            return Component.text(text);
        }
        String parsed = legacyCodes ? LegacyColors.toMiniMessage(text, allowedTags) : text;
        // Cientos de etiquetas = componentes anidados a mucha profundidad (lag o
        // paquetes enormes). Por encima del limite el mensaje va como texto.
        if (count(parsed, '<') > MAX_TAGS) {
            return Component.text(text);
        }
        return restricted(allowedTags).deserialize(parsed);
    }

    /** Maximo de etiquetas (incluidas las que salen de codigos &) por mensaje. */
    static final int MAX_TAGS = 32;
    /** Maximo de marcas combinables seguidas (texto "zalgo"). */
    static final int MAX_COMBINING = 2;

    /**
     * Limpieza del texto del jugador antes de todo:
     * <ul>
     *   <li>{@code §}: fuera (no se cuelan codigos legacy crudos).</li>
     *   <li>Caracteres de control (saltos de linea, tabuladores...): espacio. Un
     *       salto de linea permitiria fingir un mensaje del staff o del servidor.</li>
     *   <li>Caracteres invisibles y de direccion (U+200B..U+200F, U+202A..U+202E,
     *       U+2066..U+2069, U+FEFF...): fuera. Sirven para suplantar nombres o
     *       invertir el texto.</li>
     *   <li>Mas de {@value #MAX_COMBINING} marcas combinables seguidas ("zalgo"):
     *       se recortan.</li>
     *   <li>Surrogates sueltos (UTF-16 invalido): fuera.</li>
     * </ul>
     */
    public static String clean(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        int combining = 0;
        for (int i = 0; i < raw.length(); ) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '§' || isInvisible(cp) || (Character.isBmpCodePoint(cp) && Character.isSurrogate((char) cp))) {
                continue;
            }
            if (cp < 0x20 || cp == 0x7F || (cp >= 0x80 && cp <= 0x9F)) {
                sb.append(' ');
                combining = 0;
                continue;
            }
            int type = Character.getType(cp);
            if (type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK
                    || type == Character.COMBINING_SPACING_MARK) {
                if (++combining > MAX_COMBINING) continue;
            } else {
                combining = 0;
            }
            sb.appendCodePoint(cp);
        }
        return sb.toString();
    }

    private static boolean isInvisible(int cp) {
        return (cp >= 0x200B && cp <= 0x200F)   // zero-width y marcas LTR/RTL
                || (cp >= 0x202A && cp <= 0x202E) // embedding / override de direccion
                || (cp >= 0x2060 && cp <= 0x2064) // word joiner e invisibles
                || (cp >= 0x2066 && cp <= 0x2069) // isolates de direccion
                || cp == 0xFEFF || cp == 0x00AD || cp == 0x180E || cp == 0x034F;
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }

    MiniMessage restricted(Set<String> allowed) {
        StringBuilder key = new StringBuilder();
        for (String t : TagCatalog.ORDER) {
            if (allowed.contains(t)) key.append(t).append(',');
        }
        return cache.computeIfAbsent(key.toString(), k -> {
            List<TagResolver> resolvers = new ArrayList<>();
            for (String t : TagCatalog.ORDER) {
                if (allowed.contains(t)) resolvers.add(TagCatalog.RESOLVERS.get(t));
            }
            return MiniMessage.builder().tags(TagResolver.resolver(resolvers)).build();
        });
    }

    public static String stripSection(String s) {
        return s.indexOf('§') < 0 ? s : s.replace("§", "");
    }
}
