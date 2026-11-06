package dev.betterchats.core.config;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** config.json (raiz del plugin). */
public record MainConfig(
        int configVersion,
        String language,
        Persistence persistence,
        Chat chat,
        Security security,
        AntiSpam antiSpam,
        Snapshot snapshot,
        Map<String, List<String>> commands,
        boolean takeOverExistingCommands,
        boolean devAutoReload) {

    public enum Strategy {
        /** Mantiene el evento de chat (en 1.8 se fija el formato); compatible con otros plugins. */
        RENDERER,
        /** Cancela el evento y envia componentes propios. */
        CANCEL,
        /** RENDERER para el chat por defecto, CANCEL para chats privados o mensajes con hover/clic. */
        HYBRID
    }

    public record Persistence(String type, int autosaveSeconds) {
    }

    public record Chat(Strategy strategy, boolean fallbackToDefaultOnPermissionLoss, String permissionLostMessage,
                       String listenerPriority, boolean legacyColorsInFormats) {
    }

    /** @param blockLookupPatterns bloquear {@code ${...}} (Log4Shell) en chat y PMs */
    public record Security(Set<String> defaultAllowedPlayerTags, String bypassPermission,
                           boolean blockLookupPatterns) {
    }

    public record AntiSpam(boolean enabled, int duplicateCooldownSeconds, int maxLength, String bypassPermission) {
    }

    /**
     * @param refreshTicks cada cuantos ticks se refresca el snapshot en el hilo principal
     * @param placeholders placeholders de PlaceholderAPI que se precalculan en el
     *                     hilo principal (los que no son seguros en async)
     */
    public record Snapshot(int refreshTicks, List<String> placeholders) {
    }

    public List<String> commandLabels(String key, List<String> fallback) {
        List<String> v = commands.get(key);
        return v == null || v.isEmpty() ? fallback : v;
    }
}
