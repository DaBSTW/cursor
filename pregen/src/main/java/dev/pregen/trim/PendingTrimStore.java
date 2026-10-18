package dev.pregen.trim;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import dev.pregen.util.AtomicFiles;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@code pending-trims.json}: at most one pending trim per world. Synchronized because confirmations, listings and the
 * startup run may touch it from different threads; access is rare, so contention is irrelevant.
 */
public final class PendingTrimStore {

    private static final Type LIST_TYPE = new TypeToken<List<PendingTrim>>() {
    }.getType();

    private final Path file;
    private final Gson gson;

    public PendingTrimStore(Path file, Gson gson) {
        this.file = file;
        this.gson = gson;
    }

    public synchronized List<PendingTrim> list() throws IOException {
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            List<PendingTrim> trims = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), LIST_TYPE);
            return trims == null ? List.of() : List.copyOf(trims);
        } catch (JsonParseException e) {
            throw new IOException("Corrupt pending trim file " + file + ": " + e.getMessage(), e);
        }
    }

    public synchronized Optional<PendingTrim> find(String worldName) throws IOException {
        return list().stream().filter(trim -> trim.worldName().equals(worldName)).findFirst();
    }

    /** Adds the trim, replacing any pending trim for the same world. */
    public synchronized void put(PendingTrim trim) throws IOException {
        List<PendingTrim> trims = new ArrayList<>(list());
        trims.removeIf(existing -> existing.worldName().equals(trim.worldName()));
        trims.add(trim);
        write(trims);
    }

    public synchronized boolean remove(String worldName) throws IOException {
        List<PendingTrim> trims = new ArrayList<>(list());
        boolean removed = trims.removeIf(existing -> existing.worldName().equals(worldName));
        if (removed) {
            write(trims);
        }
        return removed;
    }

    private void write(List<PendingTrim> trims) throws IOException {
        if (trims.isEmpty()) {
            Files.deleteIfExists(file);
            return;
        }
        AtomicFiles.writeString(file, gson.toJson(trims, LIST_TYPE));
    }
}
