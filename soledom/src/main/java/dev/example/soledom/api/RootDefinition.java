package dev.example.soledom.api;

import java.util.Objects;

import org.jetbrains.annotations.NotNull;

/**
 * Anchor/hitbox configuration of a model.
 *
 * @param type       entity used as anchor
 * @param width      hitbox width in blocks (only meaningful for {@link RootType#INTERACTION})
 * @param height     hitbox height in blocks (only meaningful for {@link RootType#INTERACTION})
 * @param responsive whether clicks on the hitbox play the arm-swing response on the client
 */
public record RootDefinition(@NotNull RootType type, float width, float height, boolean responsive) {

    public static final RootDefinition DEFAULT = new RootDefinition(RootType.INTERACTION, 1.0f, 1.0f, true);

    public RootDefinition {
        Objects.requireNonNull(type, "type");
    }

    public boolean hasHitbox() {
        return type == RootType.INTERACTION;
    }
}
