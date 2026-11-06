package dev.betterchats.api.text;

import dev.betterchats.api.ApiStatus;

@ApiStatus.Experimental
public enum ClickAction {
    RUN_COMMAND,
    SUGGEST_COMMAND,
    OPEN_URL,
    /** Solo clientes 1.15+. */
    COPY_TO_CLIPBOARD
}
