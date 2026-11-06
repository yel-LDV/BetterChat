package dev.betterchats.api.text;

import dev.betterchats.api.ApiStatus;

import java.util.Objects;

/**
 * Fragmento de texto enriquecido independiente de la plataforma. El texto y el
 * hover usan codigos legacy ({@code &a} o {@code §a}). Como lo construye un
 * plugin (no un jugador) puede llevar clic y hover.
 */
@ApiStatus.Experimental
public final class RichText {

    private final String text;
    private final String hoverText;
    private final HoverItem hoverItem;
    private final ClickAction clickAction;
    private final String clickValue;

    private RichText(Builder b) {
        this.text = b.text;
        this.hoverText = b.hoverText;
        this.hoverItem = b.hoverItem;
        this.clickAction = b.clickAction;
        this.clickValue = b.clickValue;
    }

    public static RichText legacy(String text) {
        return builder(text).build();
    }

    public static Builder builder(String text) {
        return new Builder(text);
    }

    public String getText() { return text; }
    public String getHoverText() { return hoverText; }
    public HoverItem getHoverItem() { return hoverItem; }
    public ClickAction getClickAction() { return clickAction; }
    public String getClickValue() { return clickValue; }

    public static final class Builder {
        private final String text;
        private String hoverText;
        private HoverItem hoverItem;
        private ClickAction clickAction;
        private String clickValue;

        private Builder(String text) {
            this.text = Objects.requireNonNull(text, "text");
        }

        /** Hover de texto. Si tambien hay {@link #hoverItem}, gana el item. */
        public Builder hoverText(String legacy) { this.hoverText = legacy; return this; }
        public Builder hoverItem(HoverItem item) { this.hoverItem = item; return this; }

        public Builder click(ClickAction action, String value) {
            this.clickAction = action;
            this.clickValue = value;
            return this;
        }

        public RichText build() {
            return new RichText(this);
        }
    }
}
