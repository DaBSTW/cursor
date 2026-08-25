package dev.bookreports.util;

/**
 * Strips injected color/format codes and enforces a max length (SPECS.md §13) — applied both where free text is
 * captured (the anvil GUI) and again in {@code ReportService}, since {@code BookReportsAPI} lets other plugins submit a
 * report without going through that GUI at all.
 */
public final class TextSanitizer {

    /** Max length for report evidence text, enforced client-side (anvil) and server-side alike. */
    public static final int EVIDENCE_MAX_LENGTH = 100;

    /**
     * Minimum length for evidence text that a reporter actually chose to type — a single stray character isn't
     * evidence, and rejecting it before submission (SelectOptionCommand) saves a full report round trip. Only applies
     * when they typed something at all; skipping the evidence step entirely is always allowed.
     */
    public static final int EVIDENCE_MIN_LENGTH = 5;

    private TextSanitizer() {
    }

    public static String stripAndTruncate(String raw, int maxLength) {
        if (raw == null) {
            return null;
        }
        String stripped = raw.replaceAll("[§&][0-9a-fk-orA-FK-OR]", "").strip();
        return stripped.length() > maxLength ? stripped.substring(0, maxLength) : stripped;
    }
}
