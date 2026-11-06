package dev.betterchats.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Nivel de garantia de cada parte de la API (semver).
 *
 * <ul>
 *   <li>{@link Stable}: solo cambia de forma incompatible en una version mayor.</li>
 *   <li>{@link Experimental}: puede cambiar en cualquier version menor.</li>
 *   <li>{@link Internal}: uso interno de BetterChats, no usar desde otros plugins.</li>
 *   <li>{@link ScheduledForRemoval}: obsoleto, se elimina en la version indicada.</li>
 * </ul>
 */
public final class ApiStatus {

    private ApiStatus() {
    }

    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
    public @interface Stable {
    }

    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
    public @interface Experimental {
    }

    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
    public @interface Internal {
    }

    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
    public @interface ScheduledForRemoval {
        /** Version en la que se elimina. */
        String inVersion();
    }
}
