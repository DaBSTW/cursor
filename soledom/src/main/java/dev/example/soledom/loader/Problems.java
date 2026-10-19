package dev.example.soledom.loader;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

/** Collects validation errors and warnings with JSON-path style locations ({@code parts[1].scale}). */
public final class Problems {

    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public void error(@NotNull String path, @NotNull String message) {
        errors.add(path.isEmpty() ? message : path + ": " + message);
    }

    public void warn(@NotNull String path, @NotNull String message) {
        warnings.add(path.isEmpty() ? message : path + ": " + message);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public @NotNull List<String> errors() {
        return List.copyOf(errors);
    }

    public @NotNull List<String> warnings() {
        return List.copyOf(warnings);
    }

    /** @throws ValidationException if any error was recorded */
    public void throwIfErrors(@NotNull String source) throws ValidationException {
        if (!errors.isEmpty()) {
            throw new ValidationException(source, errors);
        }
    }
}
