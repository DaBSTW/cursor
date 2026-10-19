package dev.example.soledom.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Public entry point for other plugins.
 *
 * <p>Obtain it with {@link Soledom#api()} or through Bukkit's {@code ServicesManager}. All methods are safe to
 * call from any thread; entity work is scheduled on the owning thread and returned through futures.</p>
 */
public interface SoledomAPI {

    /**
     * Registers a model programmatically. Programmatic models survive {@code /soledom reload} and override a
     * file model with the same id.
     *
     * @param animations local animation name to definition
     * @throws IllegalArgumentException if the definition is invalid
     */
    void registerModel(@NotNull ModelDefinition model, @NotNull Map<String, AnimationDefinition> animations);

    /** Parses and validates a model JSON document using the same rules as files in {@code models/}. */
    @NotNull ModelDefinition parseModel(@NotNull String json);

    boolean unregisterModel(@NotNull String modelId);

    @NotNull Optional<ModelDefinition> getModel(@NotNull String modelId);

    @NotNull Collection<ModelDefinition> getModels();

    @NotNull Map<String, AnimationDefinition> getAnimations(@NotNull String modelId);

    /** Spawns a model; completes exceptionally if the id is unknown or the chunk limit is reached. */
    @NotNull CompletableFuture<ModelController> spawnModel(@NotNull String modelId, @NotNull Location location);

    /** Spawns a model that follows {@code target} until either is removed. */
    @NotNull CompletableFuture<ModelController> attachModelToEntity(@NotNull String modelId, @NotNull Entity target);

    boolean removeModel(@NotNull ModelController model);

    /** Removes the model that owns {@code entity} (root or part). */
    boolean removeModel(@NotNull Entity entity);

    boolean playAnimation(@NotNull ModelController model, @NotNull String animation);

    void stopAnimation(@NotNull ModelController model);

    void stopAnimation(@NotNull ModelController model, @NotNull String animation);

    /** True for roots and parts of Soledom models. */
    boolean isModelEntity(@NotNull Entity entity);

    @NotNull Optional<String> getModelId(@NotNull Entity entity);

    @NotNull Optional<ModelController> getController(@NotNull Entity entity);

    @NotNull Collection<ModelController> getActiveModels();

    void setModelVisibility(@NotNull ModelController model, boolean visible);

    void setModelVisibility(@NotNull ModelController model, @NotNull Player player, boolean visible);
}
