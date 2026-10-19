package dev.example.soledom.blockbench;

import static dev.example.soledom.TestResources.assertHas;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.example.soledom.TestResources;
import dev.example.soledom.api.AnimationDefinition;
import dev.example.soledom.api.ModelDefinition;
import dev.example.soledom.api.PartDefinition;
import dev.example.soledom.api.PartType;
import dev.example.soledom.api.Vec3;
import dev.example.soledom.loader.AnimationLoader;
import dev.example.soledom.loader.ModelLoader;

class BbModelConverterTest {

    private static final ImportSettings SETTINGS = new ImportSettings(true, "soledom", "minecraft:paper", 20000);

    static JsonObject robot() throws Exception {
        return JsonParser.parseString(TestResources.read("examples/blockbench/example_robot.bbmodel"))
                .getAsJsonObject();
    }

    private static BbModelConverter.Conversion convert(JsonObject project) {
        return new BbModelConverter().convert(project, "example_robot.bbmodel", SETTINGS, 20000);
    }

    private static JsonObject packJson(BbModelConverter.Conversion conversion, String path) {
        byte[] bytes = conversion.packFiles().get(path);
        assertNotNull(bytes, "missing " + path + " in " + conversion.packFiles().keySet());
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void convertsBoneTreeIntoPartHierarchy() throws Exception {
        BbModelConverter.Conversion conversion = convert(robot());
        ModelDefinition model = new ModelLoader().parse(conversion.model(), "robot", new ArrayList<>());

        assertEquals("soledom:example_robot", model.id());
        assertEquals(List.of("root", "body", "head", "antenna", "arm_left", "arm_right", "legs", "leg_left",
                "leg_right"), model.parts().stream().map(PartDefinition::id).toList());

        PartDefinition head = model.part("head").orElseThrow();
        assertEquals("body", head.parent());
        assertEquals(PartType.ITEM_DISPLAY, head.type());
        assertEquals(new Vec3(0, 10 / 16.0, 0), head.position());
        assertEquals("soledom:item/example_robot/head", head.model());
        assertEquals("minecraft:paper", head.material());

        PartDefinition legs = model.part("legs").orElseThrow();
        assertEquals(PartType.GROUP, legs.type());
        assertEquals("legs", model.part("leg_left").orElseThrow().parent());
        assertEquals(new Vec3(2 / 16.0, 0, 0), model.part("leg_left").orElseThrow().position());

        // One custom model data value per bone with cubes, in outliner order (GROUP bones use none).
        assertEquals(20000, model.part("root").orElseThrow().customModelData());
        assertEquals(20001, model.part("body").orElseThrow().customModelData());
        assertEquals(8, conversion.customModelDataUsed());

        // Hitbox from the cube bounds: 14 px wide (arm to arm), 29 px tall (antenna tip).
        assertEquals(0.875f, model.root().width());
        assertEquals(1.8125f, model.root().height());
        assertEquals("idle", model.defaultAnimation());
        assertEquals("blockbench:example_robot.bbmodel", model.metadata().get("source"));
    }

    @Test
    void generatesItemModelsCentredOnTheBonePivot() throws Exception {
        BbModelConverter.Conversion conversion = convert(robot());

        JsonObject body = packJson(conversion, "assets/soledom/models/item/example_robot/body.json");
        JsonObject torso = body.getAsJsonArray("elements").get(0).getAsJsonObject();
        // Torso [-4,9,-2.5]..[4,19,2.5] around pivot (0,9,0) -> model space centred on 8.
        assertEquals(List.of(4.0, 8.0, 5.5), doubles(torso.getAsJsonArray("from")));
        assertEquals(List.of(12.0, 18.0, 10.5), doubles(torso.getAsJsonArray("to")));
        assertEquals(List.of(0.0, 180.0, 0.0),
                doubles(body.getAsJsonObject("display").getAsJsonObject("fixed").getAsJsonArray("rotation")));
        assertEquals("soledom:item/example_robot/robot", body.getAsJsonObject("textures").get("0").getAsString());
        JsonObject north = torso.getAsJsonObject("faces").getAsJsonObject("north");
        // 32 px texture -> UVs scaled to Minecraft's 0..16 space.
        assertEquals(List.of(0.0, 0.0, 4.0, 4.0), doubles(north.getAsJsonArray("uv")));

        JsonObject antenna = packJson(conversion, "assets/soledom/models/item/example_robot/antenna.json");
        JsonObject rotation = antenna.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("rotation");
        assertEquals(22.5, rotation.get("angle").getAsDouble());
        assertEquals("z", rotation.get("axis").getAsString());

        assertTrue(conversion.packFiles().containsKey("assets/soledom/textures/item/example_robot/robot.png"));
        byte[] png = conversion.packFiles().get("assets/soledom/textures/item/example_robot/robot.png");
        assertEquals((byte) 0x89, png[0]);
        assertTrue(conversion.warnings().isEmpty(), () -> "warnings: " + conversion.warnings());
    }

    @Test
    void convertsAnimationsToModelAxes() throws Exception {
        BbModelConverter.Conversion conversion = convert(robot());
        AnimationLoader loader = new AnimationLoader();

        AnimationDefinition idle = loader.parse(conversion.animations().get("idle"), "idle", new ArrayList<>());
        assertEquals("soledom:example_robot/idle", idle.id());
        assertTrue(idle.loop());
        assertEquals(2.0, idle.duration());
        Map<String, Vec3> atOneSecond = new HashMap<>();
        idle.tracks().forEach(t -> atOneSecond.put(t.part() + "/" + t.channel(), t.sample(1.0)));
        // Blockbench applies X/Y rotation inverted and positions in pixels.
        assertEquals(new Vec3(0, -15, 0), atOneSecond.get("head/ROTATION"));
        assertEquals(new Vec3(0, 0.5 / 16, 0), atOneSecond.get("body/TRANSLATION"));
        assertEquals(new Vec3(1, 1.3, 1), atOneSecond.get("antenna/SCALE"));

        AnimationDefinition wave = loader.parse(conversion.animations().get("wave"), "wave", new ArrayList<>());
        assertFalse(wave.loop());
        assertTrue(wave.resetOnEnd());
        assertEquals(new Vec3(0, 0, -150), wave.tracks().get(0).sample(0.3));
    }

    @Test
    void reportsApproximationsAndSupportsJavaBlockAndBlockbench5Layouts() {
        JsonObject project = JsonParser.parseString("""
                {
                  "meta": {"format_version": "5.0", "model_format": "java_block"},
                  "resolution": {"width": 16, "height": 16},
                  "elements": [
                    {"name": "slab", "uuid": "c1", "from": [0, 0, 0], "to": [16, 8, 16], "origin": [8, 0, 8],
                     "rotation": [10, 30, 0],
                     "faces": {"up": {"uv": [0, 0, 16, 16], "texture": 0}, "down": {"uv": [0, 0, 16, 16], "texture": null}}},
                    {"name": "blob", "type": "mesh", "uuid": "m1"}
                  ],
                  "groups": [{"uuid": "g1", "name": "Main Part!", "origin": [8, 0, 8], "rotation": [0, 0, 0]}],
                  "outliner": [{"uuid": "g1", "children": ["c1", "m1"]}],
                  "textures": [{"name": "stone.png", "source": "", "uv_width": 16, "uv_height": 16}],
                  "animations": [{"name": "Spin", "loop": "hold", "length": 1, "animators": {"g1": {"name": "Main Part!",
                    "type": "bone", "keyframes": [
                      {"channel": "rotation", "time": 0, "data_points": [{"x": 0, "y": "math.sin(q.anim_time)", "z": 0}],
                       "interpolation": "catmullrom"}]}}}]
                }
                """).getAsJsonObject();

        BbModelConverter.Conversion conversion = new BbModelConverter().convert(project, "slab.bbmodel", SETTINGS, 1);

        JsonArray parts = conversion.model().getAsJsonArray("parts");
        JsonObject part = parts.get(0).getAsJsonObject();
        assertEquals("main_part", part.get("id").getAsString());
        // java_block projects are shifted so the block's bottom centre (8,0,8) becomes the origin.
        assertEquals(List.of(0.0, 0.0, 0.0), doubles(part.getAsJsonArray("position")));
        JsonObject slab = packJson(conversion, "assets/soledom/models/item/slab/main_part.json")
                .getAsJsonArray("elements").get(0).getAsJsonObject();
        assertEquals(List.of(0.0, 8.0, 0.0), doubles(slab.getAsJsonArray("from")));
        assertFalse(slab.getAsJsonObject("faces").has("down"));
        assertEquals("y", slab.getAsJsonObject("rotation").get("axis").getAsString());
        assertEquals(22.5, slab.getAsJsonObject("rotation").get("angle").getAsDouble());

        List<String> warnings = conversion.warnings();
        assertHas(warnings, "'blob' is a mesh");
        assertHas(warnings, "rotated on several axes");
        assertHas(warnings, "only allows -45/-22.5/0/22.5/45");
        assertHas(warnings, "texture 'stone.png' is not embedded");
        assertHas(warnings, "uses catmullrom keyframes");
        assertHas(warnings, "expressions are not supported");

        JsonObject spin = conversion.animations().get("spin");
        assertFalse(spin.get("loop").getAsBoolean());
        assertFalse(spin.get("reset_on_end").getAsBoolean());
    }

    @Test
    void shrinksOversizedBonesAndScalesThemBackThroughTheDisplayTransform() {
        JsonObject project = JsonParser.parseString("""
                {"meta": {"model_format": "free"}, "resolution": {"width": 16, "height": 16},
                 "elements": [{"name": "wing", "uuid": "c", "from": [0, 0, 0], "to": [48, 2, 2], "origin": [0, 0, 0],
                   "faces": {"north": {"uv": [0, 0, 16, 2], "texture": 0}}}],
                 "outliner": [{"name": "wing", "uuid": "g", "origin": [0, 0, 0], "children": ["c"]}],
                 "textures": [{"name": "t", "source": ""}]}
                """).getAsJsonObject();
        BbModelConverter.Conversion conversion = new BbModelConverter().convert(project, "wing.bbmodel", SETTINGS, 1);
        JsonObject model = packJson(conversion, "assets/soledom/models/item/wing/wing.json");
        JsonObject element = model.getAsJsonArray("elements").get(0).getAsJsonObject();
        // 48 px from the pivot -> shrink 2x so it fits [-16, 32], display scale 2 restores the size.
        assertEquals(32.0, element.getAsJsonArray("to").get(0).getAsDouble());
        assertEquals(2.0, model.getAsJsonObject("display").getAsJsonObject("fixed").getAsJsonArray("scale").get(0)
                .getAsDouble());
    }

    @Test
    void sanitizesNames() {
        assertEquals("dragon_boss", BbModelConverter.sanitize("Dragon Boss.bbmodel", "x"));
        assertEquals("x", BbModelConverter.sanitize("!!!", "x"));
    }

    private static List<Double> doubles(JsonArray array) {
        List<Double> values = new ArrayList<>();
        for (JsonElement e : array) {
            values.add(e.getAsDouble());
        }
        return values;
    }
}
