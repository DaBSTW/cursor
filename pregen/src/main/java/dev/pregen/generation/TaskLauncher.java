package dev.pregen.generation;

import dev.pregen.api.GenerationRequest;
import dev.pregen.api.StartOutcome;
import dev.pregen.api.event.PregenTaskStartEvent;
import dev.pregen.config.SettingsHolder;
import dev.pregen.pattern.CsvChunkList;
import dev.pregen.pattern.PatternType;
import dev.pregen.persistence.StoredTask;
import dev.pregen.persistence.TaskStore;
import dev.pregen.selection.Selection;
import dev.pregen.trim.ExistingChunks;
import dev.pregen.trim.StorageDirs;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.World;

/**
 * Runs every check and the (possibly slow) preparation of a task off the main thread, and only hands back a plan once
 * the task is ready to run.
 */
public final class TaskLauncher {

    /** Conservative average size of a freshly generated chunk on disk, including entities and POI data. */
    static final long ESTIMATED_BYTES_PER_CHUNK = 8 * 1024;

    public sealed interface LaunchResult {
    }

    public record Ready(TaskPlan plan, World world) implements LaunchResult {
    }

    public record Rejected(StartResult result) implements LaunchResult {
    }

    private final Server server;
    private final SettingsHolder settings;
    private final TaskStore store;
    private final Predicate<String> trimPending;
    private final Path csvDirectory;
    private final Executor io;
    private final Logger logger;

    /**
     * @param trimPending answers whether a trim is scheduled for a world; may block on disk
     */
    public TaskLauncher(Server server, SettingsHolder settings, TaskStore store, Predicate<String> trimPending,
            Path csvDirectory, Executor io, Logger logger) {
        this.server = server;
        this.settings = settings;
        this.store = store;
        this.trimPending = trimPending;
        this.csvDirectory = csvDirectory;
        this.io = io;
        this.logger = logger;
    }

    public CompletableFuture<LaunchResult> prepareNew(Selection selection, boolean ignoreDiskSpace) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return checkAndPrepare(selection, ignoreDiskSpace);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, io);
    }

    /** Loads a saved task; completes empty when there is none or it can no longer run (world gone, file missing). */
    public CompletableFuture<Optional<Ready>> prepareSaved(String worldName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return loadSaved(worldName);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, io);
    }

    private LaunchResult checkAndPrepare(Selection selection, boolean ignoreDiskSpace) throws IOException {
        World world = server.getWorld(selection.worldName());
        if (world == null) {
            return reject(StartOutcome.WORLD_NOT_FOUND);
        }
        double limit = settings.get().generation().radiusLimit();
        if (limit > 0 && selection.largestRadius() > limit) {
            return reject(StartOutcome.RADIUS_LIMIT_EXCEEDED);
        }
        if (trimPending.test(world.getName())) {
            return reject(StartOutcome.TRIM_PENDING);
        }
        if (store.load(world.getName()).isPresent()) {
            return reject(StartOutcome.SAVED_TASK_EXISTS);
        }
        Optional<long[]> list = loadList(selection, world);
        if (list.isEmpty()) {
            return reject(StartOutcome.MISSING_INPUT_FILE);
        }
        TaskPlan plan = TaskPreparer.prepare(selection, list.get(), 0, 0,
                settings.get().generation().skipExistingChunks());
        if (plan.totalChunks() == 0) {
            return reject(StartOutcome.EMPTY_SELECTION);
        }
        if (!ignoreDiskSpace && settings.get().generation().checkDiskSpace()) {
            long free = Files.getFileStore(world.getWorldFolder().toPath()).getUsableSpace();
            long required = plan.totalChunks() * ESTIMATED_BYTES_PER_CHUNK;
            if (required > free) {
                return new Rejected(
                        new StartResult(StartOutcome.INSUFFICIENT_DISK_SPACE, plan.totalChunks(), free, required));
            }
        }
        PregenTaskStartEvent event = new PregenTaskStartEvent(toRequest(selection), plan.totalChunks());
        server.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return reject(StartOutcome.CANCELLED_BY_LISTENER);
        }
        return new Ready(plan, world);
    }

    private Optional<Ready> loadSaved(String worldName) throws IOException {
        Optional<StoredTask> stored = store.load(worldName);
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        Optional<Selection> selection = stored.get().selection().toSelection();
        World world = server.getWorld(worldName);
        if (selection.isEmpty() || world == null) {
            logger.warning("Saved task cannot be resumed: world=" + worldName + " worldLoaded=" + (world != null));
            return Optional.empty();
        }
        Optional<long[]> list = loadList(selection.get(), world);
        if (list.isEmpty()) {
            return Optional.empty();
        }
        TaskPlan plan = TaskPreparer.prepare(selection.get(), list.get(), stored.get().patternIndex(),
                stored.get().elapsedMillis(), settings.get().generation().skipExistingChunks());
        return Optional.of(new Ready(plan, world));
    }

    private Optional<long[]> loadList(Selection selection, World world) {
        PatternType type = selection.pattern();
        if (!type.isListBased()) {
            return Optional.of(new long[0]);
        }
        try {
            if (type == PatternType.CSV) {
                return Optional.of(CsvChunkList.read(csvDirectory.resolve(selection.patternArgument() + ".csv")));
            }
            return Optional.of(ExistingChunks.scan(StorageDirs.locate(world).region()));
        } catch (IOException e) {
            logger.warning("Cannot read chunk list: world=" + world.getName() + " pattern=" + type.id() + " argument="
                    + selection.patternArgument() + " error=" + e.getMessage());
            return Optional.empty();
        }
    }

    private static Rejected reject(StartOutcome outcome) {
        return new Rejected(StartResult.of(outcome));
    }

    static GenerationRequest toRequest(Selection selection) {
        return new GenerationRequest(selection.worldName(), selection.centerX(), selection.centerZ(),
                selection.radiusX(), selection.effectiveRadiusZ(), selection.shape().id(), selection.pattern().id());
    }
}
