package dev.pregen.persistence;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Storage of saved generation tasks, one per world. Implementations perform blocking I/O. */
public interface TaskStore {

    List<StoredTask> loadAll() throws IOException;

    Optional<StoredTask> load(String worldName) throws IOException;

    void save(StoredTask task) throws IOException;

    void delete(String worldName) throws IOException;
}
