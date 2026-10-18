package dev.pregen.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import dev.pregen.generation.TaskCheckpoint;
import dev.pregen.geometry.ShapeType;
import dev.pregen.pattern.PatternType;
import dev.pregen.selection.Selection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonTaskStoreTest {

    @TempDir
    Path directory;

    private final List<String> warnings = new ArrayList<>();

    private JsonTaskStore store() {
        return new JsonTaskStore(directory, new Gson(), warnings::add);
    }

    @Test
    void roundTripsATask() throws IOException {
        Selection selection = new Selection("world_nether", 10.5, -20, 300, 150, ShapeType.ELLIPSE, PatternType.SPIRAL,
                "");
        store().save(StoredTask.from(new TaskCheckpoint(selection, 4242, 90_000)));
        StoredTask loaded = store().load("world_nether").orElseThrow();
        assertEquals(selection, loaded.selection().toSelection().orElseThrow());
        assertEquals(4242, loaded.patternIndex());
        assertEquals(90_000, loaded.elapsedMillis());
        assertEquals(1, store().loadAll().size());
    }

    @Test
    void worldNamesWithUnsafeCharactersStayInsideTheDirectory() throws IOException {
        Selection selection = Selection.defaults("../evil/world");
        store().save(StoredTask.from(new TaskCheckpoint(selection, 0, 0)));
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
        assertTrue(store().load("../evil/world").isPresent());
    }

    @Test
    void skipsCorruptAndInvalidFilesWithAWarning() throws IOException {
        Files.writeString(directory.resolve("broken.json"), "{not json", StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("invalid.json"),
                "{\"formatVersion\":1,\"selection\":{\"world\":\"w\",\"radiusX\":-1,\"radiusZ\":1,"
                        + "\"shape\":\"square\",\"pattern\":\"region\"},\"patternIndex\":0,\"elapsedMillis\":0}",
                StandardCharsets.UTF_8);
        assertTrue(store().loadAll().isEmpty());
        assertEquals(2, warnings.size());
    }

    @Test
    void deleteRemovesTheFile() throws IOException {
        store().save(StoredTask.from(new TaskCheckpoint(Selection.defaults("w"), 0, 0)));
        store().delete("w");
        assertTrue(store().load("w").isEmpty());
    }
}
