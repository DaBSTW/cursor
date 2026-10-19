package dev.example.soledom.resourcepack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

import dev.example.soledom.api.ModelDefinition;
import dev.example.soledom.api.PartDefinition;
import dev.example.soledom.api.PartType;
import dev.example.soledom.core.utils.NamespacedIds;

/**
 * Builds the content of the resource pack in memory from model definitions plus an optional overlay folder.
 *
 * <p>Pure Java, no server calls: safe for async threads and unit tests. Output layout:</p>
 * <pre>
 * pack.mcmeta, pack.png
 * assets/minecraft/models/item/&lt;item&gt;.json   legacy overrides (clients &lt; 1.21.4)
 * assets/minecraft/items/&lt;item&gt;.json         range_dispatch definitions (clients 1.21.4+)
 * assets/&lt;ns&gt;/items/&lt;path&gt;.json            item_model component targets (1.21.4+)
 * assets/&lt;ns&gt;/models/&lt;path&gt;.json           models from the overlay, or generated placeholders
 * assets/soledom/textures/item/placeholder.png
 * </pre>
 * Everything in the overlay folder ({@code plugins/Soledom/pack/}) is copied verbatim first; generated item
 * files replace overlay files at the same path (with a warning).
 */
public final class PackGenerator {

    /** Refuse absurd overlays instead of exhausting memory: 256 MiB. */
    public static final long MAX_OVERLAY_BYTES = 256L * 1024 * 1024;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String PLACEHOLDER_PNG = "assets/soledom/textures/item/placeholder.png";

    private final Predicate<String> isBlockItem;

    /** @param isBlockItem tells whether an item id ({@code minecraft:stone}) is a block item */
    public PackGenerator(@NotNull Predicate<String> isBlockItem) {
        this.isBlockItem = isBlockItem;
    }

    public @NotNull GeneratedPack generate(@NotNull Collection<ModelDefinition> models, @NotNull PackMeta meta,
            @Nullable Path overlayDir) throws IOException {
        return generate(models, meta, overlayDir, Map.of());
    }

