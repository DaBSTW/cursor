package dev.pregen.persistence;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import dev.pregen.util.AtomicFiles;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Saves each task as {@code tasks/<world>.json}, written atomically. Unreadable files are reported and skipped. */
public final class JsonTaskStore implements TaskStore {

    private static final String EXTENSION = ".json";

    private final Path directory;
    private final Gson gson;
    private final Consumer<String> warnings;

    public JsonTaskStore(Path directory, Gson gson, Consumer<String> warnings) {
        this.directory = directory;
        this.gson = gson;
        this.warnings = warnings;
    }

    @Override
    public List<StoredTask> loadAll() throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        List<StoredTask> tasks = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(EXTENSION)).sorted().toList()) {
                read(file).ifPresent(tasks::add);
            }
        }
        return List.copyOf(tasks);
    }

    @Override
    public Optional<StoredTask> load(String worldName) throws IOException {
        Path file = fileFor(worldName);
        return Files.isRegularFile(file) ? read(file) : Optional.empty();
    }

    @Override
    public void save(StoredTask task) throws IOException {
        AtomicFiles.writeString(fileFor(task.selection().world()), gson.toJson(task));
    }

    @Override
    public void delete(String worldName) throws IOException {
        Files.deleteIfExists(fileFor(worldName));
    }

    private Optional<StoredTask> read(Path file) throws IOException {
        try {
            StoredTask task = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), StoredTask.class);
            if (task != null && task.isUsable()) {
                return Optional.of(task);
            }
            warnings.accept("Ignoring invalid saved task file: file=" + file);
        } catch (JsonParseException e) {
            warnings.accept("Ignoring corrupt saved task file: file=" + file + " error=" + e.getMessage());
        }
        return Optional.empty();
    }

    // World names are folder names but may still contain characters that are unsafe in a flat file name.
    private Path fileFor(String worldName) {
        return directory.resolve(URLEncoder.encode(worldName, StandardCharsets.UTF_8) + EXTENSION);
    }
}
