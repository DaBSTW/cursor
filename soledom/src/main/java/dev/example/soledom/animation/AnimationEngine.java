package dev.example.soledom.animation;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import dev.example.soledom.api.AnimationDefinition;
import dev.example.soledom.api.Vec3;
import dev.example.soledom.api.events.SoledomAnimationEndEvent;
import dev.example.soledom.api.events.SoledomAnimationLoopEvent;
import dev.example.soledom.api.events.SoledomAnimationStartEvent;
import dev.example.soledom.compat.VersionAdapter;
import dev.example.soledom.core.config.SoledomConfig;
import dev.example.soledom.registry.RegisteredModel;
import dev.example.soledom.runtime.DisplayPart;
import dev.example.soledom.runtime.ModelEntityController;
import dev.example.soledom.runtime.ModelState;
import dev.example.soledom.scheduler.SchedulerAdapter;

/**
 * Server-side keyframe animation.
 *
 * <p>Each animating model gets one repeating task on its root entity's scheduler (main thread on Paper, the
 * model's region on Folia), running every {@code animation.tick-rate} ticks. A frame samples every playing
 * animation, resolves per-part channel conflicts by priority, and sends a new display transformation with an
 * interpolation duration equal to the tick rate so the client blends smoothly between frames. Models without
 * animations cost nothing: their task only exists while something plays.</p>
 */
public final class AnimationEngine {

    private static final Comparator<AnimationPlayer> BY_PRIORITY =
            Comparator.comparingInt(AnimationPlayer::priority).reversed();

    private final SchedulerAdapter scheduler;
    private final VersionAdapter adapter;
    private final Supplier<SoledomConfig> config;

    public AnimationEngine(@NotNull SchedulerAdapter scheduler, @NotNull VersionAdapter adapter,
            @NotNull Supplier<SoledomConfig> config) {
        this.scheduler = scheduler;
        this.adapter = adapter;
        this.config = config;
    }

    /**
     * Starts an animation from any thread.
     *
     * @return false if the model has no animation with that name or id
     */
    public boolean play(@NotNull ModelEntityController controller, @NotNull String nameOrId, double speed) {
        RegisteredModel model = controller.registeredModel();
        if (model.animation(nameOrId) == null) {
            return false;
        }
        controller.runOnOwner(() -> startNow(controller, nameOrId, speed));
        return true;
    }

    /** Starts an animation on the current (owning) thread. */
    public void startNow(@NotNull ModelEntityController controller, @NotNull String nameOrId, double speed) {
        RegisteredModel model = controller.registeredModel();
        AnimationDefinition definition = model.animation(nameOrId);
        String name = model.animationName(nameOrId);
        if (definition == null || name == null || controller.state() != ModelState.ACTIVE) {
            return;
        }
        if (!new SoledomAnimationStartEvent(controller, name, definition).callEvent()) {
            return;
        }
        List<AnimationPlayer> players = controller.players();
        for (AnimationPlayer existing : players) {
            if (existing.name().equals(name)) {
                players.remove(existing);
                new SoledomAnimationEndEvent(controller, name, existing.definition(),
                        SoledomAnimationEndEvent.Reason.REPLACED).callEvent();
            }
        }
        players.add(new AnimationPlayer(name, definition, speed));
        players.sort(BY_PRIORITY);
        if (definition.loop()) {
            controller.rememberAnimation(name);
        }
        controller.setPendingReset(false);
        ensureTask(controller);
    }

    /** Stops one animation (owning thread). */
    public void stop(@NotNull ModelEntityController controller, @NotNull String nameOrId) {
        String name = controller.registeredModel().animationName(nameOrId);
        String target = name == null ? nameOrId : name;
        boolean removed = false;
        for (AnimationPlayer player : controller.players()) {
            if (player.name().equals(target)) {
                controller.players().remove(player);
                removed = true;
                new SoledomAnimationEndEvent(controller, player.name(), player.definition(),
                        SoledomAnimationEndEvent.Reason.STOPPED).callEvent();
            }
        }
        if (removed) {
            if (target.equals(controller.rememberedAnimation())) {
                controller.rememberAnimation(null);
            }
            finishIfIdle(controller, true);
        }
    }