    /**
     * @param generatedAssets files produced while loading (Blockbench item models and textures); files in the
     *                        overlay folder take precedence over them so they can be hand-tuned
     */
    public @NotNull GeneratedPack generate(@NotNull Collection<ModelDefinition> models, @NotNull PackMeta meta,
            @Nullable Path overlayDir, @NotNull Map<String, byte[]> generatedAssets) throws IOException {
        SortedMap<String, byte[]> entries = new TreeMap<>(generatedAssets);
        List<String> warnings = new ArrayList<>();
        readOverlay(overlayDir, entries, warnings);

        if (entries.containsKey("pack.mcmeta")) {
            warnings.add("pack/pack.mcmeta is ignored; formats come from config.yml (resource-pack.*)");
        }
        entries.put("pack.mcmeta", json(meta.toJson()));
        entries.putIfAbsent("pack.png", PlaceholderTexture.packIcon());

        Map<String, Map<Integer, String>> bindingsByItem = new TreeMap<>();
        Map<String, String> legacyBase = new LinkedHashMap<>();
        Map<String, String> itemModels = new TreeMap<>();
        Set<String> modelRefs = new LinkedHashSet<>();
        for (ModelDefinition model : models) {
            for (PartDefinition part : model.parts()) {
                if (part.type() != PartType.ITEM_DISPLAY || part.material() == null) {
                    continue;
                }
                String where = model.id() + "/" + part.id();
                if (part.customModelData() != null && part.model() != null) {
                    Map<Integer, String> bindings = bindingsByItem.computeIfAbsent(part.material(), k -> new TreeMap<>());
                    String previous = bindings.putIfAbsent(part.customModelData(), part.model());
                    if (previous != null && !previous.equals(part.model())) {
                        warnings.add(where + ": custom_model_data " + part.customModelData() + " on "
                                + part.material() + " is already bound to " + previous + "; keeping that one");
                    }
                    if (part.legacyBaseModel() != null) {
                        legacyBase.putIfAbsent(part.material(), part.legacyBaseModel());
                    }
                    modelRefs.add(part.model());
                }
                if (part.itemModel() != null && part.model() != null) {
                    String previous = itemModels.putIfAbsent(part.itemModel(), part.model());
                    if (previous != null && !previous.equals(part.model())) {
                        warnings.add(where + ": item_model " + part.itemModel() + " already points to " + previous);
                    }
                    modelRefs.add(part.model());
                }
            }
        }

        int placeholders = 0;
        for (String ref : modelRefs) {
            String path = "assets/" + NamespacedIds.namespace(ref) + "/models/" + NamespacedIds.path(ref) + ".json";
            if (!entries.containsKey(path)) {
                entries.put(path, json(ModelJsonWriter.placeholderModel()));
                placeholders++;
            }
        }
        if (placeholders > 0) {
            entries.putIfAbsent(PLACEHOLDER_PNG, PlaceholderTexture.checkerboard());
            warnings.add(placeholders + " model(s) had no file in pack/assets and use a placeholder cube");
        }

        for (Map.Entry<String, Map<Integer, String>> item : bindingsByItem.entrySet()) {
            String itemId = item.getKey();
            String ns = NamespacedIds.namespace(itemId);
            String itemPath = NamespacedIds.path(itemId);
            List<ModelJsonWriter.Binding> bindings = item.getValue().entrySet().stream()
                    .map(e -> new ModelJsonWriter.Binding(e.getKey(), e.getValue())).toList();
            putGenerated(entries, warnings, "assets/" + ns + "/models/item/" + itemPath + ".json",
                    ModelJsonWriter.legacyItemModel(itemPath, legacyBase.get(itemId), isBlockItem.test(itemId),
                            bindings));
            putGenerated(entries, warnings, "assets/" + ns + "/items/" + itemPath + ".json",
                    ModelJsonWriter.modernItemDefinition(itemPath, bindings));
        }
        for (Map.Entry<String, String> itemModel : itemModels.entrySet()) {
            String key = itemModel.getKey();
            String path = "assets/" + NamespacedIds.namespace(key) + "/items/" + NamespacedIds.path(key) + ".json";
            entries.putIfAbsent(path, json(ModelJsonWriter.itemModelDefinition(itemModel.getValue())));
        }
        return new GeneratedPack(entries, warnings, bindingsByItem.size(), modelRefs.size(), placeholders);
    }

    private static void putGenerated(Map<String, byte[]> entries, List<String> warnings, String path,
            JsonElement content) {
        if (entries.containsKey(path)) {
            warnings.add("pack/" + path + " is replaced by the generated version");
        }
        entries.put(path, json(content));
    }

    private static void readOverlay(@Nullable Path dir, Map<String, byte[]> entries, List<String> warnings)
            throws IOException {
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        long total = 0;
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path file : walk.filter(Files::isRegularFile).sorted().toList()) {
                String relative = dir.relativize(file).toString().replace('\\', '/');
                if (relative.startsWith(".") || relative.contains("/.")) {
                    continue;
                }
                total += Files.size(file);
                if (total > MAX_OVERLAY_BYTES) {
                    throw new IOException("pack overlay is larger than " + (MAX_OVERLAY_BYTES >> 20) + " MiB");
                }
                entries.put(relative, Files.readAllBytes(file));
            }
        }
    }

    private static byte[] json(JsonElement element) {
        return (GSON.toJson(element) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * In-memory pack.
     *
     * @param entries      ZIP path to content, sorted for reproducible output
     * @param warnings     non-fatal issues to show the operator
     * @param items        vanilla items that received overrides
     * @param models       distinct model references
     * @param placeholders models generated as placeholders
     */
    public record GeneratedPack(@NotNull SortedMap<String, byte[]> entries, @NotNull List<String> warnings, int items,
            int models, int placeholders) {

        public GeneratedPack {
            entries = new TreeMap<>(entries);
            warnings = List.copyOf(warnings);
        }
    }
}
