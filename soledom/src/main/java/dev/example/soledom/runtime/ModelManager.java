package dev.example.soledom.runtime;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import dev.example.soledom.animation.AnimationEngine;
import dev.example.soledom.api.ModelController;
import dev.example.soledom.api.ModelDefinition;
import dev.example.soledom.api.events.SoledomAnimationEndEvent;
import dev.example.soledom.api.events.SoledomModelRemoveEvent;
import dev.example.soledom.api.events.SoledomModelSpawnEvent;
import dev.example.soledom.compat.VersionAdapter;
import dev.example.soledom.core.config.SoledomConfig;
import dev.example.soledom.registry.ModelRegistry;
import dev.example.soledom.registry.RegisteredModel;
import dev.example.soledom.scheduler.SchedulerAdapter;

/**
 * Owns every live {@link ModelEntityController}: spawning, removal, chunk load/unload, reload refresh and
 * shutdown cleanup.
 *
 * <p>Persistence model: only the root entity is saved with the world (when the model persists). Its
 * {@code PersistentDataContainer} stores the model id; parts are spawned non-persistent and rebuilt from the
 * current definition whenever the root loads. That way a restart or reload can never leave stale or orphaned
 * display entities behind, and model edits apply to already-placed instances.</p>
 */
public final class ModelManager {

    private final JavaPlugin plugin;
    private final ModelRegistry registry;
    private final VersionAdapter adapter;
    private final SchedulerAdapter scheduler;
    private final ModelKeys keys;
    private final Supplier<SoledomConfig> config;
    private final Map<UUID, ModelEntityController> controllers = new ConcurrentHashMap<>();
    private AnimationEngine engine;

    public ModelManager(@NotNull JavaPlugin plugin, @NotNull ModelRegistry registry, @NotNull VersionAdapter adapter,
            @NotNull SchedulerAdapter scheduler, @NotNull ModelKeys keys, @NotNull Supplier<SoledomConfig> config) {
        this.plugin = plugin;
        this.registry = registry;
        this.adapter = adapter;
        this.scheduler = scheduler;
        this.keys = keys;
        this.config = config;
    }

    public void setEngine(@NotNull AnimationEngine engine) {
        this.engine = engine;
    }

    // ---------------------------------------------------------------- spawning

    /** Spawns a model from any thread. */
    public @NotNull CompletableFuture<ModelController> spawn(@NotNull String modelId, @NotNull Location location,
            @Nullable Entity attachTo) {
        CompletableFuture<ModelController> future = new CompletableFuture<>();
        RegisteredModel model = registry.get(modelId).orElse(null);
        if (model == null) {
            future.completeExceptionally(new SpawnException(SpawnException.Reason.UNKNOWN_MODEL,
                    "Unknown model '" + modelId + "'"));
            return future;
        }
        Location target = location.clone();
        Runnable work = () -> {
            try {
                future.complete(spawnNow(model, target, attachTo));
            } catch (SpawnException e) {
                future.completeExceptionally(e);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to spawn " + modelId, e);
                future.completeExceptionally(new SpawnException(SpawnException.Reason.FAILED, String.valueOf(
                        e.getMessage())));
            }
        };
        World world = target.getWorld();
        if (world == null) {
            future.completeExceptionally(new SpawnException(SpawnException.Reason.FAILED, "Location has no world"));
            return future;
        }
        if (!world.isChunkLoaded(target.getBlockX() >> 4, target.getBlockZ() >> 4)) {
            // Since 1.21.9 there are no always-loaded spawn chunks, so console/API spawns often target unloaded
            // chunks. Load it asynchronously, then spawn on the thread that owns it.
            world.getChunkAtAsync(target).whenComplete((chunk, error) -> {
                if (error != null || chunk == null) {
                    future.completeExceptionally(new SpawnException(SpawnException.Reason.CHUNK_NOT_LOADED,
                            "Chunk could not be loaded"));
                } else {
                    runAt(target, work);
                }
            });
            return future;
        }
        runAt(target, work);
        return future;
    }

