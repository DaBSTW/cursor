package dev.pregen.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ChunkPosTest {

    @ParameterizedTest
    @CsvSource({"0,0", "1,-1", "-1,1", "2147483647,-2147483648", "-1875000,1875000"})
    void packsAndUnpacksAnyCoordinate(int x, int z) {
        long packed = ChunkPos.pack(x, z);
        assertEquals(x, ChunkPos.x(packed));
        assertEquals(z, ChunkPos.z(packed));
    }

    @ParameterizedTest
    @CsvSource({"0,0", "15.9,0", "16,1", "-0.1,-1", "-16,-1", "-16.1,-2"})
    void convertsBlocksToChunksWithFloor(double block, int chunk) {
        assertEquals(chunk, ChunkPos.blockToChunk(block));
    }

    @ParameterizedTest
    @CsvSource({"0,0,0", "31,0,31", "0,1,32", "-1,-1,1023", "32,32,0", "-33,5,191"})
    void computesIndexInsideRegion(int x, int z, int index) {
        assertEquals(index, ChunkPos.indexInRegion(x, z));
    }
}
