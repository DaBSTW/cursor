package dev.example.soledom.loader;

import static dev.example.soledom.loader.ModelLoaderTest.assertHas;
import static dev.example.soledom.loader.ModelLoaderTest.resource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.example.soledom.registry.RegisteredModel;

class ContentLoaderTest {

    @TempDir
    Path dataFolder;

    private void write(String relative, String content) throws IOException {
        Path file = dataFolder.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @Test
    void loadsExamplesAndLinksAnimationsByPathAndId() throws Exception {
        write("models/example_golem.json", resource("examples/models/example_golem.json"));
        write("animations/example_idle.json", resource("examples/animations/example_idle.json"));
        write("animations/example_rotate.json", resource("examples/animations/example_rotate.json"));

        ContentLoader.LoadReport report = new ContentLoader().load(dataFolder);

        assertTrue(report.errors().isEmpty(), () -> "errors: " + report.errors());
        RegisteredModel golem = report.model("soledom:example_golem");
        assertNotNull(golem);
        assertEquals("soledom:example_idle", golem.animation("idle").id());
        assertEquals("soledom:example_rotate", golem.animation("rotate").id());
        assertEquals("rotate", golem.animationName("soledom:example_rotate"));
        assertEquals(2, report.animations().size());
    }

    @Test
    void oneBrokenFileDoesNotBlockTheOthers() throws Exception {
        write("models/good.json", """
                {"id": "good", "parts": [{"id": "t", "type": "TEXT_DISPLAY", "text": "ok"}]}
                """);
        write("models/broken.json", "{ not json");
        write("models/dangling.json", """
                {"id": "dangling", "parts": [{"id": "t", "type": "TEXT_DISPLAY", "text": "x"}],
                 "animations": {"idle": "animations/missing.json"}}
                """);

        ContentLoader.LoadReport report = new ContentLoader().load(dataFolder);

        assertEquals(1, report.models().size());
        assertNotNull(report.model("soledom:good"));
        assertHas(report.errors(), "models/broken.json: malformed JSON");
        assertHas(report.errors(), "models/dangling.json: animations.idle: 'animations/missing.json' was not found");
    }

    @Test
    void rejectsAnimationsDrivingUnknownPartsAndDuplicateIds() throws Exception {
        write("animations/wave.json", """
                {"id": "wave", "tracks": [{"part": "arm", "channel": "rotation",
                  "keyframes": [{"time": 0, "value": [0,0,0]}]}]}
                """);
        write("models/a.json", """
                {"id": "dup", "parts": [{"id": "body", "type": "TEXT_DISPLAY", "text": "a"}],
                 "animations": {"wave": "soledom:wave"}}
                """);
        write("models/b.json", """
                {"id": "dup", "parts": [{"id": "body", "type": "TEXT_DISPLAY", "text": "b"}]}
                """);

        ContentLoader.LoadReport report = new ContentLoader().load(dataFolder);

        assertHas(report.errors(), "drives part 'arm' which this model does not have");
        assertHas(report.errors(), "models/b.json: duplicate model id 'soledom:dup'");
    }

    @Test
    void readsYamlModels() throws Exception {
        write("models/sign.yml", """
                id: soledom:sign
                parts:
                  - id: text
                    type: TEXT_DISPLAY
                    text: "<gold>Hello"
                    position: [0.0, 1.5, 0.0]
                options:
                  persist: false
                """);
        ContentLoader.LoadReport report = new ContentLoader().load(dataFolder);
        assertTrue(report.errors().isEmpty(), () -> "errors: " + report.errors());
        RegisteredModel sign = report.model("soledom:sign");
        assertNotNull(sign);
        assertEquals(false, sign.definition().options().persist());
        assertEquals(1.5, sign.definition().parts().get(0).position().y());
    }
}
