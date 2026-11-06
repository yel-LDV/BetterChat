package dev.betterchats.core.platform;

import java.util.List;

/**
 * Definicion de un comando para {@link CommandRegistrar}.
 *
 * @param takeOver si true, quita el alias a otro plugin que ya lo tenga;
 *                 si false, se salta los alias ocupados (queda {@code betterchats:alias}).
 */
public record CommandSpec(String name, List<String> aliases, String permission, String description,
                          boolean takeOver, CoreCommand executor) {

    public CommandSpec {
        aliases = List.copyOf(aliases);
    }

    /** Logica del comando, independiente de la plataforma. */
    public interface CoreCommand {

        void execute(CommandSource source, String label, String[] args);

        default List<String> complete(CommandSource source, String[] args) {
            return List.of();
        }
    }
}
