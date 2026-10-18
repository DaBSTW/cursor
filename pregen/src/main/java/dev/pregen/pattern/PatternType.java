package dev.pregen.pattern;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Generation orders. The id is what users type and what is persisted, so ids must never change. */
public enum PatternType {
    REGION("region"),
    CONCENTRIC("concentric"),
    SPIRAL("spiral"),
    LOOP("loop"),
    /** Chunks listed in {@code plugins/Pregen/csv/<name>.csv}, one {@code x,z} chunk pair per line. */
    CSV("csv"),
    /** Every chunk that already exists on disk; used to load (and so upgrade) an existing world. */
    WORLD("world");

    private static final List<String> IDS = Arrays.stream(values()).map(PatternType::id).toList();

    private final String id;

    PatternType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public boolean requiresArgument() {
        return this == CSV;
    }

    /** Whether the coordinates come from a precomputed list rather than the selection geometry. */
    public boolean isListBased() {
        return this == CSV || this == WORLD;
    }

    public static List<String> ids() {
        return IDS;
    }

    public static Optional<PatternType> fromId(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(type -> type.id.equals(normalized)).findFirst();
    }

    /** Creates a geometric pattern. List-based types must use {@link #fromList(long[])}. */
    public ChunkPattern create(ChunkBounds bounds, int centerChunkX, int centerChunkZ) {
        return switch (this) {
            case REGION -> new RegionPattern(bounds, centerChunkX, centerChunkZ);
            case CONCENTRIC -> new RingWalker(bounds, centerChunkX, centerChunkZ);
            case SPIRAL -> new SpiralPattern(bounds, centerChunkX, centerChunkZ);
            case LOOP -> new LoopPattern(bounds);
            case CSV, WORLD -> throw new IllegalStateException("Pattern " + id + " is list based");
        };
    }

    public static ChunkPattern fromList(long[] chunks) {
        return new ListPattern(chunks);
    }
}
