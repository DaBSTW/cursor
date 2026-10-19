package dev.example.soledom.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ServerVersionTest {

    @ParameterizedTest
    @CsvSource({
            "1.21, 1, 21, 0",
            "1.21.4, 1, 21, 4",
            "1.21.4-R0.1-SNAPSHOT, 1, 21, 4",
            "26.1, 26, 1, 0",
            "26.3.1, 26, 3, 1",
            "26.3.build.205-stable, 26, 3, 0",
            "git-Paper-123 (MC: 1.21.11), 1, 21, 11"
    })
    void parsesClassicAndYearBasedVersions(String raw, int major, int minor, int patch) {
        ServerVersion version = ServerVersion.parse(raw);
        assertTrue(version.isKnown());
        assertEquals(major, version.major());
        assertEquals(minor, version.minor());
        assertEquals(patch, version.patch());
    }

    @Test
    void unparsableVersionsAreUnknown() {
        assertSame(ServerVersion.UNKNOWN, ServerVersion.parse(null));
        assertSame(ServerVersion.UNKNOWN, ServerVersion.parse(""));
        assertSame(ServerVersion.UNKNOWN, ServerVersion.parse("snapshot"));
        assertFalse(ServerVersion.UNKNOWN.isKnown());
    }

    @Test
    void yearBasedVersionsCompareAboveClassicOnes() {
        ServerVersion v26 = ServerVersion.parse("26.1");
        assertTrue(v26.isAtLeast(1, 21, 11));
        assertTrue(v26.compareTo(ServerVersion.parse("1.21.11")) > 0);
        assertTrue(ServerVersion.parse("1.21.4").isAtLeast(1, 21, 4));
        assertFalse(ServerVersion.parse("1.21.3").isAtLeast(1, 21, 4));
        assertTrue(ServerVersion.parse("1.21.10").isAtLeast(1, 21, 9));
    }

    @Test
    void newerThanTestedIgnoresPatchesOfTheTestedLine() {
        assertFalse(ServerVersion.parse("26.3").isNewerThanTested());
        assertFalse(ServerVersion.parse("26.3.2").isNewerThanTested());
        assertFalse(ServerVersion.parse("1.21.4").isNewerThanTested());
        assertTrue(ServerVersion.parse("26.4").isNewerThanTested());
        assertTrue(ServerVersion.parse("27.1").isNewerThanTested());
    }

    @Test
    void featureFlagsDetectApiOnClasspathAndAssumeModernForUnknownVersions() {
        FeatureFlags detected = FeatureFlags.detect(ServerVersion.parse("1.21.4"));
        assertTrue(detected.hasDisplayEntities());
        assertTrue(detected.hasInteractionEntities());
        assertTrue(detected.has(Capability.CUSTOM_MODEL_DATA_COMPONENT));
        assertTrue(detected.has(Capability.ITEM_MODEL_DEFINITIONS));
        assertFalse(detected.has(Capability.NEW_PACK_FORMAT));
        assertFalse(detected.hasFolia());

        assertFalse(FeatureFlags.detect(ServerVersion.parse("1.21.1")).has(Capability.ITEM_MODEL_DEFINITIONS));
        assertTrue(FeatureFlags.detect(ServerVersion.UNKNOWN).has(Capability.NEW_PACK_FORMAT));
        assertEquals(EnumSet.of(Capability.FOLIA_SCHEDULER),
                new FeatureFlags(EnumSet.of(Capability.FOLIA_SCHEDULER)).all());
    }
}
