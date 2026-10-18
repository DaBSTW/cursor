package dev.pregen.trim;

import dev.pregen.trim.RegionFiles.RegionCoord;
import dev.pregen.util.ChunkPos;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Lists every chunk stored in a region folder, in a stable order (region by region, header order inside). */
public final class ExistingChunks {

    private ExistingChunks() {
    }

    public static long[] scan(Path regionDirectory) throws IOException {
        List<RegionCoord> regions = RegionFiles.list(regionDirectory);
        long[] chunks = new long[(int) Math.min(Integer.MAX_VALUE - 8L,
                (long) regions.size() * RegionFiles.CHUNKS_PER_REGION)];
        int count = 0;
        for (RegionCoord region : regions) {
            int[] locations = RegionFiles.readLocations(regionDirectory.resolve(region.fileName()));
            for (int index = 0; index < locations.length && count < chunks.length; index++) {
                if (locations[index] != 0) {
                    chunks[count++] = ChunkPos.pack(region.chunkX(index), region.chunkZ(index));
                }
            }
        }
        return Arrays.copyOf(chunks, count);
    }
}
