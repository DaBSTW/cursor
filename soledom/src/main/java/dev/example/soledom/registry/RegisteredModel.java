package dev.example.soledom.registry;

import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import dev.example.soledom.api.AnimationDefinition;
import dev.example.soledom.api.ModelDefinition;

/**
 * A model together with its resolved animations.
 *
 * @param definition the model
 * @param animations local animation name to definition
 * @param external   registered through the API (survives reloads) rather than loaded from a file
 */
public record RegisteredModel(@NotNull ModelDefinition definition, @NotNull Map<String, AnimationDefinition> animations,
        boolean external) {

    public RegisteredModel {
        Objects.requireNonNull(definition, "definition");
        animations = Map.copyOf(animations);
    }

    public @NotNull String id() {
        return definition.id();
    }

    /** Looks up an animation by local name, falling back to its namespaced id. */
    public @Nullable AnimationDefinition animation(@NotNull String nameOrId) {
        AnimationDefinition byName = animations.get(nameOrId);
        if (byName != null) {
            return byName;
        }
        for (AnimationDefinition definition : animations.values()) {
            if (definition.id().equals(nameOrId)) {
                return definition;
            }
        }
        return null;
    }

    /** Local name for an animation given by local name or id. */
    public @Nullable String animationName(@NotNull String nameOrId) {
        if (animations.containsKey(nameOrId)) {
            return nameOrId;
        }
        for (Map.Entry<String, AnimationDefinition> entry : animations.entrySet()) {
            if (entry.getValue().id().equals(nameOrId)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
