package dev.bookreports.util;

/** Detects whether the server is running Folia at runtime, without a compile-time dependency on it. */
public final class FoliaDetector {

    private static final boolean FOLIA = classExists("io.papermc.paper.threadedregions.RegionizedServer");

    private FoliaDetector() {
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    private static boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
