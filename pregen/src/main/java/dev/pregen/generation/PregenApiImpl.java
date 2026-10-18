package dev.pregen.generation;

import dev.pregen.api.GenerationRequest;
import dev.pregen.api.PregenApi;
import dev.pregen.api.StartOutcome;
import dev.pregen.api.TaskProgress;
import dev.pregen.api.TaskState;
import dev.pregen.geometry.ShapeType;
import dev.pregen.pattern.PatternType;
import dev.pregen.selection.Selection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Adapts the internal services to the public {@link PregenApi} contract. */
public final class PregenApiImpl implements PregenApi {

    private final GenerationService generation;

    public PregenApiImpl(GenerationService generation) {
        this.generation = generation;
    }

    @Override
    public boolean isRunning(String worldName) {
        return generation.task(worldName).map(task -> task.state() == TaskState.RUNNING).orElse(false);
    }

    @Override
    public Optional<TaskProgress> progress(String worldName) {
        return generation.task(worldName).map(GenerationTask::progress);
    }

    @Override
    public List<TaskProgress> tasks() {
        return generation.tasks().stream().map(GenerationTask::progress).toList();
    }

    @Override
    public CompletableFuture<StartOutcome> start(GenerationRequest request) {
        ShapeType shape = ShapeType.fromId(request.shape())
                .orElseThrow(() -> new IllegalArgumentException("Unknown shape: " + request.shape()));
        PatternType pattern = PatternType.fromId(request.pattern())
                .orElseThrow(() -> new IllegalArgumentException("Unknown pattern: " + request.pattern()));
        if (pattern.requiresArgument()) {
            throw new IllegalArgumentException("Pattern '" + pattern.id() + "' is not supported through the API");
        }
        Selection selection = new Selection(request.worldName(), request.centerX(), request.centerZ(),
                request.radiusX(), request.radiusZ(), shape, pattern, "");
        return generation.start(selection, false).thenApply(StartResult::outcome);
    }

    @Override
    public boolean pause(String worldName) {
        return generation.pause(worldName);
    }

    @Override
    public CompletableFuture<Boolean> resume(String worldName) {
        return generation.resume(worldName);
    }

    @Override
    public CompletableFuture<Boolean> cancel(String worldName) {
        return generation.cancel(worldName);
    }
}
