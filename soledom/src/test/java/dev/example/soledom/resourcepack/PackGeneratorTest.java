package dev.example.soledom.resourcepack;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.example.soledom.TestResources;
import dev.example.soledom.api.ModelDefinition;
import dev.example.soledom.loader.ModelLoader;

class PackGeneratorTest {

    @TempDir
    Path temp;

    private static ModelDefinition golem() throws Exception {
        return new ModelLoader().parse(TestResources.read("examples/models/example_golem.json"), "golem",
                new ArrayList<>());
    }

    private static JsonObject json(PackGenerator.GeneratedPack pack, String path) {
        byte[] bytes = pack.entries().get(path);
        assertTrue(bytes != null, "missing entry " + path + " in " + pack.entries().keySet());
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void generatesLegacyAndModernItemFilesWithPlaceholders() throws Exception {
        PackGenerator.GeneratedPack pack = new PackGenerator(id -> false)
                .generate(List.of(golem()), new PackMeta("test", 34, 34, 97), null);

        JsonObject meta = json(pack, "pack.mcmeta").getAsJsonObject("pack");
        assertEquals(97, meta.get("max_format").getAsInt());
        assertTrue(pack.entries().containsKey("pack.png"));

        JsonObject legacy = json(pack, "assets/minecraft/models/item/stick.json");
        assertEquals("minecraft:item/handheld", legacy.get("parent").getAsString());
        JsonArray overrides = legacy.getAsJsonArray("overrides");
        assertEquals(1000, overrides.get(0).getAsJsonObject().getAsJsonObject("predicate")
                .get("custom_model_data").getAsInt());
        assertEquals("soledom:item/example_golem/body",
                overrides.get(0).getAsJsonObject().get("model").getAsString());

        JsonObject modern = json(pack, "assets/minecraft/items/stick.json").getAsJsonObject("model");
        assertEquals("minecraft:range_dispatch", modern.get("type").getAsString());
        assertEquals("minecraft:custom_model_data", modern.get("property").getAsString());
        assertEquals(1000, modern.getAsJsonArray("entries").get(0).getAsJsonObject().get("threshold").getAsInt());
        assertEquals("minecraft:item/stick", modern.getAsJsonObject("fallback").get("model").getAsString());

        assertTrue(pack.entries().containsKey("assets/soledom/models/item/example_golem/body.json"));
        assertTrue(pack.entries().containsKey("assets/soledom/textures/item/placeholder.png"));
        assertEquals(1, pack.placeholders());
        assertEquals(1, pack.items());
    }

    @Test
    void overlayModelsReplacePlaceholdersAndMcmetaIsAlwaysGenerated() throws Exception {
        Path overlay = temp.resolve("pack");
        Path model = overlay.resolve("assets/soledom/models/item/example_golem/body.json");
        Files.createDirectories(model.getParent());
        Files.writeString(model, "{\"parent\":\"minecraft:item/generated\"}");
        Files.writeString(overlay.resolve("pack.mcmeta"), "{}");

        PackGenerator.GeneratedPack pack = new PackGenerator(id -> false)
                .generate(List.of(golem()), new PackMeta("test", 34, 34, 97), overlay);

        assertEquals("minecraft:item/generated",
                json(pack, "assets/soledom/models/item/example_golem/body.json").get("parent").getAsString());
        assertFalse(pack.entries().containsKey("assets/soledom/textures/item/placeholder.png"));
        assertEquals(0, pack.placeholders());
        assertTrue(json(pack, "pack.mcmeta").has("pack"));
        assertTrue(pack.warnings().stream().anyMatch(w -> w.contains("pack.mcmeta is ignored")));
    }

    @Test
    void blockItemsUseBlockParentAndConflictsWarn() throws Exception {
        ModelDefinition a = new ModelLoader().parse("""
                {"id": "a", "parts": [{"id": "p", "type": "ITEM_DISPLAY", "material": "minecraft:stone",
                  "custom_model_data": 5, "model": "soledom:item/a"}]}
                """, "a", new ArrayList<>());
        ModelDefinition b = new ModelLoader().parse("""
                {"id": "b", "parts": [{"id": "p", "type": "ITEM_DISPLAY", "material": "minecraft:stone",
                  "custom_model_data": 5, "model": "soledom:item/b"}]}
                """, "b", new ArrayList<>());
        PackGenerator.GeneratedPack pack = new PackGenerator(Set.of("minecraft:stone")::contains)
                .generate(List.of(a, b), new PackMeta("t", 34, 34, 97), null);

        assertEquals("minecraft:block/stone",
                json(pack, "assets/minecraft/models/item/stone.json").get("parent").getAsString());
        assertTrue(pack.warnings().stream().anyMatch(w -> w.contains("already bound to soledom:item/a")));
    }

    @Test
    void exportIsReproducibleAndHashed() throws Exception {
        PackGenerator.GeneratedPack pack = new PackGenerator(id -> false)
                .generate(List.of(golem()), new PackMeta("test", 34, 34, 97), null);
        Path zip = temp.resolve("export/SoledomPack.zip");
        PackExporter exporter = new PackExporter();

        PackExporter.PackResult first = exporter.export(pack, zip);
        byte[] firstBytes = Files.readAllBytes(zip);
        PackExporter.PackResult second = exporter.export(pack, zip);

        assertEquals(first.sha1(), second.sha1());
        assertArrayEquals(firstBytes, Files.readAllBytes(zip));
        assertTrue(first.sha1().matches("[0-9a-f]{40}"));
        assertEquals(first.sha1(), PackExporter.readExportedSha1(zip));
        try (ZipFile file = new ZipFile(zip.toFile())) {
            Set<String> names = new HashSet<>();
            file.stream().forEach(e -> names.add(e.getName()));
            assertEquals(pack.entries().keySet(), names);
        }
        try (var listing = Files.list(zip.getParent())) {
            assertTrue(listing.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")));
        }
    }
}
