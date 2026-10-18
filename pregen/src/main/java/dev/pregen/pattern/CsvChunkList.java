package dev.pregen.pattern;

import dev.pregen.util.ChunkPos;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Reads chunk lists for the {@code csv} pattern: one {@code x,z} chunk coordinate pair per line ({@code ;} also
 * accepted). Blank lines and lines starting with {@code #} are ignored; a header line with non-numeric text is skipped.
 */
public final class CsvChunkList {

    private CsvChunkList() {
    }

    public static long[] read(Path file) throws IOException {
        return parse(Files.readAllLines(file, StandardCharsets.UTF_8));
    }

    static long[] parse(List<String> lines) throws IOException {
        long[] chunks = new long[lines.size()];
        int count = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("[,;]");
            if (parts.length != 2) {
                throw new IOException("Line " + (i + 1) + ": expected 'x,z' but got '" + line + "'");
            }
            try {
                int x = Integer.parseInt(parts[0].trim());
                int z = Integer.parseInt(parts[1].trim());
                chunks[count++] = ChunkPos.pack(x, z);
            } catch (NumberFormatException e) {
                if (count == 0 && i == firstContentLine(lines)) {
                    continue;
                }
                throw new IOException("Line " + (i + 1) + ": coordinates must be whole numbers: '" + line + "'", e);
            }
        }
        return Arrays.copyOf(chunks, count);
    }

    private static int firstContentLine(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (!line.isEmpty() && !line.startsWith("#")) {
                return i;
            }
        }
        return -1;
    }
}
