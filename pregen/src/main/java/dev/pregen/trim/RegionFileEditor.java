package dev.pregen.trim;

import dev.pregen.trim.RegionFiles.RegionCoord;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Removes chunks from a region file by zeroing their location and timestamp entries. The game treats a zero entry as
 * "chunk not generated" and reuses the orphaned sectors later, so the data itself does not need rewriting. Only ever
 * used while the world is not loaded.
 */
public final class RegionFileEditor {

    private RegionFileEditor() {
    }

    /**
     * Clears the given chunk indices; deletes the file when no chunk remains.
     *
     * @return number of chunks that existed and were removed
     */
    public static int removeChunks(Path regionFile, RegionCoord region, BitSet indices) throws IOException {
        if (indices.isEmpty() || !Files.isRegularFile(regionFile)) {
            return 0;
        }
        int[] locations = RegionFiles.readLocations(regionFile);
        int removed = 0;
        List<Path> externalFiles = new ArrayList<>();
        try (FileChannel channel = FileChannel.open(regionFile, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            ByteBuffer zero = ByteBuffer.allocate(4);
            for (int index = indices.nextSetBit(0); index >= 0; index = indices.nextSetBit(index + 1)) {
                if (locations[index] == 0) {
                    continue;
                }
                if (ChunkDataReader.readHeader(channel, locations[index]).isExternal()) {
                    externalFiles.add(ChunkDataReader.externalFile(regionFile, region, index));
                }
                writeZero(channel, zero, index * 4L);
                writeZero(channel, zero, RegionFiles.SECTOR_BYTES + index * 4L);
                locations[index] = 0;
                removed++;
            }
            channel.force(true);
        }
        for (Path external : externalFiles) {
            Files.deleteIfExists(external);
        }
        if (RegionFiles.countChunks(locations) == 0) {
            Files.deleteIfExists(regionFile);
        }
        return removed;
    }

    private static void writeZero(FileChannel channel, ByteBuffer zero, long position) throws IOException {
        zero.clear();
        while (zero.hasRemaining()) {
            channel.write(zero, position + zero.position());
        }
    }
}
