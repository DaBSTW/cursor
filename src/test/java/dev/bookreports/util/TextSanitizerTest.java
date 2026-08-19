package dev.bookreports.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class TextSanitizerTest {

    @Test
    void stripsLegacyAndAmpersandColorCodes() {
        assertEquals("Hacking", TextSanitizer.stripAndTruncate("&cHacking§l", 100));
    }

    @Test
    void truncatesToMaxLength() {
        String raw = "a".repeat(150);
        assertEquals(100, TextSanitizer.stripAndTruncate(raw, 100).length());
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertEquals("evidence", TextSanitizer.stripAndTruncate("  evidence  ", 100));
    }

    @Test
    void passesNullThrough() {
        assertNull(TextSanitizer.stripAndTruncate(null, 100));
    }
}
