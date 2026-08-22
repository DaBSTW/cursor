package dev.bookreports.update;

/**
 * Compares two dotted version strings (optionally {@code v}-prefixed, e.g. {@code "v1.2.0"}) numerically instead of
 * lexicographically — {@code "1.10.0"} must read as newer than {@code "1.9.0"}, which plain string comparison gets
 * backwards.
 */
final class VersionComparator {

    private VersionComparator() {
    }

    /** {@code true} if {@code candidate} is a strictly newer version than {@code current}. */
    static boolean isNewer(String candidate, String current) {
        int[] a = parse(candidate);
        int[] b = parse(current);
        if (a == null || b == null) {
            // Neither is a well-formed dotted-numeric version (e.g. a git-hash build tag) — fall back to "did the
            // string change at all", since we can't otherwise tell which direction is "newer".
            return !normalize(candidate).equalsIgnoreCase(normalize(current));
        }
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int ai = i < a.length ? a[i] : 0;
            int bi = i < b.length ? b[i] : 0;
            if (ai != bi) {
                return ai > bi;
            }
        }
        return false;
    }

    private static String normalize(String raw) {
        String trimmed = raw.trim();
        return trimmed.startsWith("v") || trimmed.startsWith("V") ? trimmed.substring(1) : trimmed;
    }

    private static int[] parse(String raw) {
        String[] parts = normalize(raw).split("\\.");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String digits = leadingDigits(parts[i]);
            if (digits.isEmpty()) {
                return null;
            }
            try {
                result[i] = Integer.parseInt(digits);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return result;
    }

    /** {@code "0-SNAPSHOT"} → {@code "0"}; lets a trailing pre-release/build suffix not break parsing. */
    private static String leadingDigits(String segment) {
        int end = 0;
        while (end < segment.length() && Character.isDigit(segment.charAt(end))) {
            end++;
        }
        return segment.substring(0, end);
    }
}
