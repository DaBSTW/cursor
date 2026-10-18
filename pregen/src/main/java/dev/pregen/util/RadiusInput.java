package dev.pregen.util;

import java.util.Locale;
import java.util.Optional;

/**
 * A radius typed by a user: an absolute value or a relative change, with an optional unit suffix.
 *
 * <p>Accepted forms: {@code 500} (blocks), {@code 10c} (chunks), {@code 2r} (regions), {@code 1.5k} (thousands
 * of blocks),
 * each optionally prefixed with {@code +} or {@code -} to change the current radius.
 */
public record RadiusInput(double blocks, Mode mode) {

    public static final double MAX_RADIUS = 30_000_000.0;

    public enum Mode {
        ABSOLUTE,
        ADD,
        SUBTRACT
    }

    public static Optional<RadiusInput> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String text = raw.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return Optional.empty();
        }
        Mode mode = switch (text.charAt(0)) {
            case '+' -> Mode.ADD;
            case '-' -> Mode.SUBTRACT;
            default -> Mode.ABSOLUTE;
        };
        if (mode != Mode.ABSOLUTE) {
            text = text.substring(1);
        }
        if (text.isEmpty()) {
            return Optional.empty();
        }
        double unit = switch (text.charAt(text.length() - 1)) {
            case 'c' -> 16.0;
            case 'r' -> 512.0;
            case 'k' -> 1000.0;
            case 'b' -> 1.0;
            default -> 0.0;
        };
        if (unit != 0.0) {
            text = text.substring(0, text.length() - 1);
        }
        double multiplier = unit == 0.0 ? 1.0 : unit;
        return parseNumber(text).map(value -> value * multiplier)
                .filter(value -> Double.isFinite(value) && value > 0.0 && value <= MAX_RADIUS)
                .map(value -> new RadiusInput(value, mode));
    }

    /** Applies this input to the current radius; empty when the result is not a valid radius. */
    public Optional<Double> applyTo(double current) {
        double result = switch (mode) {
            case ABSOLUTE -> blocks;
            case ADD -> current + blocks;
            case SUBTRACT -> current - blocks;
        };
        if (result <= 0.0 || result > MAX_RADIUS) {
            return Optional.empty();
        }
        return Optional.of(result);
    }

    private static Optional<Double> parseNumber(String text) {
        if (text.isEmpty() || !text.chars().allMatch(c -> Character.isDigit(c) || c == '.')) {
            return Optional.empty();
        }
        try {
            return Optional.of(Double.parseDouble(text));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