    private void runAt(Location location, Runnable work) {
        if (scheduler.isOwnedByCurrentThread(location)) {
            work.run();
        } else {
            scheduler.runAtLocation(location, work);
        }
    }

    /** Spawns a model on the current thread, which must own {@code location}. */
    public @NotNull ModelEntityController spawnNow(@NotNull RegisteredModel model, @NotNull Location location,
            @Nullable Entity attachTo) throws SpawnException {
        World world = location.getWorld();
        if (world == null || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            throw new SpawnException(SpawnException.Reason.CHUNK_NOT_LOADED, "Chunk is not loaded");
        }
        int limit = config.get().maxModelsPerChunk();
        if (limit > 0 && countRoots(location.getChunk()) >= limit) {
            throw new SpawnException(SpawnException.Reason.CHUNK_LIMIT, "Chunk already holds " + limit + " models");
        }
        ModelDefinition definition = model.definition();
        if (!new SoledomModelSpawnEvent(definition, location, attachTo).callEvent()) {
            throw new SpawnException(SpawnException.Reason.CANCELLED, "Spawn cancelled by another plugin");
        }
        Location spawnAt = location.clone();
        spawnAt.setPitch(0f);
        boolean persist = config.get().persistModels() && definition.options().persist();
        boolean visible = definition.options().visibleByDefault();
        Entity root = adapter.spawnRoot(spawnAt, definition.root(), entity -> {
            entity.setPersistent(persist);
            entity.setSilent(true);
            entity.setInvulnerable(true);
            entity.setVisibleByDefault(visible);
            keys.setString(entity, keys.modelId, definition.id());
            keys.setFlag(entity, keys.hidden, !visible);
        });
        ModelEntityController controller = new ModelEntityController(this, model, root);
        controllers.put(root.getUniqueId(), controller);
        controller.buildParts();
        if (attachTo != null) {
            controller.startFollowing(attachTo);
        }
        startDefaultAnimation(controller);
        debug("Spawned " + definition.id() + " (" + root.getUniqueId() + ")");
        return controller;
    }

    void startDefaultAnimation(@NotNull ModelEntityController controller) {
        String remembered = controller.rememberedAnimation();
        RegisteredModel model = controller.registeredModel();
        String name = remembered != null && model.animation(remembered) != null
                ? remembered
                : model.definition().defaultAnimation();
        if (name != null && model.animation(name) != null) {
            engine.startNow(controller, name, 1.0);
        }
    }

    // ---------------------------------------------------------------- removal

    /** Removes a model permanently, from any thread. */
    public void remove(@NotNull ModelEntityController controller, @NotNull SoledomModelRemoveEvent.Cause cause) {
        controller.runOnOwner(() -> {
            if (controller.state() == ModelState.REMOVED) {
                return;
            }
            new SoledomModelRemoveEvent(controller, cause).callEvent();
            controllers.remove(controller.getUniqueId());
            controller.destroy(true);
            debug("Removed " + controller.getModel().id() + " (" + cause + ")");
        });
    }

    /** The root was removed by something else (kill command, another plugin): clean up the parts. */
    public void handleRootRemoved(@NotNull Entity root) {
        ModelEntityController controller = controllers.remove(root.getUniqueId());
        if (controller == null || controller.state() == ModelState.REMOVED) {
            return;
        }
        new SoledomModelRemoveEvent(controller, SoledomModelRemoveEvent.Cause.ROOT_REMOVED).callEvent();
        // Defer by a tick: removing other entities while the server is mid-removal of this one is not safe.
        scheduler.runAtLocation(root.getLocation(), () -> controller.destroy(false));
    }

    // ---------------------------------------------------------------- chunk lifecycle

