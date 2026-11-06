package dev.betterchats.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Serializadores compartidos. */
public final class Texts {

    /** Sin hex: los colores RGB se aproximan al color con nombre mas cercano (clientes 1.8). */
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private Texts() {
    }

    public static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    /** Component -> texto con codigos §, para APIs que solo aceptan String (1.8). */
    public static String legacy(Component c) {
        return LEGACY.serialize(c);
    }
}
