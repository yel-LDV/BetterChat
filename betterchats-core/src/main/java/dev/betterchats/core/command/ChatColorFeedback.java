package dev.betterchats.core.command;

import dev.betterchats.core.ChatStyleService;
import dev.betterchats.core.config.Lang;
import net.kyori.adventure.text.Component;

/** Acceso publico a los mensajes de /chatcolor para el menu del adaptador. */
public final class ChatColorFeedback {

    private ChatColorFeedback() {
    }

    public static Component of(Lang lang, ChatStyleService.Result result, Component name) {
        return ChatColorCommand.feedback(lang, result, name);
    }
}
