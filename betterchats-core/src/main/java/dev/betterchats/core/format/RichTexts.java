package dev.betterchats.core.format;

import dev.betterchats.api.text.HoverItem;
import dev.betterchats.api.text.RichText;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

/** {@link RichText} (API, sin Adventure) -> Component. */
public final class RichTexts {

    private RichTexts() {
    }

    public static Component toComponent(RichText rich) {
        Component c = FormatPipeline.legacyToComponent(rich.getText());
        HoverEvent<?> hover = null;
        if (rich.getHoverItem() != null) hover = itemHover(rich.getHoverItem());
        if (hover == null && rich.getHoverText() != null) {
            String text = rich.getHoverText();
            if (text.length() > MAX_SNBT) text = text.substring(0, MAX_SNBT) + "...";
            hover = HoverEvent.showText(FormatPipeline.legacyToComponent(text));
        }
        if (hover != null) c = c.hoverEvent(hover);
        if (rich.getClickAction() != null && rich.getClickValue() != null) {
            String v = rich.getClickValue();
            ClickEvent click = switch (rich.getClickAction()) {
                case RUN_COMMAND -> ClickEvent.runCommand(v);
                case SUGGEST_COMMAND -> ClickEvent.suggestCommand(v);
                case OPEN_URL -> ClickEvent.openUrl(v);
                case COPY_TO_CLIPBOARD -> ClickEvent.copyToClipboard(v);
            };
            c = c.clickEvent(click);
        }
        return c;
    }

    /** NBT mas grande que esto (libros llenos, items con datos enormes) se cambia por hover de texto. */
    static final int MAX_SNBT = 12000;

    @SuppressWarnings("deprecation")
    private static HoverEvent<HoverEvent.ShowItem> itemHover(HoverItem item) {
        if (item.getSnbt() != null && item.getSnbt().length() > MAX_SNBT) return null;
        try {
            Key key = Key.key(item.getItemKey());
            int amount = Math.max(1, item.getAmount());
            HoverEvent.ShowItem show = item.getSnbt() != null && !item.getSnbt().isEmpty()
                    ? HoverEvent.ShowItem.showItem(key, amount, BinaryTagHolder.binaryTagHolder(item.getSnbt()))
                    : HoverEvent.ShowItem.showItem(key, amount);
            return HoverEvent.showItem(show);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
