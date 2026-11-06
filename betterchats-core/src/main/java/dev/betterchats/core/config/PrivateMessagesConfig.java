package dev.betterchats.core.config;

import dev.betterchats.api.RateLimit;
import dev.betterchats.api.SoundSettings;

import java.util.List;

/** private_messages.json (raiz del plugin, nunca en chats/). */
public record PrivateMessagesConfig(
        boolean enabled,
        List<String> commandAliases,
        List<String> replyCommandAliases,
        boolean takeOverExistingCommands,
        String formatIncoming,
        String formatOutgoing,
        SoundSettings sound,
        boolean respectVanish,
        String vanishSeePermission,
        boolean allowSelfMessage,
        boolean notifyIgnoredSender,
        RateLimit rateLimit,
        Spy spy) {

    public record Spy(boolean enabled, String permission, String format) {
    }
}
