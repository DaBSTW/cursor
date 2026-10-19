package dev.example.soledom.api;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Handle to one spawned model instance.
 *
 * <p>Methods that touch entities transparently hop to the thread that owns the model (the main thread on
 * Paper, the model's region thread on Folia), so they are safe to call from anywhere.</p>
 */
public interface ModelController {

    /** Stable id of this instance (the UUID of its root entity). */
    @NotNull UUID getUniqueId();

    @NotNull ModelDefinition getModel();

    @NotNull Entity getRootEntity();

    @NotNull Location getLocation();

    /** False once removed, or while its chunk is unloaded. */
    boolean isValid();

    /** Display entity rendering the given part, if currently spawned. */
    @NotNull Optional<Display> getPartEntity(@NotNull String partId);

    @NotNull List<String> getPartIds();

    /** Names of animations currently playing, highest priority first. */
    @NotNull Collection<String> getPlayingAnimations();

    boolean playAnimation(@NotNull String animation);

    boolean playAnimation(@NotNull String animation, double speed);

    void stopAnimation(@NotNull String animation);

    void stopAllAnimations();

    void pauseAnimations();

    void resumeAnimations();

    void setVisible(boolean visible);

    void setVisible(@NotNull Player player, boolean visible);

    boolean isVisible();

    /** Entity this model follows, if it was attached with {@link SoledomAPI#attachModelToEntity}. */
    @Nullable Entity getAttachedEntity();

    void teleport(@NotNull Location location);

    void remove();
}
