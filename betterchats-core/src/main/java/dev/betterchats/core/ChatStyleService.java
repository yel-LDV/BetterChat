package dev.betterchats.core;

import dev.betterchats.core.config.ChatColorsConfig;
import dev.betterchats.core.data.PlayerData;
import dev.betterchats.core.platform.ChatPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Color y formatos elegidos en /chatcolor (sustituye a EzColors). Se aplican
 * al mensaje ya sanitizado; los colores que el jugador escriba con permiso
 * ({@code &c}...) tienen prioridad porque van en nodos hijos.
 */
public final class ChatStyleService {

    /** Resultado de una seleccion desde el menu o el comando. */
    public enum Result { SELECTED, REMOVED, LOCKED, UNKNOWN, DISABLED }

    private final BetterChatsCore core;

    ChatStyleService(BetterChatsCore core) {
        this.core = core;
    }

    private ChatColorsConfig cfg() {
        return core.config().chatColors();
    }

    public boolean canUse(ChatPlayer player, String permission) {
        return permission == null || permission.isEmpty() || player.hasPermission(permission);
    }

    /** Alterna un color/efecto o un formato por su id. */
    public Result toggle(ChatPlayer player, String id) {
        ChatColorsConfig cfg = cfg();
        if (!cfg.enabled()) return Result.DISABLED;
        PlayerData d = core.data().get(player.uuid());
        Optional<ChatColorsConfig.ColorOption> color = cfg.color(id);
        if (color.isPresent()) {
            if (!canUse(player, color.get().permission())) return Result.LOCKED;
            if (id.equals(d.chatColor())) {
                d.chatColor(null);
                return Result.REMOVED;
            }
            d.chatColor(id);
            return Result.SELECTED;
        }
        Optional<ChatColorsConfig.FormatOption> format = cfg.format(id);
        if (format.isPresent()) {
            if (!canUse(player, format.get().permission())) return Result.LOCKED;
            boolean on = !d.hasChatFormat(id);
            d.setChatFormat(id, on);
            return on ? Result.SELECTED : Result.REMOVED;
        }
        return Result.UNKNOWN;
    }

    public void reset(ChatPlayer player) {
        core.data().get(player.uuid()).clearChatStyle();
    }

    public boolean isSelected(ChatPlayer player, String id) {
        PlayerData d = core.data().get(player.uuid());
        return id.equals(d.chatColor()) || d.hasChatFormat(id);
    }

    /**
     * Aplica el estilo elegido. Si el jugador perdio el permiso de un color o
     * formato, simplemente no se aplica (queda guardado por si lo recupera).
     *
     * @param rich el mensaje lleva tokens/hover: los efectos letra a letra no
     *             se aplican para no romperlos (si se aplican los formatos)
     */
    public Component apply(ChatPlayer player, Component body, boolean rich, boolean privateMessage) {
        ChatColorsConfig cfg = cfg();
        if (!cfg.enabled() || (privateMessage && !cfg.applyToPrivateMessages())) return body;
        PlayerData d = core.data().cached(player.uuid());
        if (d == null) return body;

        ChatColorsConfig.ColorOption color = cfg.color(d.chatColor())
                .filter(c -> canUse(player, c.permission())).orElse(null);
        List<TextDecoration> decorations = new ArrayList<>();
        for (String id : d.chatFormats()) {
            cfg.format(id).filter(f -> f.decoration() != null && canUse(player, f.permission()))
                    .ifPresent(f -> decorations.add(f.decoration()));
        }
        if (color == null && decorations.isEmpty()) return body;

        Component out;
        if (color != null && color.miniMessage() != null && !rich && !hasStyle(body)) {
            // Degradado / rainbow letra a letra sobre el texto plano (escapado).
            String plain = PlainTextComponentSerializer.plainText().serialize(body);
            out = MiniMessage.miniMessage().deserialize(color.miniMessage()
                    + MiniMessage.miniMessage().escapeTags(plain));
        } else {
            var builder = Component.text().append(body);
            if (color != null && color.color() != null) builder.color(color.color());
            out = builder.build();
        }
        for (TextDecoration deco : decorations) out = out.decoration(deco, true);
        return out;
    }

    /** true si algun nodo ya tiene color o decoracion (el jugador escribio &c...). */
    private static boolean hasStyle(Component c) {
        if (c.color() != null) return true;
        for (TextDecoration d : TextDecoration.values()) {
            if (c.decoration(d) != TextDecoration.State.NOT_SET) return true;
        }
        for (Component child : c.children()) if (hasStyle(child)) return true;
        return false;
    }
}
