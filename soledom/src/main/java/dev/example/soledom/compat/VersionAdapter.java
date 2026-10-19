package dev.example.soledom.compat;

import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.kyori.adventure.text.Component;

import dev.example.soledom.api.PartDefinition;
import dev.example.soledom.api.RootDefinition;

/**
 * Single place where version-sensitive server calls live. Everything else in the plugin talks to this interface,
 * so supporting a future Paper change means touching one class.
 */
public interface VersionAdapter {

    @NotNull ServerVersion version();

    @NotNull FeatureFlags features();

    /**
     * Spawns the anchor entity. Falls back to a {@code Marker} when {@code Interaction} entities are unavailable.
     * {@code configure} runs before the entity is added to the world.
     */
    @NotNull Entity spawnRoot(@NotNull Location location, @NotNull RootDefinition root,
            @NotNull Consumer<Entity> configure);

    /**
     * Spawns and fully configures the display entity for {@code part}.
     *
     * @return the entity, or {@code null} if display entities are unavailable or the part cannot be rendered
     */
    @Nullable Display spawnPart(@NotNull Location location, @NotNull PartDefinition part,
            @NotNull Transformation transformation, @NotNull Consumer<Display> configure);

    /** Builds the item an {@code ITEM_DISPLAY} part shows, with custom model data and item model applied. */
    @NotNull ItemStack createItem(@NotNull PartDefinition part);

    /** Sets a new transformation and lets the client interpolate to it over {@code interpolationTicks}. */
    void applyTransformation(@NotNull Display display, @NotNull Transformation transformation, int interpolationTicks);

    /** Sends a server resource pack using the newest API the server offers. */
    void sendResourcePack(@NotNull Player player, @NotNull String url, @Nullable String sha1Hex,
            @Nullable Component prompt, boolean required);
}
