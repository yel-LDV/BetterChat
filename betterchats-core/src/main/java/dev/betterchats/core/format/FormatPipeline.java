package dev.betterchats.core.format;

import dev.betterchats.core.platform.ChatPlayer;
import dev.betterchats.core.platform.PlaceholderBridge;
import dev.betterchats.core.platform.PlayerSnapshot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renderiza una plantilla de confianza (formato de chat, PM, spy) en el orden
 * de la seccion 5.1 del plan:
 *
 * <ol>
 *   <li>{@code {placeholders}} propios -> etiquetas internas cuyos valores
 *       entran como texto sin parsear o como Component ya construido.</li>
 *   <li>PlaceholderAPI solo sobre la plantilla (nunca sobre el texto del
 *       jugador). Cada valor entra en linea con sus codigos {@code &}/{@code §}
 *       convertidos y sus etiquetas MiniMessage escapadas: un prefijo con
 *       colores funciona (y su ultimo color sigue aplicando, como en legacy)
 *       pero un nick con {@code <click>} no se interpreta.</li>
 *   <li>MiniMessage deserializa la plantilla con esos resolutores.</li>
 *   <li>{@code {message}} entra como Component ya sanitizado.</li>
 * </ol>
 */
public final class FormatPipeline {

    private static final Pattern BRACE = Pattern.compile("\\{([A-Za-z0-9_]+)}");
    private static final Pattern PAPI = Pattern.compile("%([^%\\s]+)%");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    /**
     * @param template       plantilla MiniMessage (de configuracion)
     * @param text           placeholders de texto plano ({@code playerName} -> valor)
     * @param components     placeholders de componente ({@code message} -> body)
     * @param papiSubject    jugador para PlaceholderAPI (null = sin PAPI)
     * @param snapshot       valores de PAPI precalculados en el hilo principal (puede ser null)
     * @param legacyTemplate convertir {@code &x} de la plantilla
     */
    public Component render(String template, Map<String, String> text, Map<String, Component> components,
                            ChatPlayer papiSubject, PlaceholderBridge papi, PlayerSnapshot snapshot,
                            boolean legacyTemplate) {
        // 0) PlaceholderAPI sobre la plantilla ORIGINAL, antes de convertir &/§:
        //    los argumentos de un placeholder (p. ej. changeoutput con
        //    "_ifmatch:§e⚠") deben llegarle tal cual. Cada valor resuelto se
        //    aparta con un marcador y se vuelve a poner al final.
        List<String> papiValues = new ArrayList<>();
        String tpl = resolvePlaceholderApi(template, papiSubject, papi, snapshot, papiValues);

        if (legacyTemplate) tpl = LegacyColors.toMiniMessage(tpl);
        List<TagResolver> resolvers = new ArrayList<>();

        // 1) {placeholders} propios
        StringBuilder sb = new StringBuilder(tpl.length() + 16);
        Matcher m = BRACE.matcher(tpl);
        int last = 0;
        while (m.find()) {
            String key = m.group(1);
            String tag = "bc_" + key.toLowerCase(Locale.ROOT);
            if (components.containsKey(key)) {
                sb.append(tpl, last, m.start()).append('<').append(tag).append('>');
                resolvers.add(Placeholder.component(tag, components.get(key)));
            } else if (text.containsKey(key)) {
                sb.append(tpl, last, m.start()).append('<').append(tag).append('>');
                resolvers.add(Placeholder.unparsed(tag, MessageSanitizer.stripSection(text.get(key))));
            } else {
                sb.append(tpl, last, m.end());
            }
            last = m.end();
        }
        sb.append(tpl, last, tpl.length());
        tpl = sb.toString();

        // 2) Valores de PlaceholderAPI en linea, como en los plugins legacy: el
        //    color final de un prefijo ("&c[Admin] &f") sigue aplicando a lo que
        //    va detras. Sus etiquetas MiniMessage van escapadas.
        for (int i = 0; i < papiValues.size(); i++) {
            tpl = tpl.replace("" + MARK_OPEN + i + MARK_CLOSE, papiValues.get(i));
        }

        // 3) + 4) MiniMessage sobre la plantilla, con el mensaje como componente
        return miniMessage.deserialize(tpl, TagResolver.resolver(resolvers));
    }

    /** Marcadores de uso privado de Unicode: no los toca ni la conversion legacy ni MiniMessage. */
    private static final char MARK_OPEN = '';
    private static final char MARK_CLOSE = '';

    /**
     * Sustituye cada {@code %placeholder%} resuelto por un marcador y guarda en
     * {@code values} su valor ya convertido a MiniMessage seguro. Los que no se
     * resuelven se dejan como texto.
     */
    private static String resolvePlaceholderApi(String template, ChatPlayer subject, PlaceholderBridge papi,
                                                PlayerSnapshot snapshot, List<String> values) {
        boolean hasSnapshot = snapshot != null && !snapshot.placeholders().isEmpty();
        if (subject == null || template.indexOf('%') < 0 || papi == null
                || (!papi.isAvailable() && !hasSnapshot)) {
            return template;
        }
        StringBuilder out = new StringBuilder(template.length());
        Matcher pm = PAPI.matcher(template);
        int idx = 0;
        while (pm.find()) {
            String ph = pm.group();
            String value = hasSnapshot ? snapshot.placeholders().get(ph) : null;
            if (value == null && papi.isAvailable()) value = papi.apply(subject, ph);
            if (value == null || value.equals(ph)) continue; // sin resolver: se deja literal
            out.append(template, idx, pm.start()).append(MARK_OPEN).append(values.size()).append(MARK_CLOSE);
            values.add(papiValueToMiniMessage(value));
            idx = pm.end();
        }
        out.append(template, idx, template.length());
        return out.toString();
    }

    /**
     * Valor de PlaceholderAPI -> fragmento MiniMessage seguro: se escapan sus
     * etiquetas y despues se convierten sus codigos {@code &}/{@code §}.
     */
    public static String papiValueToMiniMessage(String value) {
        return LegacyColors.toMiniMessage(MiniMessage.miniMessage().escapeTags(value));
    }

    /** Texto con codigos legacy (& o §) a Component, sin interpretar MiniMessage. */
    public static Component legacyToComponent(String value) {
        String v = value.indexOf('&') >= 0 ? translateAmpersand(value) : value;
        return LEGACY.deserialize(v);
    }

    private static String translateAmpersand(String s) {
        char[] b = s.toCharArray();
        for (int i = 0; i < b.length - 1; i++) {
            if (b[i] == '&' && "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx".indexOf(b[i + 1]) > -1) {
                b[i] = '§';
                b[i + 1] = Character.toLowerCase(b[i + 1]);
            }
        }
        return new String(b);
    }
}
