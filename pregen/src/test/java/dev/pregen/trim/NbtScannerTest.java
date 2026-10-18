package dev.pregen.trim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class NbtScannerTest {

    @Test
    void findsTheLongAfterSkippingNestedTags() throws IOException {
        byte[] nbt = TestRegions.chunkNbt(12_345);
        assertEquals(OptionalLong.of(12_345), NbtScanner.findLong(new ByteArrayInputStream(nbt), "InhabitedTime"));
    }

    @Test
    void findsTheLongInsideTheLegacyLevelCompound() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(10);
        out.writeUTF("");
        out.writeByte(10);
        out.writeUTF("Level");
        out.writeByte(1);
        out.writeUTF("TerrainPopulated");
        out.writeByte(1);
        out.writeByte(4);
        out.writeUTF("InhabitedTime");
        out.writeLong(77);
        out.writeByte(0);
        out.writeByte(0);
        assertEquals(OptionalLong.of(77),
                NbtScanner.findLong(new ByteArrayInputStream(bytes.toByteArray()), "InhabitedTime"));
    }

    @Test
    void returnsEmptyWhenTheTagIsMissing() throws IOException {
        byte[] nbt = TestRegions.chunkNbt(1);
        assertTrue(NbtScanner.findLong(new ByteArrayInputStream(nbt), "Missing").isEmpty());
    }

    @Test
    void rejectsTruncatedAndInvalidData() throws IOException {
        byte[] nbt = Arrays.copyOf(TestRegions.chunkNbt(1), 30);
        assertThrows(IOException.class, () -> NbtScanner.findLong(new ByteArrayInputStream(nbt), "Missing"));
        assertThrows(IOException.class, () -> NbtScanner.findLong(new ByteArrayInputStream(new byte[]{8, 0, 0}), "x"));
    }
}
