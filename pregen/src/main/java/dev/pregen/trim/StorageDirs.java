package dev.pregen.trim;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.World;

/**
 * Folders of one dimension: block data ({@code region}), entities and points of interest. Each holds region files with
 * the same names, one per 32x32 chunk area.
 */
public record StorageDirs(Path region, Path entities, Path poi) {

    public static StorageDirs under(Path dimensionFolder) {
        return new StorageDirs(dimensionFolder.resolve("region"), dimensionFolder.resolve("entities"),
                dimensionFolder.resolve("poi"));
    }

    public List<Path> all() {
        return List.of(region, entities, poi);
    }

    public boolean exists() {
        return Files.isDirectory(region);
    }

    /**
     * Finds the dimension folder of a loaded world. Paper 1.21.x stores the nether and end of a world in {@code DIM-1}
     * / {@code DIM1} subfolders of their world folder, while 26.x stores every dimension in its own folder. Both
     * layouts are probed, so the result does not depend on the server version.
     */
    public static StorageDirs locate(World world) {
        return locate(world.getWorldFolder().toPath(), world.getEnvironment());
    }

    static StorageDirs locate(Path worldFolder, World.Environment environment) {
        List<Path> candidates = switch (environment) {
            case NETHER -> List.of(worldFolder.resolve("DIM-1"), worldFolder);
            case THE_END -> List.of(worldFolder.resolve("DIM1"), worldFolder);
            default -> List.of(worldFolder);
        };
        for (Path candidate : candidates) {
            StorageDirs dirs = under(candidate);
            if (dirs.exists()) {
                return dirs;
            }
        }
        return under(candidates.getFirst());
    }
}
