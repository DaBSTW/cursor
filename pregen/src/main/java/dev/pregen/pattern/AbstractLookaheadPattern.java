package dev.pregen.pattern;

import java.util.NoSuchElementException;

/** Base for patterns that walk candidates and drop those outside the bounds. */
abstract class AbstractLookaheadPattern implements ChunkPattern {

    private boolean hasBuffered;
    private long buffered;
    private long position;

    /** Writes the next in-bounds coordinate into the buffer; returns false when the walk is over. */
    protected abstract boolean advance();

    protected final void offer(long packed) {
        buffered = packed;
        hasBuffered = true;
    }

    @Override
    public final boolean hasNext() {
        return hasBuffered || advance();
    }

    @Override
    public final long next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        hasBuffered = false;
        position++;
        return buffered;
    }

    @Override
    public final long position() {
        return position;
    }
}
