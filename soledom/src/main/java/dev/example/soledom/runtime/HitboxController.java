package dev.example.soledom.runtime;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import dev.example.soledom.api.RootDefinition;

/**
 * Click hitbox of a model, backed by the root {@link Interaction} entity.
 *
 * <p>LIMITATION: when the root is a {@code Marker} (by choice or because Interaction entities are unavailable)
 * there is no hitbox and {@link #isPresent()} is false; such models cannot be clicked.</p>
 */
public final class HitboxController {

    private final @Nullable Interaction interaction;

    public HitboxController(@NotNull Entity root) {
        this.interaction = root instanceof Interaction i ? i : null;
    }

    public boolean isPresent() {
        return interaction != null && interaction.isValid();
    }

    public float width() {
        return interaction == null ? 0f : interaction.getInteractionWidth();
    }

    public float height() {
        return interaction == null ? 0f : interaction.getInteractionHeight();
    }

    /** Applies size/responsiveness from a (possibly reloaded) definition. */
    public void apply(@NotNull RootDefinition definition) {
        if (interaction == null || !interaction.isValid()) {
            return;
        }
        interaction.setInteractionWidth(definition.width());
        interaction.setInteractionHeight(definition.height());
        interaction.setResponsive(definition.responsive());
    }
}
