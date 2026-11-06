package dev.betterchats.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resultado de una recarga. Si {@link #isSuccess()} es false se conservo la
 * configuracion anterior.
 */
@ApiStatus.Stable
public final class ReloadResult {

    private final boolean success;
    private final List<String> errors;
    private final List<String> warnings;

    public ReloadResult(boolean success, List<String> errors, List<String> warnings) {
        this.success = success;
        this.errors = Collections.unmodifiableList(new ArrayList<String>(errors));
        this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
    }

    public boolean isSuccess() { return success; }
    /** Formato: {@code archivo -> campo: problema (sugerencia)}. */
    public List<String> getErrors() { return errors; }
    public List<String> getWarnings() { return warnings; }
}
