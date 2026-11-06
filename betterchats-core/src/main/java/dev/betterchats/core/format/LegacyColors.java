package dev.betterchats.core.format;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Convierte codigos legacy ({@code &a}, {@code §a}, {@code &l}, {@code &#RRGGBB}) a
 * etiquetas MiniMessage, respetando la semantica legacy: un color quita las
 * decoraciones anteriores ({@code &l&c} = rojo sin negrita).
 *
 * <p>Para texto de jugadores se usa con un filtro de etiquetas permitidas: los
 * codigos no permitidos se dejan tal cual, como texto literal.</p>
 */
public final class LegacyColors {

    private static final String[] COLORS = {
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
            "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"};

    private LegacyColors() {
    }

    /** Conversion completa (plantillas de configuracion, de confianza). */
    public static String toMiniMessage(String input) {
        return convert(input, tag -> true);
    }

    /** Conversion limitada a las etiquetas permitidas (texto de jugadores). */
    public static String toMiniMessage(String input, Set<String> allowedTags) {
        return convert(input, allowedTags::contains);
    }

    private static String convert(String in, Predicate<String> allowed) {
        if (in.indexOf('&') < 0 && in.indexOf('§') < 0) return in;
        StringBuilder out = new StringBuilder(in.length() + 16);
        Set<String> active = new LinkedHashSet<>();
        int i = 0;
        while (i < in.length()) {
            char c = in.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < in.length()) {
                char code = Character.toLowerCase(in.charAt(i + 1));
                // &#RRGGBB
                if (code == '#' && i + 8 <= in.length() && isHex(in, i + 2, 6) && allowed.test("color")) {
                    closeDecorations(out, active);
                    out.append("<#").append(in, i + 2, i + 8).append('>');
                    i += 8;
                    continue;
                }
                int colorIdx = "0123456789abcdef".indexOf(code);
                if (colorIdx >= 0 && allowed.test("color")) {
                    closeDecorations(out, active);
                    out.append('<').append(COLORS[colorIdx]).append('>');
                    i += 2;
                    continue;
                }
                String deco = switch (code) {
                    case 'k' -> "obfuscated";
                    case 'l' -> "bold";
                    case 'm' -> "strikethrough";
                    case 'n' -> "underlined";
                    case 'o' -> "italic";
                    default -> null;
                };
                if (deco != null && allowed.test(deco)) {
                    out.append('<').append(deco).append('>');
                    active.add(deco);
                    i += 2;
                    continue;
                }
                if (code == 'r' && allowed.test("reset")) {
                    out.append("<reset>");
                    active.clear();
                    i += 2;
                    continue;
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /** Legacy: un color nuevo apaga las decoraciones activas. */
    private static void closeDecorations(StringBuilder out, Set<String> active) {
        for (String d : active) out.append("<!").append(d).append('>');
        active.clear();
    }

    private static boolean isHex(String s, int from, int len) {
        for (int k = from; k < from + len; k++) {
            if (Character.digit(s.charAt(k), 16) < 0) return false;
        }
        return true;
    }
}
