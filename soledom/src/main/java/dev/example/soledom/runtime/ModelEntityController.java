package dev.example.soledom.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import dev.example.soledom.animation.AnimationPlayer;
import dev.example.soledom.animation.TransformSnapshot;
import dev.example.soledom.api.ModelController;
import dev.example.soledom.api.ModelDefinition;
import dev.example.soledom.api.PartDefinition;
import dev.example.soledom.api.events.SoledomAnimationEndEvent;
import dev.example.soledom.registry.RegisteredModel;
import dev.example.soledom.scheduler.TaskHandle;

/**
 * Runtime state of one model instance: root entity, spawned parts, animation players and attachment.
 *
 * <p>Threading: mutable state is only modified on the thread owning the root entity. Public API methods hop there
 * through {@link #runOnOwner(Runnable)}. Collections read by commands from other threads are concurrent.</p>
 */
public final class ModelEntityController implements ModelController {

    private final ModelManager manager;
    private final Entity root;
    private final HitboxController hitbox;
    private final Map<String, DisplayPart> parts = new ConcurrentHashMap<>();
    private final List<AnimationPlayer> players = new CopyOnWriteArrayList<>();
    private final Set<UUID> hiddenFor = ConcurrentHashMap.newKeySet();
    private final Set<UUID> shownFor = ConcurrentHashMap.newKeySet();

    private volatile RegisteredModel model;
    private volatile ModelState state = ModelState.DORMANT;
    private volatile @Nullable Entity attachedTo;
    private @Nullable TaskHandle animationTask;
    private @Nullable TaskHandle followTask;
    private boolean pendingReset;

    ModelEntityController(@NotNull ModelManager manager, @NotNull RegisteredModel model, @NotNull Entity root) {
        this.manager = manager;
        this.model = model;
        this.root = root;
        this.hitbox = new HitboxController(root);
    }

    // ---------------------------------------------------------------- lifecycle (owner thread only)

    /** Despawns current parts and spawns fresh ones from the current definition. */
    void buildParts() {
        removeParts();
        ModelDefinition definition = model.definition();
        hitbox.apply(definition.root());
        Location origin = root.getLocation();
        origin.setPitch(0f);
        boolean visible = isVisible();
        for (PartDefinition part : definition.parts()) {
            Transformation rest = TransformSnapshot.IDENTITY.toTransformation(definition, part);
            try {
                Display display = manager.adapter().spawnPart(origin, part, rest, d -> {
                    manager.keys().setString(d, manager.keys().owner, root.getUniqueId().toString());
                    manager.keys().setString(d, manager.keys().partId, part.id());
                    d.setTeleportDuration(Math.min(59, manager.config().animationTickRate()));
                    d.setVisibleByDefault(visible);
                });
                if (display != null) {
                    parts.put(part.id(), new DisplayPart(part, display, rest));
                }
            } catch (RuntimeException e) {
                manager.logger().log(Level.WARNING, "Could not spawn part '" + part.id() + "' of " + definition.id(), e);
            }
        }
        applyPerPlayerVisibility();
        state = ModelState.ACTIVE;
    }

    /** Swaps in a reloaded definition, rebuilding parts and restarting animations that still exist. */
    void rebuild(@NotNull RegisteredModel newModel) {
        List<String> looping = players.stream().filter(p -> p.definition().loop()).map(AnimationPlayer::name).toList();
        manager.engine().stopAll(this, SoledomAnimationEndEvent.Reason.MODEL_REMOVED, false);
        this.model = newModel;
        buildParts();
        for (String name : looping) {
            if (newModel.animation(name) != null) {
                manager.engine().startNow(this, name, 1.0);
            }
        }
        if (players.isEmpty()) {
            manager.startDefaultAnimation(this);
        }
    }

    /** Keeps the root but despawns parts; used when the definition disappeared and auto-cleanup is off. */
    void makeDormant() {
        manager.engine().stopAll(this, SoledomAnimationEndEvent.Reason.MODEL_REMOVED, false);
        removeParts();
        state = ModelState.DORMANT;
    }

