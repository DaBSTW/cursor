package dev.pregen.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import dev.pregen.selection.Selection;
import dev.pregen.trim.PendingTrim;
import dev.pregen.trim.PendingTrimStore;
import dev.pregen.trim.StorageDirs;
import dev.pregen.trim.TrimRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PendingTrimStoreTest {

    @TempDir
    Path directory;

    @Test
    void keepsOneTrimPerWorldAndRoundTripsTheRequest() throws IOException {
        PendingTrimStore store = new PendingTrimStore(directory.resolve("pending-trims.json"), new Gson());
        TrimRequest first = new TrimRequest(Selection.defaults("world"), false, -1);
        TrimRequest second = new TrimRequest(Selection.defaults("world").withRadius(2000), true, 600);
        StorageDirs dirs = StorageDirs.under(directory.resolve("world"));
        store.put(PendingTrim.of("a", first, dirs, 1, "Admin"));
        store.put(PendingTrim.of("b", second, dirs, 2, "Admin"));
        TrimRequest other = new TrimRequest(Selection.defaults("other"), false, -1);
        store.put(PendingTrim.of("c", other, StorageDirs.under(directory.resolve("other")), 3, "Admin"));

        assertEquals(2, store.list().size());
        PendingTrim world = store.find("world").orElseThrow();
        assertEquals(second, world.toRequest().orElseThrow());
        assertEquals(dirs.region().toAbsolutePath(), world.dirs().region());

        assertTrue(store.remove("world"));
        assertFalse(store.remove("world"));
        assertTrue(store.remove("other"));
        assertFalse(Files.exists(directory.resolve("pending-trims.json")));
    }
}
