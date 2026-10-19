package dev.example.soledom.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable description of a model: its anchor, visual parts and animation bindings.
 *
 * @param id                 namespaced id, e.g. {@code soledom:example_golem}
 * @param displayName        MiniMessage display name
 * @param root               anchor/hitbox configuration
 * @param parts              visual parts, in spawn order
 * @param offset             translation applied to every part
 * @param rotation           rotation in degrees applied to every part (around the model origin)
 * @param scale              scale applied to every part
 * @param animations         local animation name to reference (animation id or {@code animations/x.json} path)
 * @param defaultAnimation   animation started automatically on spawn, or {@code null}
 * @param tags               free-form tags
 * @param metadata           free-form string metadata
 * @param options            behavioural flags
 */
public record ModelDefinition(
        @NotNull String id,
        @NotNull String displayName,
        @NotNull RootDefinition root,
        @NotNull List<PartDefinition> parts,
        @NotNull Vec3 offset,
        @NotNull Vec3 rotation,
        @NotNull Vec3 scale,
        @NotNull Map<String, String> animations,
        @Nullable String defaultAnimation,
        @NotNull Set<String> tags,
        @NotNull Map<String, String> metadata,
        @NotNull ModelOptions options) {

    public ModelDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(offset, "offset");
        Objects.requireNonNull(rotation, "rotation");
        Objects.requireNonNull(scale, "scale");
        Objects.requireNonNull(options, "options");
        parts = List.copyOf(parts);
        animations = Map.copyOf(animations);
        tags = Set.copyOf(tags);
        metadata = Map.copyOf(metadata);
    }

    public @NotNull Optional<PartDefinition> part(@NotNull String partId) {
        for (PartDefinition part : parts) {
            if (part.id().equals(partId)) {
                return Optional.of(part);
            }
        }
        return Optional.empty();
    }

    /** Path component of the id ({@code example_golem} for {@code soledom:example_golem}). */
    public @NotNull String path() {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }

    public @NotNull String namespace() {
        int colon = id.indexOf(':');
        return colon < 0 ? "soledom" : id.substring(0, colon);
    }
}
