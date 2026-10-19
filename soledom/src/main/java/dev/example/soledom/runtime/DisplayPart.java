package dev.example.soledom.runtime;

import java.util.Objects;

import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import dev.example.soledom.api.PartDefinition;
import dev.example.soledom.compat.VersionAdapter;

/** A spawned part: its display entity plus the last transformation sent, to avoid redundant packets. */
public final class DisplayPart {

    private final PartDefinition definition;
    private final Display entity;
    private final Transformation rest;
    private @Nullable Transformation lastApplied;

    public DisplayPart(@NotNull PartDefinition definition, @NotNull Display entity, @NotNull Transformation rest) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.entity = Objects.requireNonNull(entity, "entity");
        this.rest = Objects.requireNonNull(rest, "rest");
        this.lastApplied = rest;
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

    /** Transformation of the rest pose (no animation). */
    public @NotNull Transformation rest() {
        return rest;
    }

    public boolean isValid() {
        return entity.isValid();
    }

    /** Sends {@code transformation} unless it equals the one already shown. */
    public boolean apply(@NotNull VersionAdapter adapter, @NotNull Transformation transformation,
            int interpolationTicks) {
        if (!entity.isValid() || transformation.equals(lastApplied)) {
            return false;
        }
        adapter.applyTransformation(entity, transformation, interpolationTicks);
        lastApplied = transformation;
        return true;
    }

    public void remove() {
        if (entity.isValid()) {
            entity.remove();
        }
    }
}
