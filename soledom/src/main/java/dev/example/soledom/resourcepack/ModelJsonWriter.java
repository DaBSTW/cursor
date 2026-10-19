package dev.example.soledom.resourcepack;

import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Writes the JSON documents of the generated pack: item override files for both the legacy (&lt;1.21.4) and the
 * modern (1.21.4+) client formats, plus placeholder models.
 */
public final class ModelJsonWriter {

    /** Texture used by generated placeholder models. */
    public static final String PLACEHOLDER_TEXTURE = "soledom:item/placeholder";

    /** Vanilla items whose base model is {@code item/handheld} rather than {@code item/generated}. */
    private static final Set<String> HANDHELD = Set.of("stick", "blaze_rod", "breeze_rod", "bone", "bamboo",
            "debug_stick", "mace", "trident");
    private static final List<String> HANDHELD_SUFFIXES = List.of("_sword", "_axe", "_pickaxe", "_shovel", "_hoe");

    /** One custom-model-data binding on an item. */
    public record Binding(int customModelData, @NotNull String model) {
    }

    private ModelJsonWriter() {
    }

    /**
     * Legacy {@code assets/minecraft/models/item/<item>.json}: vanilla look plus {@code overrides} keyed by
     * {@code custom_model_data}. Ignored by 1.21.4+ clients.
     *
     * @param baseModel explicit vanilla base model, or {@code null} to derive one from the item name
     * @param isBlock   whether the item is a block item (its base model lives under {@code block/})
     */
    public static @NotNull JsonObject legacyItemModel(@NotNull String itemPath, @Nullable String baseModel,
            boolean isBlock, @NotNull List<Binding> bindings) {
        JsonObject json = new JsonObject();
        if (baseModel != null) {
            json.addProperty("parent", baseModel);
        } else if (isBlock) {
            json.addProperty("parent", "minecraft:block/" + itemPath);
        } else {
            json.addProperty("parent", isHandheld(itemPath) ? "minecraft:item/handheld" : "minecraft:item/generated");
            JsonObject textures = new JsonObject();
            textures.addProperty("layer0", "minecraft:item/" + itemPath);
            json.add("textures", textures);
        }
        JsonArray overrides = new JsonArray();
        bindings.stream().sorted((a, b) -> Integer.compare(a.customModelData(), b.customModelData())).forEach(b -> {
            JsonObject predicate = new JsonObject();
            predicate.addProperty("custom_model_data", b.customModelData());
            JsonObject override = new JsonObject();
            override.add("predicate", predicate);
            override.addProperty("model", b.model());
            overrides.add(override);
        });
        json.add("overrides", overrides);
        return json;
    }

    /**
     * Modern {@code assets/minecraft/items/<item>.json} item definition: a {@code range_dispatch} over custom model
     * data (float index 0) with the vanilla model as fallback.
     */
    public static @NotNull JsonObject modernItemDefinition(@NotNull String itemPath, @NotNull List<Binding> bindings) {
        JsonObject dispatch = new JsonObject();
        dispatch.addProperty("type", "minecraft:range_dispatch");
        dispatch.addProperty("property", "minecraft:custom_model_data");
        dispatch.addProperty("index", 0);
        JsonArray entries = new JsonArray();
        bindings.stream().sorted((a, b) -> Integer.compare(a.customModelData(), b.customModelData())).forEach(b -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("threshold", b.customModelData());
            entry.add("model", plainModel(b.model()));
            entries.add(entry);
        });
        dispatch.add("entries", entries);
        dispatch.add("fallback", plainModel("minecraft:item/" + itemPath));
        JsonObject json = new JsonObject();
        json.add("model", dispatch);
        return json;
    }

    /** {@code assets/<ns>/items/<path>.json} for parts using the {@code item_model} component (1.21.4+). */
    public static @NotNull JsonObject itemModelDefinition(@NotNull String model) {
        JsonObject json = new JsonObject();
        json.add("model", plainModel(model));
        return json;
    }

    /** A textured cube used when the pack overlay has no model file for a reference. */
    public static @NotNull JsonObject placeholderModel() {
        JsonObject textures = new JsonObject();
        textures.addProperty("particle", PLACEHOLDER_TEXTURE);
        textures.addProperty("all", PLACEHOLDER_TEXTURE);

        JsonObject faces = new JsonObject();
        for (String face : List.of("north", "east", "south", "west", "up", "down")) {
            JsonObject f = new JsonObject();
            f.add("uv", numbers(0, 0, 16, 16));
            f.addProperty("texture", "#all");
            faces.add(face, f);
        }
        JsonObject element = new JsonObject();
        element.add("from", numbers(4, 4, 4));
        element.add("to", numbers(12, 12, 12));
        element.add("faces", faces);
        JsonArray elements = new JsonArray();
        elements.add(element);

        JsonObject fixed = new JsonObject();
        fixed.add("scale", numbers(1, 1, 1));
        JsonObject display = new JsonObject();
        display.add("fixed", fixed);
        display.add("head", fixed.deepCopy());

        JsonObject json = new JsonObject();
        json.addProperty("credit", "Generated by Soledom (placeholder)");
        json.add("textures", textures);
        json.add("elements", elements);
        json.add("display", display);
        return json;
    }

    static boolean isHandheld(@NotNull String itemPath) {
        if (HANDHELD.contains(itemPath)) {
            return true;
        }
        for (String suffix : HANDHELD_SUFFIXES) {
            if (itemPath.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private static JsonObject plainModel(String model) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:model");
        json.addProperty("model", model);
        return json;
    }

    private static JsonArray numbers(double... values) {
        JsonArray array = new JsonArray();
        for (double v : values) {
            array.add(v);
        }
        return array;
    }
}
