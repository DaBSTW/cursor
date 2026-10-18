package dev.pregen.trim;

import dev.pregen.trim.RegionFiles.RegionCoord;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/** Opens the decompressed NBT payload of one chunk inside a region file. */
public final class ChunkDataReader {

    static final int COMPRESSION_GZIP = 1;
    static final int COMPRESSION_ZLIB = 2;
    static final int COMPRESSION_NONE = 3;
    static final int EXTERNAL_FLAG = 0x80;

    private ChunkDataReader() {
    }

    /** Payload header of a stored chunk: compression byte (with the external flag) and payload length. */
    public record ChunkHeader(int compression, int length) {
        public boolean isExternal() {
            return (compression & EXTERNAL_FLAG) != 0;
        }

        public int compressionType() {
            return compression & ~EXTERNAL_FLAG;
        }
    }

    public static ChunkHeader readHeader(FileChannel channel, int location) throws IOException {
        long position = RegionFiles.sectorOffset(location) * RegionFiles.SECTOR_BYTES;
        ByteBuffer buffer = ByteBuffer.allocate(5);
        readFully(channel, buffer, position);
        buffer.flip();
        int length = buffer.getInt();
        int compression = buffer.get() & 0xFF;
        return new ChunkHeader(compression, length);
    }

    /**
     * Returns the decompressed NBT stream, or empty when the compression is not supported (LZ4, custom) or the data is
     * unusable. Callers must treat empty as "unknown", never as "safe to delete".
     */
    public static Optional<InputStream> open(Path regionFile, RegionCoord region, int index, int location)
            throws IOException {
        try (FileChannel channel = FileChannel.open(regionFile)) {
            ChunkHeader header = readHeader(channel, location);
            byte[] payload;
            if (header.isExternal()) {
                Path external = externalFile(regionFile, region, index);
                if (!Files.isRegularFile(external)) {
                    return Optional.empty();
                }
                payload = Files.readAllBytes(external);
            } else {
                int maxLength = RegionFiles.sectorCount(location) * RegionFiles.SECTOR_BYTES - 4;
                if (header.length() <= 1 || header.length() > maxLength) {
                    return Optional.empty();
                }
                ByteBuffer data = ByteBuffer.allocate(header.length() - 1);
                readFully(channel, data, RegionFiles.sectorOffset(location) * RegionFiles.SECTOR_BYTES + 5);
                payload = data.array();
            }
            return decompress(header.compressionType(), payload);
        }
    }

    /** Oversized chunks are stored next to the region file as {@code c.<chunkX>.<chunkZ>.mcc}. */
    public static Path externalFile(Path regionFile, RegionCoord region, int index) {
        return regionFile.resolveSibling("c." + region.chunkX(index) + "." + region.chunkZ(index) + ".mcc");
    }

    private static Optional<InputStream> decompress(int type, byte[] payload) throws IOException {
        InputStream raw = new ByteArrayInputStream(payload);
        return switch (type) {
            case COMPRESSION_GZIP -> Optional.of(new GZIPInputStream(raw));
            case COMPRESSION_ZLIB -> Optional.of(new InflaterInputStream(raw));
            case COMPRESSION_NONE -> Optional.of(raw);
            default -> Optional.empty();
        };
    }

    private static void readFully(FileChannel channel, ByteBuffer buffer, long position) throws IOException {
        long current = position;
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, current);
            if (read < 0) {
                throw new IOException("Unexpected end of region file at position " + current);
            }
            current += read;
        }
    }
}