    /** Called from {@code EntitiesLoadEvent} and the startup scan, on the thread owning those entities. */
    public void handleEntitiesLoaded(@NotNull List<Entity> entities) {
        for (Entity entity : entities) {
            if (keys.isRoot(entity)) {
                scheduler.runAtEntity(entity, () -> rehydrate(entity));
            } else if (keys.isPart(entity)) {
                UUID owner = keys.owner(entity);
                if (owner == null || !controllers.containsKey(owner)) {
                    // Parts are never meant to be saved; one surviving means an unclean shutdown.
                    scheduler.runAtEntity(entity, entity::remove);
                }
            }
        }
    }

    public void handleEntitiesUnloaded(@NotNull List<Entity> entities) {
        for (Entity entity : entities) {
            if (keys.isRoot(entity)) {
                ModelEntityController controller = controllers.remove(entity.getUniqueId());
                if (controller != null) {
                    controller.unload();
                }
            }
        }
    }

    private void rehydrate(@NotNull Entity root) {
        if (!root.isValid() || controllers.containsKey(root.getUniqueId())) {
            return;
        }
        String modelId = keys.modelId(root);
        RegisteredModel model = modelId == null ? null : registry.get(modelId).orElse(null);
        if (model == null) {
            if (config.get().autoCleanup()) {
                plugin.getLogger().info("Removing model instance " + root.getUniqueId() + ": model '" + modelId
                        + "' no longer exists (models.auto-cleanup)");
                root.remove();
            } else {
                // Keep a dormant controller so a later reload that brings the model back can revive it.
                ModelEntityController dormant = new ModelEntityController(this, placeholder(modelId), root);
                controllers.put(root.getUniqueId(), dormant);
            }
            return;
        }
        ModelEntityController controller = new ModelEntityController(this, model, root);
        controllers.put(root.getUniqueId(), controller);
        controller.buildParts();
        UUID attached = keys.attachedTo(root);
        if (attached != null) {
            Entity target = findNearby(root, attached);
            if (target != null) {
                controller.startFollowing(target);
            } else if (config.get().autoCleanup()) {
                remove(controller, SoledomModelRemoveEvent.Cause.ATTACHED_ENTITY_GONE);
                return;
            } else {
                keys.setString(root, keys.attachedTo, null);
            }
        }
        startDefaultAnimation(controller);
        debug("Rehydrated " + model.id() + " (" + root.getUniqueId() + ")");
    }

    /** Re-applies definitions after {@code /soledom reload}. */
    public void refreshAll() {
        for (ModelEntityController controller : controllers.values()) {
            controller.runOnOwner(() -> {
                if (!controller.getRootEntity().isValid()) {
                    return;
                }
                String id = keys.modelId(controller.getRootEntity());
                RegisteredModel model = id == null ? null : registry.get(id).orElse(null);
                if (model != null) {
                    controller.rebuild(model);
                } else if (config.get().autoCleanup()) {
                    remove(controller, SoledomModelRemoveEvent.Cause.CLEANUP);
                } else if (controller.state() == ModelState.ACTIVE) {
                    controller.makeDormant();
                }
            });
        }
    }

    /** Rehydrates models in chunks that were already loaded before the plugin enabled. */
    public void scanLoadedWorlds() {
        for (World world : Bukkit.getWorlds()) {
            Chunk[] chunks;
            try {
                chunks = world.getLoadedChunks();
            } catch (UnsupportedOperationException e) {
                // Folia: no global chunk view. EntitiesLoadEvent covers chunks as regions load them.
                continue;
            }
            for (Chunk chunk : chunks) {
                Location anchor = new Location(world, (chunk.getX() << 4) + 8, 64, (chunk.getZ() << 4) + 8);
                Runnable scan = () -> handleEntitiesLoaded(List.of(chunk.getEntities()));
                if (scheduler.isOwnedByCurrentThread(anchor)) {
                    scan.run();
                } else {
                    scheduler.runAtLocation(anchor, scan);
                }
            }
        }
    }

