package dev.example.soledom.loader;

import java.util.List;

import org.jetbrains.annotations.NotNull;

/** Thrown when a model or animation document is invalid. Carries every problem found, not just the first. */
public final class ValidationException extends Exception {

    private static final long serialVersionUID = 1L;

    private final String source;
    private final transient List<String> errors;

    public ValidationException(@NotNull String source, @NotNull List<String> errors) {
        super(source + ": " + String.join("; ", errors));
        this.source = source;
        this.errors = List.copyOf(errors);
    }

    public ValidationException(@NotNull String source, @NotNull String error) {
        this(source, List.of(error));
    }

    /** File name or other origin of the document. */
    public @NotNull String getSource() {
        return source;
    }

    public @NotNull List<String> getErrors() {
        return errors;
    }
}
