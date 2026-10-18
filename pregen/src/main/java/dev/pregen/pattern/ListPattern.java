package dev.pregen.pattern;

import java.util.NoSuchElementException;

/** Explicit list of chunks (CSV import or chunks already on disk). The selection shape does not filter it. */
final class ListPattern implements ChunkPattern {

    private final long[] chunks;
    private int index;

    ListPattern(long[] chunks) {
        this.chunks = chunks.clone();
    }

    @Override
    public boolean hasNext() {
        return index < chunks.length;
    }

    @Override
    public long next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        return chunks[index++];
    }

    @Override
    public long position() {
        return index;
    }

    @Override
    public boolean appliesShapeFilter() {
        return false;
    }
}