    /** Chunk unload: stop ticking. Parts are non-persistent and vanish with the chunk. */
    void unload() {
        manager.engine().stopAll(this, SoledomAnimationEndEvent.Reason.UNLOADED, false);
        stopFollowing();
        parts.clear();
        state = ModelState.REMOVED;
    }

    /** Permanently removes the root and every part. */
    void destroy(boolean removeRoot) {
        manager.engine().stopAll(this, SoledomAnimationEndEvent.Reason.MODEL_REMOVED, false);
        stopFollowing();
        removeParts();
        if (removeRoot && root.isValid()) {
            root.remove();
        }
        state = ModelState.REMOVED;
    }

    void removeParts() {
        for (DisplayPart part : parts.values()) {
            part.remove();
        }
        parts.clear();
    }

    void startFollowing(@NotNull Entity target) {
        stopFollowing();
        attachedTo = target;
        manager.keys().setString(root, manager.keys().attachedTo, target.getUniqueId().toString());
        int period = manager.config().animationTickRate();
        followTask = manager.scheduler().runAtEntityTimer(root, this::followTick, 1, period);
    }

    void stopFollowing() {
        if (followTask != null) {
            followTask.cancel();
            followTask = null;
        }
    }

    private void followTick() {
        Entity target = attachedTo;
        if (target == null || state != ModelState.ACTIVE) {
            stopFollowing();
            return;
        }
        if (!target.isValid()) {
            Location last = target.getLocation();
            boolean chunkLoaded = last.getWorld() != null
                    && last.getWorld().isChunkLoaded(last.getBlockX() >> 4, last.getBlockZ() >> 4);
            if (chunkLoaded || target.isDead()) {
                manager.remove(this, dev.example.soledom.api.events.SoledomModelRemoveEvent.Cause.ATTACHED_ENTITY_GONE);
            } else {
                stopFollowing();
            }
            return;
        }
        Location destination = target.getLocation();
        destination.setPitch(0f);
        if (destination.getWorld() != root.getWorld() || destination.distanceSquared(root.getLocation()) > 1.0e-4
                || Math.abs(destination.getYaw() - root.getLocation().getYaw()) > 0.5f) {
            moveTo(destination);
        }
    }

    private void moveTo(@NotNull Location destination) {
        root.teleportAsync(destination);
        for (DisplayPart part : parts.values()) {
            part.entity().teleportAsync(destination);
        }
    }

    // ---------------------------------------------------------------- threading

    /** Runs {@code task} now if this thread owns the root, otherwise on the owning thread next tick. */
    public void runOnOwner(@NotNull Runnable task) {
        if (manager.scheduler().isOwnedByCurrentThread(root)) {
            task.run();
        } else {
            manager.scheduler().runAtEntity(root, task);
        }
    }

    // ---------------------------------------------------------------- animation state (used by AnimationEngine)

    public @NotNull List<AnimationPlayer> players() {
        return players;
    }

    public @Nullable TaskHandle animationTask() {
        return animationTask;
    }

    public void setAnimationTask(@Nullable TaskHandle task) {
        this.animationTask = task;
    }

    public boolean isPendingReset() {
        return pendingReset;
    }

    public void setPendingReset(boolean pendingReset) {
        this.pendingReset = pendingReset;
    }

    public @NotNull Collection<DisplayPart> parts() {
        return parts.values();
    }

    public @NotNull RegisteredModel registeredModel() {
        return model;
    }

    public @NotNull ModelState state() {
        return state;
    }

    public @NotNull HitboxController hitbox() {
        return hitbox;
    }

    /** Remembers the looping animation so it resumes after a restart. */
    public void rememberAnimation(@Nullable String name) {
        manager.keys().setString(root, manager.keys().animation, name);
    }

    public @Nullable String rememberedAnimation() {
        return manager.keys().getString(root, manager.keys().animation);
    }

    // ---------------------------------------------------------------- ModelController

    @Override
    public @NotNull UUID getUniqueId() {
        return root.getUniqueId();
    }

