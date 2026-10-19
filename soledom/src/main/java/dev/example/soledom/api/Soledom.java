package dev.example.soledom.api;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Static accessor for the {@link SoledomAPI} instance. */
public final class Soledom {

    private static volatile SoledomAPI instance;

    private Soledom() {
    }

    /** @throws IllegalStateException if Soledom is not enabled */
    public static @NotNull SoledomAPI api() {
        SoledomAPI api = instance;
        if (api == null) {
            throw new IllegalStateException("Soledom is not enabled");
        }
        return api;
    }

    public static boolean isAvailable() {
        return instance != null;
    }

    @ApiStatus.Internal
    public static void install(SoledomAPI api) {
        instance = api;
    }
}
