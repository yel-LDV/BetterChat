package dev.betterchats.core.config;

import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.List;
import java.util.Optional;

/**
 * chat_colors.json: menu de colores y formatos de chat (/chatcolor).
 *
 * @param applyToPrivateMessages aplicar el estilo elegido tambien a los /msg
 */
public record ChatColorsConfig(boolean enabled, List<String> commandAliases, String title, int rows,
                               boolean applyToPrivateMessages, Item filler, Item reset,
                               String statusSelected, String statusAvailable, String statusLocked,
                               List<ColorOption> colors, List<FormatOption> formats) {

    /** Icono del menu. Material con nombre de la version del servidor (1.8: WOOL + data). */
    public record Item(String material, int data, int slot, String name, List<String> lore) {
    }

    /**
     * Un color o efecto. {@code color} para un color fijo; {@code miniMessage}
     * para efectos (degradado, rainbow) que se aplican letra a letra.
     */
    public record ColorOption(String id, Item item, String permission, TextColor color, String miniMessage) {
    }

    public record FormatOption(String id, Item item, String permission, TextDecoration decoration) {
    }

    public Optional<ColorOption> color(String id) {
        if (id == null) return Optional.empty();
        for (ColorOption c : colors) if (c.id().equals(id)) return Optional.of(c);
        return Optional.empty();
    }

    public Optional<FormatOption> format(String id) {
        if (id == null) return Optional.empty();
        for (FormatOption f : formats) if (f.id().equals(id)) return Optional.of(f);
        return Optional.empty();
    }
}