    /**
     * Stops every animation (owning thread).
     *
     * @param reset smoothly return parts to their rest pose
     */
    public void stopAll(@NotNull ModelEntityController controller, @NotNull SoledomAnimationEndEvent.Reason reason,
            boolean reset) {
        List<AnimationPlayer> players = List.copyOf(controller.players());
        controller.players().clear();
        for (AnimationPlayer player : players) {
            new SoledomAnimationEndEvent(controller, player.name(), player.definition(), reason).callEvent();
        }
        if (reason == SoledomAnimationEndEvent.Reason.STOPPED) {
            controller.rememberAnimation(null);
        }
        if (reset && controller.state() == ModelState.ACTIVE) {
            applyRestPose(controller);
        }
        cancelTask(controller);
        controller.setPendingReset(false);
    }

    private void ensureTask(ModelEntityController controller) {
        if (controller.animationTask() != null) {
            return;
        }
        int period = config.get().animationTickRate();
        controller.setAnimationTask(scheduler.runAtEntityTimer(controller.getRootEntity(), () -> tick(controller),
                1, period));
    }

    private void cancelTask(ModelEntityController controller) {
        if (controller.animationTask() != null) {
            controller.animationTask().cancel();
            controller.setAnimationTask(null);
        }
    }

    /** One animation frame. Runs on the root's owning thread. */
    void tick(@NotNull ModelEntityController controller) {
        Entity root = controller.getRootEntity();
        if (controller.state() != ModelState.ACTIVE || !root.isValid() || !isChunkLoaded(root.getLocation())) {
            controller.players().clear();
            cancelTask(controller);
            return;
        }
        SoledomConfig cfg = config.get();
        int tickRate = cfg.animationTickRate();

        if (controller.isPendingReset() && controller.players().isEmpty()) {
            applyRestPose(controller);
            controller.setPendingReset(false);
            cancelTask(controller);
            return;
        }

        double seconds = tickRate / 20.0;
        boolean anyResetOnEnd = false;
        for (AnimationPlayer player : controller.players()) {
            AnimationPlayer.Step step = player.advance(seconds);
            if (step == AnimationPlayer.Step.LOOPED) {
                new SoledomAnimationLoopEvent(controller, player.name(), player.definition(), player.loops())
                        .callEvent();
            }
        }

        boolean cull = cfg.animateOnlyNearPlayers() && controller.getModel().options().animateOnlyNearPlayers()
                && root.getLocation().getNearbyPlayers(cfg.maxAnimationDistance()).isEmpty();
        if (!cull) {
            applyFrame(controller, tickRate);
        }

        for (AnimationPlayer player : controller.players()) {
            if (player.isFinished()) {
                controller.players().remove(player);
                anyResetOnEnd |= player.definition().resetOnEnd();
                new SoledomAnimationEndEvent(controller, player.name(), player.definition(),
                        SoledomAnimationEndEvent.Reason.FINISHED).callEvent();
            }
        }
        if (controller.players().isEmpty()) {
            // Keep the task one more frame so the final keyframe is visible before snapping back to rest.
            controller.setPendingReset(anyResetOnEnd);
            if (!anyResetOnEnd) {
                cancelTask(controller);
            }
        }
    }

    private void finishIfIdle(ModelEntityController controller, boolean reset) {
        if (!controller.players().isEmpty()) {
            return;
        }
        if (reset) {
            applyRestPose(controller);
        }
        cancelTask(controller);
    }

    private void applyFrame(ModelEntityController controller, int interpolationTicks) {
        List<AnimationPlayer> players = controller.players();
        // Every part (GROUP nodes included) gets its delta first, then the hierarchy is resolved in one pass so
        // that animating a parent moves its children.
        Map<String, Matrix4f> pose = PoseSolver.solve(controller.getModel(), partId -> {
            TransformSnapshot snapshot = TransformSnapshot.IDENTITY;
            for (Channel channel : Channel.values()) {
                // Players are sorted by priority: the first one driving this channel wins.
                for (AnimationPlayer player : players) {
                    Vec3 value = player.sample(partId, channel);
                    if (value != null) {
                        snapshot = snapshot.with(channel, value);
                        break;
                    }
                }
            }
            return snapshot;
        });
        for (DisplayPart part : controller.parts()) {
            Matrix4f matrix = pose.get(part.id());
            if (matrix != null) {
                part.apply(adapter, matrix, interpolationTicks);
            }
        }
    }

    private void applyRestPose(ModelEntityController controller) {
        int ticks = config.get().animationTickRate();
        for (DisplayPart part : controller.parts()) {
            part.apply(adapter, part.rest(), ticks);
        }
    }

    private static boolean isChunkLoaded(Location location) {
        World world = location.getWorld();
        return world != null && world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }
}
