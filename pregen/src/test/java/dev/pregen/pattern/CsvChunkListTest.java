package dev.pregen.pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.pregen.util.ChunkPos;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvChunkListTest {

    @Test
    void parsesPairsSkippingCommentsBlankLinesAndHeader() throws IOException {
        long[] chunks = CsvChunkList.parse(List.of("# exported", "x,z", "", "1,2", " -3 ; 4 ", "5,-6"));
        assertArrayEquals(new long[]{ChunkPos.pack(1, 2), ChunkPos.pack(-3, 4), ChunkPos.pack(5, -6)}, chunks);
    }

    @Test
    void rejectsMalformedLinesWithTheirLineNumber() {
        IOException error = assertThrows(IOException.class, () -> CsvChunkList.parse(List.of("1,2", "3")));
        assertTrue(error.getMessage().contains("Line 2"));
        assertThrows(IOException.class, () -> CsvChunkList.parse(List.of("1,2", "a,b")));
    }
}
