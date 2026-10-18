package dev.pregen.trim;

import dev.pregen.geometry.Shape;
import dev.pregen.trim.RegionFiles.RegionCoord;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Dry run of a trim: reads only region headers and reports what would be deleted. Never writes anything. With an
 * inhabited-time filter the chunk count is an upper bound, because reading every chunk's NBT here would make the
 * preview as slow as the trim itself.
 */
public final class TrimPlanner {

    public record Preview(int regionsScanned, int regionsAffected, int regionsDeletedEntirely, long chunks,
            long bytes) {
        public boolean isEmpty() {
            return chunks == 0;
        }
    }

    private TrimPlanner() {
    }

    public static Preview plan(TrimRequest request, StorageDirs dirs) throws IOException {
        Shape shape = request.selection().toShape();
        int scanned = 0;
        int affected = 0;
        int deletedEntirely = 0;
        long chunks = 0;
        long bytes = 0;
        for (RegionCoord region : RegionFiles.list(dirs.region())) {
            scanned++;
            Path file = dirs.region().resolve(region.fileName());
            int[] locations = RegionFiles.readLocations(file);
            int existing = 0;
            int selected = 0;
            for (int index = 0; index < locations.length; index++) {
                if (locations[index] == 0) {
                    continue;
                }
                existing++;
                if (request.selectsByPosition(shape, region.chunkX(index), region.chunkZ(index))) {
                    selected++;
                    bytes += (long) RegionFiles.sectorCount(locations[index]) * RegionFiles.SECTOR_BYTES;
                }
            }
            if (selected == 0) {
                continue;
            }
            affected++;
            chunks += selected;
            if (selected == existing && !request.hasInhabitedFilter()) {
                deletedEntirely++;
            }
        }
        return new Preview(scanned, affected, deletedEntirely, chunks, bytes);
    }
}
