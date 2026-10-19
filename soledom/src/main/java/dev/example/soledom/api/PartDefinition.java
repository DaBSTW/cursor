package dev.example.soledom.api;

import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One visual piece of a model, rendered by its own display entity.
 *
 * @param id                unique id inside the model ({@code [a-z0-9_-]+})
 * @param type              display entity kind
 * @param material          item id for {@link PartType#ITEM_DISPLAY} (e.g. {@code minecraft:stick})
 * @param customModelData   custom model data applied to the item, or {@code null}
 * @param model             resource-pack model reference ({@code namespace:path}) bound to the custom model data
 * @param itemModel         optional {@code minecraft:item_model} component value (1.21.4+ clients)
 * @param legacyBaseModel   vanilla base model used in pre-1.21.4 override files, or {@code null} to guess
 * @param block             block state for {@link PartType#BLOCK_DISPLAY} (e.g. {@code minecraft:stone})
 * @param text              MiniMessage text for {@link PartType#TEXT_DISPLAY}
 * @param textStyle         text rendering options
 * @param displayTransform  item display transform name ({@code FIXED}, {@code HEAD}, ...)
 * @param billboard         billboard mode ({@code FIXED}, {@code VERTICAL}, {@code HORIZONTAL}, {@code CENTER})
 * @param position          translation relative to the model origin, in blocks
 * @param rotation          rotation in degrees, applied X then Y then Z (extrinsic, same as Blockbench)
 * @param scale             per-axis scale
 * @param glow              glowing outline
 * @param glowColor         RGB glow colour override, or {@code null}
 * @param brightness        fixed light level, or {@code null} to use world light
 * @param viewRange         view range multiplier
 * @param shadowRadius      shadow radius in blocks
 * @param shadowStrength    shadow opacity
 */
public record PartDefinition(
        @NotNull String id,
        @NotNull PartType type,
        @Nullable String material,
        @Nullable Integer customModelData,
        @Nullable String model,
        @Nullable String itemModel,
        @Nullable String legacyBaseModel,
        @Nullable String block,
        @Nullable String text,
        @NotNull TextStyle textStyle,
        @NotNull String displayTransform,
        @NotNull String billboard,
        @NotNull Vec3 position,
        @NotNull Vec3 rotation,
        @NotNull Vec3 scale,
        boolean glow,
        @Nullable Integer glowColor,
        @Nullable Brightness brightness,
        float viewRange,
        float shadowRadius,
        float shadowStrength) {

    public PartDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(textStyle, "textStyle");
        Objects.requireNonNull(displayTransform, "displayTransform");
        Objects.requireNonNull(billboard, "billboard");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(rotation, "rotation");
        Objects.requireNonNull(scale, "scale");
    }

    /** Convenience factory for the most common case: an item display with custom model data. */
    public static @NotNull PartDefinition item(@NotNull String id, @NotNull String material,
            @Nullable Integer customModelData, @Nullable String model) {
        return new PartDefinition(id, PartType.ITEM_DISPLAY, material, customModelData, model, null, null, null,
                null, TextStyle.DEFAULT, "FIXED", "FIXED", Vec3.ZERO, Vec3.ZERO, Vec3.ONE, false, null, null,
                1.0f, 0f, 1f);
    }

    /** Fixed block/sky light levels (0-15). */
    public record Brightness(int block, int sky) {
    }
}
