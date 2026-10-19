package dev.example.soledom.registry;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.NotNull;

import dev.example.soledom.api.AnimationDefinition;

/**
 * Thread-safe registry of models.
 *
 * <p>Readers see an immutable snapshot published through a volatile field, so lookups never lock. File models are
 * replaced wholesale on reload; API models live in a separate map and always take precedence.</p>
 */
public final class ModelRegistry {

    private final Map<String, RegisteredModel> external = new ConcurrentHashMap<>();
    private volatile Map<String, RegisteredModel> fileModels = Map.of();
    private volatile Map<String, AnimationDefinition> fileAnimations = Map.of();
    private volatile Map<String, RegisteredModel> snapshot = Map.of();
    private volatile Map<String, byte[]> packAssets = Map.of();

    /** Atomically replaces every file-backed model and animation, plus pack files generated for them. */
    public synchronized void replaceFileContent(@NotNull Map<String, RegisteredModel> models,
            @NotNull Map<String, AnimationDefinition> animations, @NotNull Map<String, byte[]> generatedPackAssets) {
        this.fileModels = Map.copyOf(models);
        this.fileAnimations = Map.copyOf(animations);
        this.packAssets = Map.copyOf(generatedPackAssets);
        rebuild();
    }

    /** Resource pack files generated while loading (Blockbench item models and textures), by ZIP path. */
    public @NotNull Map<String, byte[]> packAssets() {
        return packAssets;
    }

    public synchronized void registerExternal(@NotNull RegisteredModel model) {
        external.put(model.id(), new RegisteredModel(model.definition(), model.animations(), true));
        rebuild();
    }

    public synchronized boolean unregisterExternal(@NotNull String id) {
        boolean removed = external.remove(id) != null;
        if (removed) {
            rebuild();
        }
        return removed;
    }

    public @NotNull Optional<RegisteredModel> get(@NotNull String id) {
        return Optional.ofNullable(snapshot.get(id));
    }

    public boolean contains(@NotNull String id) {
        return snapshot.containsKey(id);
    }

    public @NotNull Collection<RegisteredModel> all() {
        return snapshot.values();
    }

    public @NotNull List<String> ids() {
        return snapshot.keySet().stream().sorted().toList();
    }

    public @NotNull Map<String, AnimationDefinition> animations() {
        return fileAnimations;
    }

    public int size() {
        return snapshot.size();
    }

    private void rebuild() {
        Map<String, RegisteredModel> merged = new LinkedHashMap<>(fileModels);
        merged.putAll(external);
        snapshot = Map.copyOf(merged);
    }
}
