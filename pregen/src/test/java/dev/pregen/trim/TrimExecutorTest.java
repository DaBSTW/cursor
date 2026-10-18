package dev.pregen.trim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.pregen.geometry.ShapeType;
import dev.pregen.pattern.PatternType;
import dev.pregen.selection.Selection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrimExecutorTest {

    @TempDir
    Path world;

    private StorageDirs dirs;

    // Region 0,0 covers chunks 0..31; region -1,0 covers chunks -32..-1 on x.
    private static final Selection KEEP_FIRST_CHUNKS = new Selection("world", 0, 0, 40, 40, ShapeType.SQUARE,
            PatternType.REGION, "");

    @BeforeEach
    void createWorld() throws IOException {
        dirs = StorageDirs.under(world);
        Map<Integer, Long> nearOrigin = new HashMap<>();
        nearOrigin.put(0, 0L);
        nearOrigin.put(1, 5_000L);
        nearOrigin.put(10, 10L);
        nearOrigin.put(33 * 1, 20L);
        TestRegions.write(dirs.region().resolve("r.0.0.mca"), nearOrigin);
        TestRegions.write(dirs.entities().resolve("r.0.0.mca"), Map.of(10, 0L));
        TestRegions.write(dirs.region().resolve("r.-1.0.mca"), Map.of(5, 0L, 6, 0L));
    }

    @Test
    void previewCountsWithoutTouchingFiles() throws IOException {
        long sizeBefore = Files.size(dirs.region().resolve("r.0.0.mca"));
        TrimPlanner.Preview preview = TrimPlanner.plan(new TrimRequest(KEEP_FIRST_CHUNKS, false, -1), dirs);
        // Outside a 40-block square: chunk 10 of r.0.0 and both chunks of r.-1.0 (x=-27,-26).
        assertEquals(3, preview.chunks());
        assertEquals(2, preview.regionsAffected());
        assertEquals(1, preview.regionsDeletedEntirely());
        assertEquals(sizeBefore, Files.size(dirs.region().resolve("r.0.0.mca")));
    }

    @Test
    void deletesOutsideChunksAndWholeRegionsWithBackups() throws IOException {
        Path backup = world.resolve("backup");
        TrimExecutor.Result result = new TrimExecutor(new TrimRequest(KEEP_FIRST_CHUNKS, false, -1), dirs, backup,
                () -> 0L).run();

        assertEquals(3, result.chunksDeleted());
        assertFalse(Files.exists(dirs.region().resolve("r.-1.0.mca")));
        int[] remaining = RegionFiles.readLocations(dirs.region().resolve("r.0.0.mca"));
        assertTrue(remaining[0] != 0 && remaining[1] != 0 && remaining[33] != 0);
        assertEquals(0, remaining[10]);
        // Entities of the removed chunk go too; the file then has no chunk left and is deleted.
        assertFalse(Files.exists(dirs.entities().resolve("r.0.0.mca")));
        assertTrue(Files.exists(backup.resolve("region").resolve("r.-1.0.mca")));
        assertTrue(Files.exists(backup.resolve("entities").resolve("r.0.0.mca")));
    }

    @Test
    void inhabitedFilterKeepsVisitedChunks() throws IOException {
        Selection tiny = KEEP_FIRST_CHUNKS.withRadius(1);
        new TrimExecutor(new TrimRequest(tiny, false, 100), dirs, null, () -> 0L).run();
        int[] remaining = RegionFiles.readLocations(dirs.region().resolve("r.0.0.mca"));
        assertTrue(remaining[1] != 0, "5000 ticks > 100: kept");
        assertEquals(0, remaining[10]);
        assertEquals(0, remaining[33]);
    }

    @Test
    void insideModeDeletesTheSelection() throws IOException {
        new TrimExecutor(new TrimRequest(KEEP_FIRST_CHUNKS, true, -1), dirs, null, () -> 0L).run();
        int[] remaining = RegionFiles.readLocations(dirs.region().resolve("r.0.0.mca"));
        assertEquals(0, remaining[0]);
        assertEquals(0, remaining[1]);
        assertTrue(remaining[10] != 0);
        assertTrue(Files.exists(dirs.region().resolve("r.-1.0.mca")));
    }

    @Test
    void runningTwiceIsHarmlessAndKeepsTheOriginalBackup() throws IOException {
        Path backup = world.resolve("backup");
        TrimRequest request = new TrimRequest(KEEP_FIRST_CHUNKS, false, -1);
        new TrimExecutor(request, dirs, backup, () -> 0L).run();
        long backupSize = Files.size(backup.resolve("region").resolve("r.0.0.mca"));
        TrimExecutor.Result second = new TrimExecutor(request, dirs, backup, () -> 0L).run();
        assertEquals(0, second.chunksDeleted());
        assertEquals(backupSize, Files.size(backup.resolve("region").resolve("r.0.0.mca")));
        assertEquals(4,
                RegionFiles.countChunks(RegionFiles.readLocations(backup.resolve("region").resolve("r.0.0.mca"))));
    }

    @Test
    void listsExistingChunksForTheWorldPattern() throws IOException {
        long[] chunks = ExistingChunks.scan(dirs.region());
        assertEquals(6, chunks.length);
    }
}
