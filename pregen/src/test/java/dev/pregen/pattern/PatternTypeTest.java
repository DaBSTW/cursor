package dev.pregen.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.pregen.util.ChunkPos;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PatternTypeTest {

    static Stream<Arguments> geometricPatternsAndBounds() {
        List<ChunkBounds> bounds = List.of(new ChunkBounds(0, 0, 0, 0), new ChunkBounds(-10, -10, 10, 10),
                new ChunkBounds(-3, 5, 40, 9), new ChunkBounds(-70, -33, -2, 64), new ChunkBounds(5, 5, 6, 100));
        return Stream.of(PatternType.REGION, PatternType.CONCENTRIC, PatternType.SPIRAL, PatternType.LOOP)
                .flatMap(type -> bounds.stream().map(bound -> Arguments.of(type, bound)));
    }

    @ParameterizedTest
    @MethodSource("geometricPatternsAndBounds")
    void visitsEveryChunkOfTheBoundsExactlyOnce(PatternType type, ChunkBounds bounds) {
        int centerX = (bounds.minX() + bounds.maxX()) / 2;
        int centerZ = (bounds.minZ() + bounds.maxZ()) / 2;
        ChunkPattern pattern = type.create(bounds, centerX, centerZ);
        Set<Long> seen = new HashSet<>();
        while (pattern.hasNext()) {
            long packed = pattern.next();
            assertTrue(bounds.contains(ChunkPos.x(packed), ChunkPos.z(packed)), "outside bounds");
            assertTrue(seen.add(packed), "visited twice");
        }
        assertEquals(bounds.area(), seen.size());
        assertEquals(bounds.area(), pattern.position());
        assertFalse(pattern.hasNext());
    }

    @ParameterizedTest
    @MethodSource("geometricPatternsAndBounds")
    void replayingAndSkippingResumesAtTheSameCoordinate(PatternType type, ChunkBounds bounds) {
        int centerX = bounds.minX();
        int centerZ = bounds.maxZ();
        List<Long> full = drain(type.create(bounds, centerX, centerZ));
        int resumeAt = full.size() / 3;
        ChunkPattern resumed = type.create(bounds, centerX, centerZ);
        for (int i = 0; i < resumeAt; i++) {
            resumed.next();
        }
        assertEquals(full.subList(resumeAt, full.size()), drain(resumed));
    }

    @ParameterizedTest
    @MethodSource("geometricPatternsAndBounds")
    void concentricAndSpiralStartAtTheCenter(PatternType type, ChunkBounds bounds) {
        if (type != PatternType.CONCENTRIC && type != PatternType.SPIRAL) {
            return;
        }
        int centerX = (bounds.minX() + bounds.maxX()) / 2;
        int centerZ = (bounds.minZ() + bounds.maxZ()) / 2;
        assertEquals(ChunkPos.pack(centerX, centerZ), type.create(bounds, centerX, centerZ).next());
    }

    @Test
    void regionPatternFinishesARegionBeforeTheNext() {
        ChunkBounds bounds = new ChunkBounds(-40, -40, 40, 40);
        List<Long> order = drain(PatternType.REGION.create(bounds, 0, 0));
        Set<Long> finishedRegions = new HashSet<>();
        long current = Long.MIN_VALUE;
        for (long packed : order) {
            long region = ChunkPos.pack(ChunkPos.chunkToRegion(ChunkPos.x(packed)),
                    ChunkPos.chunkToRegion(ChunkPos.z(packed)));
            if (region != current) {
                assertTrue(finishedRegions.add(region), "region revisited");
                current = region;
            }
        }
    }

    @Test
    void listPatternKeepsOrderAndSkipsShapeFilter() {
        long[] chunks = {ChunkPos.pack(5, 5), ChunkPos.pack(-1, 2)};
        ChunkPattern pattern = PatternType.fromList(chunks);
        assertFalse(pattern.appliesShapeFilter());
        assertEquals(List.of(chunks[0], chunks[1]), drain(pattern));
    }

    private static List<Long> drain(ChunkPattern pattern) {
        List<Long> result = new ArrayList<>();
        while (pattern.hasNext()) {
            result.add(pattern.next());
        }
        return result;
    }
}
