package dev.bookreports.update;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VersionComparatorTest {

    @Test
    void detectsAHigherPatchVersionAsNewer() {
        assertTrue(VersionComparator.isNewer("1.0.1", "1.0.0"));
        assertFalse(VersionComparator.isNewer("1.0.0", "1.0.1"));
    }

    @Test
    void comparesNumericallyNotLexicographically() {
        // Plain string comparison would put "1.9.0" after "1.10.0" — this must not.
        assertTrue(VersionComparator.isNewer("1.10.0", "1.9.0"));
        assertFalse(VersionComparator.isNewer("1.9.0", "1.10.0"));
    }

    @Test
    void treatsEqualVersionsAsNotNewer() {
        assertFalse(VersionComparator.isNewer("1.2.3", "1.2.3"));
    }

    @Test
    void ignoresALeadingVPrefix() {
        assertTrue(VersionComparator.isNewer("v1.1.0", "1.0.0"));
        assertTrue(VersionComparator.isNewer("1.1.0", "v1.0.0"));
    }

    @Test
    void treatsAMissingTrailingSegmentAsZero() {
        assertTrue(VersionComparator.isNewer("1.1", "1.0.5"));
        assertFalse(VersionComparator.isNewer("1.0", "1.0.0"));
    }

    @Test
    void toleratesATrailingPreReleaseSuffix() {
        assertTrue(VersionComparator.isNewer("1.1.0-SNAPSHOT", "1.0.0"));
    }

    @Test
    void fallsBackToPlainInequalityForNonNumericVersions() {
        assertTrue(VersionComparator.isNewer("git-abc123", "git-def456"));
        assertFalse(VersionComparator.isNewer("git-abc123", "git-abc123"));
    }
}
