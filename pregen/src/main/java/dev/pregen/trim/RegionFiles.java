package dev.pregen.trim;

import dev.pregen.util.ChunkPos;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reading helpers for Anvil region files ({@code r.<x>.<z>.mca}). A region file starts with a 4 KiB table of 1024 chunk
 * locations (3 bytes sector offset, 1 byte sector count, big endian), followed by a 4 KiB timestamp table.
 */
public final class RegionFiles {

    public static final int SECTOR_BYTES = 4096;
    public static final int CHUNKS_PER_REGION = 1024;
    private static final Pattern REGION_NAME = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

    private RegionFiles() {
    }

    public record RegionCoord(int x, int z) {
        public String fileName() {
            return "r." + x + "." + z + ".mca";
        }

        public int chunkX(int index) {
            return x * ChunkPos.REGION_SIZE + index % ChunkPos.REGION_SIZE;
        }

        public int chunkZ(int index) {
            return z * ChunkPos.REGION_SIZE + index / ChunkPos.REGION_SIZE;
        }
    }

    public static Optional<RegionCoord> parseName(String fileName) {
        Matcher matcher = REGION_NAME.matcher(fileName);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new RegionCoord(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** Region files in the directory, sorted by x then z so every scan visits them in the same order. */
    public static List<RegionCoord> list(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<RegionCoord> regions = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            files.forEach(file -> parseName(file.getFileName().toString()).ifPresent(regions::add));
        }
        regions.sort(Comparator.comparingInt(RegionCoord::x).thenComparingInt(RegionCoord::z));
        return regions;
    }

    /** The 1024 location entries; all zero for a missing, empty or truncated file. */
    public static int[] readLocations(Path regionFile) throws IOException {
        int[] locations = new int[CHUNKS_PER_REGION];
        if (!Files.isRegularFile(regionFile)) {
            return locations;
        }
        try (FileChannel channel = FileChannel.open(regionFile, StandardOpenOption.READ)) {
            long fileSize = channel.size();
            if (fileSize < SECTOR_BYTES) {
                return locations;
            }
            ByteBuffer header = ByteBuffer.allocate(SECTOR_BYTES);
            while (header.hasRemaining() && channel.read(header, header.position()) > 0) {
                // Positional reads may return short counts; keep reading until the header is full.
            }
            header.flip();
            for (int i = 0; i < CHUNKS_PER_REGION && header.remaining() >= 4; i++) {
                int location = header.getInt();
                locations[i] = isPlausible(location, fileSize) ? location : 0;
            }
        }
        return locations;
    }

    public static long sectorOffset(int location) {
        return (location >>> 8) & 0xFFFFFFL;
    }

    public static int sectorCount(int location) {
        return location & 0xFF;
    }

    /** Rejects entries pointing into the header or past the end of the file (corruption, not chunks). */
    private static boolean isPlausible(int location, long fileSize) {
        if (location == 0) {
            return false;
        }
        long offset = sectorOffset(location);
        int count = sectorCount(location);
        return offset >= 2 && count > 0 && (offset + count) * SECTOR_BYTES <= fileSize + SECTOR_BYTES;
    }

    public static int countChunks(int[] locations) {
        int count = 0;
        for (int location : locations) {
            if (location != 0) {
                count++;
            }
        }
        return count;
    }
}
