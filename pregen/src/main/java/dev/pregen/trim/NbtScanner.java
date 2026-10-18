package dev.pregen.trim;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.OptionalLong;

/**
 * Streams through chunk NBT looking for one long tag, without building a tree. It stops as soon as the tag is found, so
 * reading {@code InhabitedTime} usually touches a small prefix of the chunk.
 *
 * <p>The tag is searched at the root and inside a {@code Level} compound (the chunk layout before 1.18).
 */
public final class NbtScanner {

    private static final int TAG_END = 0;
    private static final int TAG_BYTE = 1;
    private static final int TAG_SHORT = 2;
    private static final int TAG_INT = 3;
    private static final int TAG_LONG = 4;
    private static final int TAG_FLOAT = 5;
    private static final int TAG_DOUBLE = 6;
    private static final int TAG_BYTE_ARRAY = 7;
    private static final int TAG_STRING = 8;
    private static final int TAG_LIST = 9;
    private static final int TAG_COMPOUND = 10;
    private static final int TAG_INT_ARRAY = 11;
    private static final int TAG_LONG_ARRAY = 12;
    private static final int MAX_DEPTH = 512;
    private static final String LEGACY_LEVEL = "Level";

    private NbtScanner() {
    }

    public static OptionalLong findLong(InputStream stream, String name) throws IOException {
        DataInputStream input = new DataInputStream(stream);
        int rootType = input.readUnsignedByte();
        if (rootType != TAG_COMPOUND) {
            throw new IOException("Chunk NBT root is not a compound but type " + rootType);
        }
        input.readUTF();
        return searchCompound(input, name, true);
    }

    private static OptionalLong searchCompound(DataInputStream input, String name, boolean descendIntoLevel)
            throws IOException {
        while (true) {
            int type = input.readUnsignedByte();
            if (type == TAG_END) {
                return OptionalLong.empty();
            }
            String tagName = input.readUTF();
            if (type == TAG_LONG && tagName.equals(name)) {
                return OptionalLong.of(input.readLong());
            }
            if (type == TAG_COMPOUND && descendIntoLevel && LEGACY_LEVEL.equals(tagName)) {
                OptionalLong found = searchCompound(input, name, false);
                if (found.isPresent()) {
                    return found;
                }
                continue;
            }
            skipPayload(input, type, 0);
        }
    }

    private static void skipPayload(DataInputStream input, int type, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("NBT nesting deeper than " + MAX_DEPTH);
        }
        switch (type) {
            case TAG_BYTE -> skip(input, 1);
            case TAG_SHORT -> skip(input, 2);
            case TAG_INT, TAG_FLOAT -> skip(input, 4);
            case TAG_LONG, TAG_DOUBLE -> skip(input, 8);
            case TAG_BYTE_ARRAY -> skip(input, length(input));
            case TAG_STRING -> skip(input, input.readUnsignedShort());
            case TAG_INT_ARRAY -> skip(input, (long) length(input) * 4);
            case TAG_LONG_ARRAY -> skip(input, (long) length(input) * 8);
            case TAG_LIST -> {
                int elementType = input.readUnsignedByte();
                int size = length(input);
                for (int i = 0; i < size; i++) {
                    skipPayload(input, elementType, depth + 1);
                }
            }
            case TAG_COMPOUND -> {
                int child;
                while ((child = input.readUnsignedByte()) != TAG_END) {
                    skip(input, input.readUnsignedShort());
                    skipPayload(input, child, depth + 1);
                }
            }
            default -> throw new IOException("Unknown NBT tag type " + type);
        }
    }

    private static int length(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0) {
            throw new IOException("Negative NBT array length " + length);
        }
        return length;
    }

    private static void skip(DataInputStream input, long bytes) throws IOException {
        long remaining = bytes;
        while (remaining > 0) {
            long skipped = input.skip(remaining);
            if (skipped <= 0) {
                if (input.read() < 0) {
                    throw new IOException("Unexpected end of NBT data");
                }
                skipped = 1;
            }
            remaining -= skipped;
        }
    }
}
