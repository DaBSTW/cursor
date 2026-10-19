package dev.example.soledom.blockbench;

import static dev.example.soledom.TestResources.assertHas;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.example.soledom.TestResources;
import dev.example.soledom.api.ModelDefinition;
import dev.example.soledom.loader.ContentLoader;
import dev.example.soledom.registry.RegisteredModel;
import dev.example.soledom.resourcepack.PackGenerator;
import dev.example.soledom.resourcepack.PackMeta;

class BlockbenchImporterTest {

    @TempDir
    Path dataFolder;

    private void write(String relative, String content) throws Exception {
        Path file = dataFolder.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @Test
    void contentLoaderImportsProjectsWithAnimationsAndPackAssets() throws Exception {
        write("blockbench/example_robot.bbmodel", TestResources.read("examples/blockbench/example_robot.bbmodel"));

        ContentLoader.LoadReport report = new ContentLoader().load(dataFolder, ImportSettings.DEFAULT);

        assertTrue(report.errors().isEmpty(), () -> "errors: " + report.errors());
        RegisteredModel robot = report.model("soledom:example_robot");
        assertNotNull(robot);
        assertEquals("soledom:example_robot/idle", robot.animation("idle").id());
        assertNotNull(robot.animation("wave"));
        assertTrue(report.packAssets().containsKey("assets/soledom/models/item/example_robot/head.json"));
        assertTrue(report.packAssets().containsKey("assets/soledom/textures/item/example_robot/robot.png"));

        // The generated item models are used as-is by the pack generator: no placeholders needed.
        PackGenerator.GeneratedPack pack = new PackGenerator(id -> false).generate(
                List.of(robot.definition()), new PackMeta("t", 34, 34, 97), null, report.packAssets());
        assertEquals(0, pack.placeholders());
        assertTrue(pack.entries().containsKey("assets/minecraft/items/paper.json"));
    }

    @Test
    void sidecarOverridesModelFieldsAndCustomModelDataIsAllocatedPerFile() throws Exception {
        String robot = TestResources.read("examples/blockbench/example_robot.bbmodel");
        write("blockbench/a_robot.bbmodel", robot);
        write("blockbench/b_robot.bbmodel", robot);
        write("blockbench/b_robot.soledom.json", """
                {"id": "soledom:big_robot", "scale": 2.0, "default_animation": "wave",
                 "root": {"type": "INTERACTION", "width": 1.5, "height": 3.6},
                 "metadata": {"author": "me"}, "parts": []}
                """);

        BlockbenchImporter.Result result = new BlockbenchImporter().importAll(dataFolder,
                new ImportSettings(true, "soledom", "minecraft:paper", 100));

        assertTrue(result.errors().isEmpty(), () -> "errors: " + result.errors());
        ModelDefinition first = result.models().get("soledom:a_robot").definition();
        ModelDefinition big = result.models().get("soledom:big_robot").definition();
        assertEquals(100, first.part("root").orElseThrow().customModelData());
        assertEquals(108, big.part("root").orElseThrow().customModelData());
        assertEquals(2.0, big.scale().x());
        assertEquals("wave", big.defaultAnimation());
        assertEquals(3.6f, big.root().height());
        assertEquals("me", big.metadata().get("author"));
        assertEquals(9, big.parts().size());
        assertHas(result.warnings(), "'parts' cannot be overridden here");
    }

    @Test
    void brokenProjectsAreReportedAndDisabledImportSkipsTheFolder() throws Exception {
        write("blockbench/broken.bbmodel", "{ nope");
        write("blockbench/robot.bbmodel", TestResources.read("examples/blockbench/example_robot.bbmodel"));

        BlockbenchImporter.Result result = new BlockbenchImporter().importAll(dataFolder, ImportSettings.DEFAULT);
        assertHas(result.errors(), "blockbench/broken.bbmodel: cannot read project");
        assertNotNull(result.models().get("soledom:robot"));

        BlockbenchImporter.Result disabled = new BlockbenchImporter().importAll(dataFolder,
                new ImportSettings(false, "soledom", "minecraft:paper", 1));
        assertTrue(disabled.models().isEmpty());
    }
}
