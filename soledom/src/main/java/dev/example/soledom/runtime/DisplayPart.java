package dev.example.soledom.runtime;

import java.util.Objects;

import org.bukkit.entity.Display;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import dev.example.soledom.api.PartDefinition;
import dev.example.soledom.compat.VersionAdapter;

/** A spawned part: its display entity plus the last matrix sent, to avoid redundant packets. */
public final class DisplayPart {

    private final PartDefinition definition;
    private final Display entity;
    private final Matrix4f rest;
    private @Nullable Matrix4f lastApplied;

    public DisplayPart(@NotNull PartDefinition definition, @NotNull Display entity, @NotNull Matrix4fc rest) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.entity = Objects.requireNonNull(entity, "entity");
        this.rest = new Matrix4f(rest);
        this.lastApplied = this.rest;
    }

    public @NotNull String id() {
        return definition.id();
    }

    public @NotNull PartDefinition definition() {
        return definition;
    }

    public @NotNull Display entity() {
        return entity;
    }

    /** Model-space matrix of the rest pose (no animation). */
    public @NotNull Matrix4fc rest() {
        return rest;
    }

    public boolean isValid() {
        return entity.isValid();
    }

    /** Sends {@code matrix} unless it equals the one already shown. */
    public boolean apply(@NotNull VersionAdapter adapter, @NotNull Matrix4fc matrix, int interpolationTicks) {
        if (!entity.isValid() || matrix.equals(lastApplied)) {
            return false;
        }
        Matrix4f copy = new Matrix4f(matrix);
        adapter.applyTransformation(entity, copy, interpolationTicks);
        lastApplied = copy;
        return true;
    }

    public void remove() {
        if (entity.isValid()) {
            entity.remove();
        }
    }
}