    /**
     * Plugin disable. Stops animations and, on the main thread, removes parts (and non-persistent roots) right
     * away. On Folia entities cannot be touched from here; non-persistent entities are dropped by the server
     * anyway and stray parts are cleaned up on the next load.
     */
    public void shutdown() {
        boolean canTouchWorld = Bukkit.isPrimaryThread() && !adapter.features().hasFolia();
        for (ModelEntityController controller : controllers.values()) {
            try {
                if (canTouchWorld) {
                    engine.stopAll(controller, SoledomAnimationEndEvent.Reason.SHUTDOWN, false);
                    controller.stopFollowing();
                    controller.removeParts();
                    Entity root = controller.getRootEntity();
                    if (!root.isPersistent() && root.isValid()) {
                        root.remove();
                    }
                } else {
                    controller.players().clear();
                }
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Error while unloading model " + controller.getUniqueId(), e);
            }
        }
        controllers.clear();
    }

    // ---------------------------------------------------------------- lookup

    public @NotNull Optional<ModelEntityController> find(@NotNull Entity entity) {
        ModelEntityController direct = controllers.get(entity.getUniqueId());
        if (direct != null) {
            return Optional.of(direct);
        }
        if (keys.isPart(entity)) {
            UUID owner = keys.owner(entity);
            return owner == null ? Optional.empty() : Optional.ofNullable(controllers.get(owner));
        }
        return Optional.empty();
    }

    public @NotNull Optional<ModelEntityController> get(@NotNull UUID id) {
        return Optional.ofNullable(controllers.get(id));
    }

    /** Nearest active model within {@code radius}, optionally of one model id. Must run on the owning thread. */
    public @NotNull Optional<ModelEntityController> nearest(@NotNull Location location, double radius,
            @Nullable String modelId) {
        World world = location.getWorld();
        if (world == null) {
            return Optional.empty();
        }
        ModelEntityController best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : world.getNearbyEntities(location, radius, radius, radius, keys::isRoot)) {
            ModelEntityController controller = controllers.get(entity.getUniqueId());
            if (controller == null || (modelId != null && !modelId.equals(controller.getModel().id()))) {
                continue;
            }
            double distance = entity.getLocation().distanceSquared(location);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = controller;
            }
        }
        return Optional.ofNullable(best);
    }

    public @NotNull Collection<ModelEntityController> all() {
        return controllers.values();
    }

    public int activeCount() {
        return controllers.size();
    }

    private int countRoots(Chunk chunk) {
        int count = 0;
        for (Entity entity : chunk.getEntities()) {
            if (keys.isRoot(entity)) {
                count++;
            }
        }
        return count;
    }

    private static @Nullable Entity findNearby(Entity root, UUID target) {
        for (Entity entity : root.getNearbyEntities(16, 16, 16)) {
            if (entity.getUniqueId().equals(target)) {
                return entity;
            }
        }
        return null;
    }

    private static RegisteredModel placeholder(@Nullable String modelId) {
        String id = modelId == null ? "soledom:missing" : modelId;
        ModelDefinition definition = new ModelDefinition(id, id, dev.example.soledom.api.RootDefinition.DEFAULT,
                List.of(), dev.example.soledom.api.Vec3.ZERO, dev.example.soledom.api.Vec3.ZERO,
                dev.example.soledom.api.Vec3.ONE, Map.of(), null, java.util.Set.of(), Map.of(),
                dev.example.soledom.api.ModelOptions.DEFAULT);
        return new RegisteredModel(definition, Map.of(), false);
    }

    private void debug(String message) {
        if (config.get().debug()) {
            plugin.getLogger().info("[debug] " + message);
        }
    }

    // ---------------------------------------------------------------- accessors for controllers

    @NotNull JavaPlugin plugin() {
        return plugin;
    }

    @NotNull VersionAdapter adapter() {
        return adapter;
    }

    @NotNull SchedulerAdapter scheduler() {
        return scheduler;
    }

    @NotNull ModelKeys keys() {
        return keys;
    }

    @NotNull SoledomConfig config() {
        return config.get();
    }

    @NotNull AnimationEngine engine() {
        return engine;
    }

    @NotNull Logger logger() {
        return plugin.getLogger();
    }
}
