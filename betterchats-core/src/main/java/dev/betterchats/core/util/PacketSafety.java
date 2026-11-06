package dev.betterchats.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Evita el "chat kick": en 1.8 un paquete de chat cuyo JSON pasa de 32767
 * bytes no se puede codificar y desconecta a TODOS los que lo reciben. El
 * caso tipico es enseñar con [item] un libro o un item con NBT enorme.
 *
 * <p>Si la linea es demasiado grande se quitan hover/clic; si aun asi lo es,
 * se envia como texto plano recortado.</p>
 */
public final class PacketSafety {

    /** Margen bajo el limite de 32767 bytes del protocolo. */
    public static final int MAX_JSON_BYTES = 30000;
    /** Longitud maxima del texto plano de respaldo. */
    private static final int MAX_PLAIN = 1000;

    private static final GsonComponentSerializer GSON = GsonComponentSerializer.colorDownsamplingGson();

    private PacketSafety() {
    }

    public static Component limit(Component line) {
        if (jsonBytes(line) <= MAX_JSON_BYTES) return line;
        Component noEvents = stripEvents(line);
        if (jsonBytes(noEvents) <= MAX_JSON_BYTES) return noEvents;
        String plain = PlainTextComponentSerializer.plainText().serialize(line);
        if (plain.length() > MAX_PLAIN) plain = plain.substring(0, MAX_PLAIN) + "...";
        return Component.text(plain);
    }

    public static int jsonBytes(Component c) {
        return GSON.serialize(c).getBytes(StandardCharsets.UTF_8).length;
    }

    private static Component stripEvents(Component c) {
        List<Component> children = new ArrayList<>(c.children().size());
        for (Component child : c.children()) children.add(stripEvents(child));
        return c.hoverEvent(null).clickEvent(null).insertion(null).children(children);
    }
}
