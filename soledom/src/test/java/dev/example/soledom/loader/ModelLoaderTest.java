package dev.example.soledom.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.example.soledom.TestResources;
import dev.example.soledom.api.ModelDefinition;
import dev.example.soledom.api.PartDefinition;
import dev.example.soledom.api.PartType;
import dev.example.soledom.api.RootType;
import dev.example.soledom.api.Vec3;

class ModelLoaderTest {

    private final ModelLoader loader = new ModelLoader();

    static String resource(String path) throws IOException {
        return TestResources.read(path);
    }

    @Test
    void loadsBundledExampleModel() throws Exception {
        List<String> warnings = new ArrayList<>();
        ModelDefinition model = loader.parse(resource("examples/models/example_golem.json"), "example", warnings);

        assertEquals("soledom:example_golem", model.id());
        assertEquals(RootType.INTERACTION, model.root().type());
        assertEquals(1.8f, model.root().height());
        assertEquals(3, model.parts().size());
        PartDefinition body = model.part("body").orElseThrow();
        assertEquals(PartType.ITEM_DISPLAY, body.type());
        assertEquals("minecraft:stick", body.material());
        assertEquals(1000, body.customModelData());
        assertEquals(Vec3.uniform(1.4), body.scale());
        assertEquals(Vec3.uniform(0.6), model.part("head").orElseThrow().scale());
        assertEquals("idle", model.defaultAnimation());
        assertEquals(2, model.animations().size());
        assertTrue(warnings.isEmpty(), "unexpected warnings: " + warnings);
    }

    @Test
    void defaultsMissingNamespaceAndModelReference() throws Exception {
        ModelDefinition model = loader.parse("""
                {"id": "Lamp", "parts": [{"id": "glass", "type": "item_display", "material": "glass_pane",
                  "custom_model_data": 7}]}
                """, "lamp.json", new ArrayList<>());
        assertEquals("soledom:lamp", model.id());
        PartDefinition part = model.parts().get(0);
        assertEquals("minecraft:glass_pane", part.material());
        assertEquals("soledom:item/lamp/glass", part.model());
        assertEquals(RootType.INTERACTION, model.root().type());
    }

    @Test
    void reportsEveryProblemWithItsPath() {
        ValidationException e = assertThrows(ValidationException.class, () -> loader.parse("""
                {
                  "id": "Bad Id!",
                  "root": {"type": "INTERACTION", "width": -1},
                  "parts": [
                    {"id": "a", "type": "ITEM_DISPLAY"},
                    {"id": "a", "type": "BLOCK_DISPLAY", "block": "minecraft:stone", "scale": [1, 2]},
                    {"id": "c", "type": "SPHERE"},
                    {"id": "d", "type": "ITEM_DISPLAY", "material": "minecraft:stick", "custom_model_data": -5}
                  ],
                  "default_animation": "walk"
                }
                """, "bad.json", new ArrayList<>()));
        List<String> errors = e.getErrors();
        assertHas(errors, "id: 'Bad Id!' is not a valid id");
        assertHas(errors, "root.width: must be greater than 0");
        assertHas(errors, "parts[0].material: ITEM_DISPLAY parts need a material");
        assertHas(errors, "parts[1].id: duplicate part id 'a'");
        assertHas(errors, "parts[1].scale: expected a number or an array of 3 numbers");
        assertHas(errors, "parts[2].type: 'SPHERE' is not one of [ITEM_DISPLAY, BLOCK_DISPLAY, TEXT_DISPLAY, GROUP]");
        assertHas(errors, "parts[3].custom_model_data: must be between 0 and 16777216");
        assertHas(errors, "default_animation: 'walk' is not declared");
        assertEquals("bad.json", e.getSource());
    }

    @Test
    void rejectsMalformedJsonAndEmptyParts() {
        ValidationException malformed = assertThrows(ValidationException.class,
                () -> loader.parse("{\"id\": \"x\", ", "broken.json", new ArrayList<>()));
        assertTrue(malformed.getErrors().get(0).startsWith("malformed JSON"));

        ValidationException empty = assertThrows(ValidationException.class,
                () -> loader.parse("{\"id\": \"x\", \"parts\": []}", "empty.json", new ArrayList<>()));
        assertHas(empty.getErrors(), "parts: a model needs at least one part");

        ValidationException future = assertThrows(ValidationException.class, () -> loader.parse(
                "{\"format_version\": 99, \"id\": \"x\", \"parts\": [{\"id\":\"t\",\"type\":\"TEXT_DISPLAY\",\"text\":\"hi\"}]}",
                "future.json", new ArrayList<>()));
        assertHas(future.getErrors(), "format_version: unsupported version 99");
    }

    @Test
    void warnsAboutUnknownFields() throws Exception {
        List<String> warnings = new ArrayList<>();
        loader.parse("""
                {"id": "x", "colour": "red",
                 "parts": [{"id": "t", "type": "TEXT_DISPLAY", "text": "hi", "positon": [0, 1, 0]}]}
                """, "typo.json", warnings);
        assertHas(warnings, "typo.json: colour: unknown field");
        assertHas(warnings, "typo.json: parts[0].positon: unknown field");
    }

    @Test
    void validatesPartHierarchy() throws Exception {
        ModelDefinition ok = loader.parse("""
                {"id": "arm", "parts": [
                  {"id": "hand", "type": "TEXT_DISPLAY", "text": "x", "parent": "shoulder"},
                  {"id": "shoulder", "type": "GROUP", "position": [0, 1, 0]}]}
                """, "arm.json", new ArrayList<>());
        assertEquals("shoulder", ok.part("hand").orElseThrow().parent());
        assertEquals(PartType.GROUP, ok.part("shoulder").orElseThrow().type());

        ValidationException e = assertThrows(ValidationException.class, () -> loader.parse("""
                {"id": "bad", "parts": [
                  {"id": "a", "type": "GROUP", "parent": "b"},
                  {"id": "b", "type": "GROUP", "parent": "a"},
                  {"id": "c", "type": "GROUP", "parent": "ghost"}]}
                """, "bad.json", new ArrayList<>()));
        assertHas(e.getErrors(), "parts[0].parent: parent chain of 'a' loops back on itself");
        assertHas(e.getErrors(), "parts[2].parent: no part with id 'ghost'");
    }

    static void assertHas(List<String> messages, String fragment) {
        assertTrue(messages.stream().anyMatch(m -> m.contains(fragment)),
                () -> "expected a message containing \"" + fragment + "\" in " + messages);
    }
}
