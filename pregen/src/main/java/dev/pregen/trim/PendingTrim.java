package dev.pregen.trim;

import dev.pregen.persistence.SelectionData;
import dev.pregen.selection.Selection;
import java.nio.file.Path;
import java.util.Optional;

/**
 * A confirmed trim waiting for the next startup. Folder paths are resolved while the world is loaded and stored as
 * absolute paths, because at startup no world object exists yet to resolve them from.
 */
public record PendingTrim(String id, SelectionData selection, boolean deleteInside, long maxInhabitedTicks,
        String regionDir, String entitiesDir, String poiDir, long createdAtMillis, String requestedBy) {

    public static PendingTrim of(String id, TrimRequest request, StorageDirs dirs, long createdAtMillis,
            String requestedBy) {
        return new PendingTrim(id, SelectionData.from(request.selection()), request.deleteInside(),
                request.maxInhabitedTicks(), dirs.region().toAbsolutePath().toString(),
                dirs.entities().toAbsolutePath().toString(), dirs.poi().toAbsolutePath().toString(), createdAtMillis,
                requestedBy);
    }

    public String worldName() {
        return selection.world();
    }

    public Optional<TrimRequest> toRequest() {
        return selection.toSelection().map(this::toRequest);
    }

    private TrimRequest toRequest(Selection resolved) {
        return new TrimRequest(resolved, deleteInside, maxInhabitedTicks);
    }

    public StorageDirs dirs() {
        return new StorageDirs(Path.of(regionDir), Path.of(entitiesDir), Path.of(poiDir));
    }
}
