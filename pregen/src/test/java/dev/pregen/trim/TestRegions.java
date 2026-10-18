package dev.pregen.trim;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.DeflaterOutputStream;

/** Builds small but valid Anvil region files for tests. */
final class TestRegions {

    private TestRegions() {
    }

    /** Writes a region file whose chunks (by header index) hold the given InhabitedTime values. */
    static void write(Path file, Map<Integer, Long> inhabitedByIndex) throws IOException {
        Map<Integer, byte[]> payloads = new TreeMap<>();
        for (Map.Entry<Integer, Long> entry : inhabitedByIndex.entrySet()) {
            payloads.put(entry.getKey(), zlib(chunkNbt(entry.getValue())));
        }
        ByteBuffer header = ByteBuffer.allocate(RegionFiles.SECTOR_BYTES * 2);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int sector = 2;
        for (Map.Entry<Integer, byte[]> entry : payloads.entrySet()) {
            byte[] data = entry.getValue();
            int sectors = (data.length + 5 + RegionFiles.SECTOR_BYTES - 1) / RegionFiles.SECTOR_BYTES;
            header.putInt(entry.getKey() * 4, (sector << 8) | sectors);
            header.putInt(RegionFiles.SECTOR_BYTES + entry.getKey() * 4, 1_700_000_000);
            ByteBuffer chunk = ByteBuffer.allocate(sectors * RegionFiles.SECTOR_BYTES);
            chunk.putInt(data.length + 1);
            chunk.put((byte) ChunkDataReader.COMPRESSION_ZLIB);
            chunk.put(data);
            body.write(chunk.array());
            sector += sectors;
        }
        Files.createDirectories(file.getParent());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(header.array());
        out.write(body.toByteArray());
        Files.write(file, out.toByteArray());
    }

    /** Minimal modern chunk NBT: some noise before and after the InhabitedTime long. */
    static byte[] chunkNbt(long inhabited) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(10);
        out.writeUTF("");
        out.writeByte(3);
        out.writeUTF("DataVersion");
        out.writeInt(4189);
        out.writeByte(9);
        out.writeUTF("sections");
        out.writeByte(10);
        out.writeInt(2);
        for (int i = 0; i < 2; i++) {
            out.writeByte(12);
            out.writeUTF("data");
            out.writeInt(3);
            out.writeLong(1);
            out.writeLong(2);
            out.writeLong(3);
            out.writeByte(8);
            out.writeUTF("name");
            out.writeUTF("minecraft:stone");
            out.writeByte(0);
        }
        out.writeByte(4);
        out.writeUTF("InhabitedTime");
        out.writeLong(inhabited);
        out.writeByte(7);
        out.writeUTF("bytes");
        out.writeInt(4);
        out.write(new byte[]{1, 2, 3, 4});
        out.writeByte(0);
        return bytes.toByteArray();
    }

    private static byte[] zlib(byte[] data) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DeflaterOutputStream out = new DeflaterOutputStream(bytes)) {
            out.write(data);
        }
        return bytes.toByteArray();
    }
}
