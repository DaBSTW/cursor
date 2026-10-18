package dev.pregen.trim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageDirsTest {

    @TempDir
    Path root;

    @Test
    void findsLegacyNetherSubfolder() throws IOException {
        Path worldFolder = root.resolve("world_nether");
        Files.createDirectories(worldFolder.resolve("DIM-1").resolve("region"));
        assertEquals(worldFolder.resolve("DIM-1").resolve("region"),
                StorageDirs.locate(worldFolder, World.Environment.NETHER).region());
    }

    @Test
    void findsPerDimensionFolderLayout() throws IOException {
        Path dimension = root.resolve("world").resolve("dimensions").resolve("minecraft").resolve("the_end");
        Files.createDirectories(dimension.resolve("region"));
        assertEquals(dimension.resolve("region"), StorageDirs.locate(dimension, World.Environment.THE_END).region());
    }

    @Test
    void overworldUsesItsOwnFolder() {
        assertEquals(root.resolve("region"), StorageDirs.locate(root, World.Environment.NORMAL).region());
    }

    @Test
    void parsesRegionFileNames() {
        assertEquals(new RegionFiles.RegionCoord(-3, 12), RegionFiles.parseName("r.-3.12.mca").orElseThrow());
        assertEquals(true, RegionFiles.parseName("r.1.2.mcc").isEmpty());
        assertEquals(true, RegionFiles.parseName("r.a.2.mca").isEmpty());
    }
}
