package dev.pregen.trim;

import dev.pregen.geometry.Shape;
import dev.pregen.trim.RegionFiles.RegionCoord;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.BitSet;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeSet;
import java.util.function.LongSupplier;
import org.jetbrains.annotations.Nullable;

/**
 * Applies a trim to region files on disk. Must only run while the world is not loaded; the plugin guarantees that by
 * running pending trims during startup, before any world exists.
 *
 * <p>Idempotent, so a trim interrupted by a crash can simply run again: removed chunks stay removed, and existing
 * backups
 * are never overwritten with partially trimmed files.
 */
public final class TrimExecutor {

    private static final String INHABITED_TIME = "InhabitedTime";

    public record Result(int regionsModified, long chunksDeleted, int unreadableChunksKept, long millis) {
    }

    private final TrimRequest request;
    private final StorageDirs dirs;
    private final @Nullable Path backupDirectory;
    private final LongSupplier clock;
    private final Shape shape;
    private int unreadable;

    /**
     * @param backupDirectory where affected files are copied first; null disables backups
     */
    public TrimExecutor(TrimRequest request, StorageDirs dirs, @Nullable Path backupDirectory, LongSupplier clock) {
        this.request = request;
        this.dirs = dirs;
        this.backupDirectory = backupDirectory;
        this.clock = clock;
        this.shape = request.selection().toShape();
    }

    public Result run() throws IOException {
        long start = clock.getAsLong();
        int modified = 0;
        long deleted = 0;
        for (RegionCoord region : allRegions()) {
            BitSet doomed = chunksToDelete(region);
            if (doomed.isEmpty()) {
                continue;
            }
            backup(region);
            long removedBlocks = 0;
            for (Path directory : dirs.all()) {
                int removed = RegionFileEditor.removeChunks(directory.resolve(region.fileName()), region, doomed);
                if (directory.equals(dirs.region())) {
                    removedBlocks = removed;
                }
            }
            modified++;
            deleted += removedBlocks;
        }
        return new Result(modified, deleted, unreadable, clock.getAsLong() - start);
    }

    // Entities and POI files can exist without a matching block region file, so all three folders are scanned.
    private TreeSet<RegionCoord> allRegions() throws IOException {
        TreeSet<RegionCoord> regions = new TreeSet<>(
                (a, b) -> a.x() != b.x() ? Integer.compare(a.x(), b.x()) : Integer.compare(a.z(), b.z()));
        for (Path directory : dirs.all()) {
            regions.addAll(RegionFiles.list(directory));
        }
        return regions;
    }

    private BitSet chunksToDelete(RegionCoord region) throws IOException {
        BitSet present = new BitSet(RegionFiles.CHUNKS_PER_REGION);
        for (Path directory : dirs.all()) {
            int[] locations = RegionFiles.readLocations(directory.resolve(region.fileName()));
            for (int index = 0; index < locations.length; index++) {
                if (locations[index] != 0) {
                    present.set(index);
                }
            }
        }
        Path blockFile = dirs.region().resolve(region.fileName());
        int[] blockLocations = RegionFiles.readLocations(blockFile);
        BitSet doomed = new BitSet(RegionFiles.CHUNKS_PER_REGION);
        for (int index = present.nextSetBit(0); index >= 0; index = present.nextSetBit(index + 1)) {
            if (!request.selectsByPosition(shape, region.chunkX(index), region.chunkZ(index))) {
                continue;
            }
            if (request.hasInhabitedFilter() && blockLocations[index] != 0
                    && !isRarelyVisited(blockFile, region, index, blockLocations[index])) {
                continue;
            }
            doomed.set(index);
        }
        return doomed;
    }

    private boolean isRarelyVisited(Path blockFile, RegionCoord region, int index, int location) {
        try {
            Optional<InputStream> data = ChunkDataReader.open(blockFile, region, index, location);
            if (data.isEmpty()) {
                unreadable++;
                return false;
            }
            try (InputStream stream = data.get()) {
                OptionalLong inhabited = NbtScanner.findLong(stream, INHABITED_TIME);
                return inhabited.orElse(0L) <= request.maxInhabitedTicks();
            }
        } catch (IOException e) {
            // Unknown visit time: keeping the chunk is the only safe answer.
            unreadable++;
            return false;
        }
    }

    private void backup(RegionCoord region) throws IOException {
        if (backupDirectory == null) {
            return;
        }
        for (Path directory : dirs.all()) {
            Path source = directory.resolve(region.fileName());
            if (!Files.isRegularFile(source)) {
                continue;
            }
            Path target = backupDirectory.resolve(directory.getFileName().toString()).resolve(region.fileName());
            if (Files.exists(target)) {
                continue;
            }
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }
}
