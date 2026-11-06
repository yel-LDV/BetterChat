package dev.betterchats.api.text;

import dev.betterchats.api.ApiStatus;

import java.util.Objects;

/** Item para un hover SHOW_ITEM, independiente de la plataforma. */
@ApiStatus.Experimental
public final class HoverItem {

    private final String itemKey;
    private final int amount;
    private final String snbt;

    /**
     * @param itemKey clave del item, p. ej. {@code minecraft:diamond_sword}
     * @param amount  cantidad
     * @param snbt    NBT del tag en formato SNBT (puede ser null)
     */
    public HoverItem(String itemKey, int amount, String snbt) {
        this.itemKey = Objects.requireNonNull(itemKey, "itemKey");
        this.amount = amount;
        this.snbt = snbt;
    }

    public String getItemKey() { return itemKey; }
    public int getAmount() { return amount; }
    public String getSnbt() { return snbt; }
}