    @Override
    public @NotNull ModelDefinition getModel() {
        return model.definition();
    }

    @Override
    public @NotNull Entity getRootEntity() {
        return root;
    }

    @Override
    public @NotNull Location getLocation() {
        return root.getLocation();
    }

    @Override
    public boolean isValid() {
        return state != ModelState.REMOVED && root.isValid();
    }

    @Override
    public @NotNull Optional<Display> getPartEntity(@NotNull String partId) {
        DisplayPart part = parts.get(partId);
        return part == null || !part.isValid() ? Optional.empty() : Optional.of(part.entity());
    }

    @Override
    public @NotNull List<String> getPartIds() {
        return model.definition().parts().stream().map(PartDefinition::id).toList();
    }

    @Override
    public @NotNull Collection<String> getPlayingAnimations() {
        List<String> names = new ArrayList<>();
        for (AnimationPlayer player : players) {
            names.add(player.name());
        }
        return names;
    }

    @Override
    public boolean playAnimation(@NotNull String animation) {
        return playAnimation(animation, 1.0);
    }

    @Override
    public boolean playAnimation(@NotNull String animation, double speed) {
        return manager.engine().play(this, animation, speed);
    }

    @Override
    public void stopAnimation(@NotNull String animation) {
        runOnOwner(() -> manager.engine().stop(this, animation));
    }

    @Override
    public void stopAllAnimations() {
        runOnOwner(() -> manager.engine().stopAll(this, SoledomAnimationEndEvent.Reason.STOPPED, true));
    }

    @Override
    public void pauseAnimations() {
        runOnOwner(() -> players.forEach(p -> p.setPaused(true)));
    }

    @Override
    public void resumeAnimations() {
        runOnOwner(() -> players.forEach(p -> p.setPaused(false)));
    }

    @Override
    public void setVisible(boolean visible) {
        runOnOwner(() -> {
            manager.keys().setFlag(root, manager.keys().hidden, !visible);
            root.setVisibleByDefault(visible);
            for (DisplayPart part : parts.values()) {
                part.entity().setVisibleByDefault(visible);
            }
        });
    }

    @Override
    public void setVisible(@NotNull Player player, boolean visible) {
        UUID id = player.getUniqueId();
        if (visible) {
            hiddenFor.remove(id);
            shownFor.add(id);
        } else {
            shownFor.remove(id);
            hiddenFor.add(id);
        }
        runOnOwner(() -> applyVisibility(player, visible));
    }

    @Override
    public boolean isVisible() {
        return !manager.keys().getFlag(root, manager.keys().hidden);
    }

    @Override
    public @Nullable Entity getAttachedEntity() {
        return attachedTo;
    }

    @Override
    public void teleport(@NotNull Location location) {
        runOnOwner(() -> {
            Location destination = location.clone();
            destination.setPitch(0f);
            moveTo(destination);
        });
    }

    @Override
    public void remove() {
        manager.remove(this, dev.example.soledom.api.events.SoledomModelRemoveEvent.Cause.PLUGIN);
    }

    private void applyPerPlayerVisibility() {
        for (UUID id : hiddenFor) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                applyVisibility(player, false);
            }
        }
        for (UUID id : shownFor) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                applyVisibility(player, true);
            }
        }
    }

    private void applyVisibility(Player player, boolean visible) {
        List<Entity> entities = new ArrayList<>(parts.size() + 1);
        entities.add(root);
        parts.values().forEach(p -> entities.add(p.entity()));
        for (Entity entity : entities) {
            if (visible) {
                player.showEntity(manager.plugin(), entity);
            } else {
                player.hideEntity(manager.plugin(), entity);
            }
        }
    }

    /** Ordered snapshot of parts for deterministic iteration (debug output). */
    public @NotNull Map<String, DisplayPart> partsById() {
        Map<String, DisplayPart> ordered = new LinkedHashMap<>();
        for (PartDefinition definition : model.definition().parts()) {
            DisplayPart part = parts.get(definition.id());
            if (part != null) {
                ordered.put(definition.id(), part);
            }
        }
        return ordered;
    }
}
