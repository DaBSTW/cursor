package dev.example.soledom.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import dev.example.soledom.api.AnimationDefinition;
import dev.example.soledom.api.ModelController;
import dev.example.soledom.api.ModelDefinition;
import dev.example.soledom.api.SoledomAPI;
import dev.example.soledom.loader.ContentLoader;
import dev.example.soledom.loader.ModelLoader;
import dev.example.soledom.loader.ValidationException;
import dev.example.soledom.core.utils.RuntimeValidator;
import dev.example.soledom.registry.ModelRegistry;
import dev.example.soledom.registry.RegisteredModel;
import dev.example.soledom.runtime.ModelEntityController;
import dev.example.soledom.runtime.ModelKeys;
import dev.example.soledom.runtime.ModelManager;

/** Implementation of the public API on top of the registry and the model manager. */
public final class SoledomApiImpl implements SoledomAPI {

    private final ModelRegistry registry;
    private final ModelManager manager;
    private final ModelKeys keys;

    public SoledomApiImpl(@NotNull ModelRegistry registry, @NotNull ModelManager manager, @NotNull ModelKeys keys) {
        this.registry = registry;
        this.manager = manager;
        this.keys = keys;
    }

    @Override
    public void registerModel(@NotNull ModelDefinition model, @NotNull Map<String, AnimationDefinition> animations) {
        List<String> errors = new ArrayList<>();
        model.parts().forEach(part -> {
            String problem = RuntimeValidator.check(part);
            if (problem != null) {
                errors.add("part '" + part.id() + "': " + problem);
            }
        });
        for (String name : model.animations().keySet()) {
            if (!animations.containsKey(name)) {
                errors.add("animation '" + name + "' is declared but no definition was given");
            }
        }
        if (model.defaultAnimation() != null && !animations.containsKey(model.defaultAnimation())) {
            errors.add("default animation '" + model.defaultAnimation() + "' has no definition");
        }
        Map<String, AnimationDefinition> byId = new java.util.HashMap<>();
        animations.values().forEach(a -> byId.put(a.id(), a));
        ModelDefinition linked = withAnimationIds(model, animations);
        ContentLoader.resolveAnimations(linked, byId, Map.of(), errors);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(model.id() + ": " + String.join("; ", errors));
        }
        registry.registerExternal(new RegisteredModel(linked, animations, true));
        manager.refreshAll();
    }

    @Override
    public @NotNull ModelDefinition parseModel(@NotNull String json) {
        try {
            return new ModelLoader().parse(json, "api", new ArrayList<>());
        } catch (ValidationException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    @Override
    public boolean unregisterModel(@NotNull String modelId) {
        boolean removed = registry.unregisterExternal(modelId);
        if (removed) {
            manager.refreshAll();
        }
        return removed;
    }

    @Override
    public @NotNull Optional<ModelDefinition> getModel(@NotNull String modelId) {
        return registry.get(modelId).map(RegisteredModel::definition);
    }

    @Override
    public @NotNull Collection<ModelDefinition> getModels() {
        return registry.all().stream().map(RegisteredModel::definition).toList();
    }

    @Override
    public @NotNull Map<String, AnimationDefinition> getAnimations(@NotNull String modelId) {
        return registry.get(modelId).map(RegisteredModel::animations).orElse(Map.of());
    }

    @Override
    public @NotNull CompletableFuture<ModelController> spawnModel(@NotNull String modelId,
            @NotNull Location location) {
        return manager.spawn(modelId, location, null);
    }

    @Override
    public @NotNull CompletableFuture<ModelController> attachModelToEntity(@NotNull String modelId,
            @NotNull Entity target) {
        return manager.spawn(modelId, target.getLocation(), target);
    }

    @Override
    public boolean removeModel(@NotNull ModelController model) {
        if (!(model instanceof ModelEntityController controller) || !controller.isValid()) {
            return false;
        }
        controller.remove();
        return true;
    }

    @Override
    public boolean removeModel(@NotNull Entity entity) {
        return manager.find(entity).map(this::removeModel).orElse(false);
    }

    @Override
    public boolean playAnimation(@NotNull ModelController model, @NotNull String animation) {
        return model.playAnimation(animation);
    }

    @Override
    public void stopAnimation(@NotNull ModelController model) {
        model.stopAllAnimations();
    }

    @Override
    public void stopAnimation(@NotNull ModelController model, @NotNull String animation) {
        model.stopAnimation(animation);
    }

    @Override
    public boolean isModelEntity(@NotNull Entity entity) {
        return keys.isRoot(entity) || keys.isPart(entity);
    }

    @Override
    public @NotNull Optional<String> getModelId(@NotNull Entity entity) {
        if (keys.isRoot(entity)) {
            return Optional.ofNullable(keys.modelId(entity));
        }
        return manager.find(entity).map(c -> c.getModel().id());
    }

    @Override
    public @NotNull Optional<ModelController> getController(@NotNull Entity entity) {
        return manager.find(entity).map(ModelController.class::cast);
    }

    @Override
    public @NotNull Collection<ModelController> getActiveModels() {
        return List.copyOf(manager.all());
    }

    @Override
    public void setModelVisibility(@NotNull ModelController model, boolean visible) {
        model.setVisible(visible);
    }

    @Override
    public void setModelVisibility(@NotNull ModelController model, @NotNull Player player, boolean visible) {
        model.setVisible(player, visible);
    }

    /** Points each declared animation reference at the id of the supplied definition, for validation. */
    private static ModelDefinition withAnimationIds(ModelDefinition model, Map<String, AnimationDefinition> animations) {
        Map<String, String> refs = new java.util.LinkedHashMap<>();
        animations.forEach((name, def) -> refs.put(name, def.id()));
        return new ModelDefinition(model.id(), model.displayName(), model.root(), model.parts(), model.offset(),
                model.rotation(), model.scale(), refs, model.defaultAnimation(), model.tags(), model.metadata(),
                model.options());
    }
}
