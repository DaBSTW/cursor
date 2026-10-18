package dev.pregen.generation;

import dev.pregen.api.StartOutcome;

/** Outcome of a start request plus the figures needed to explain it to the user. */
public record StartResult(StartOutcome outcome, long totalChunks, long freeBytes, long requiredBytes) {

    public static StartResult of(StartOutcome outcome) {
        return new StartResult(outcome, 0, 0, 0);
    }
}
