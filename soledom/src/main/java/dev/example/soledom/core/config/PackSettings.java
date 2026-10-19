package dev.example.soledom.core.config;

import org.jetbrains.annotations.NotNull;

/**
 * Resource pack options from {@code config.yml}.
 *
 * @param fileName        name of the exported ZIP inside {@code export/}
 * @param description     pack description shown in the client's pack list
 * @param packFormat      {@code pack_format} written for pre-1.21.9 clients
 * @param minFormat       lowest supported pack format
 * @param maxFormat       highest supported pack format
 * @param sendOnJoin      send the pack to players when they join
 * @param url             public download URL of the pack (you host it)
 * @param sha1            SHA-1 of the hosted file, or empty
 * @param useExportedHash use the SHA-1 of the last export when {@code sha1} is empty
 * @param required        kick players who decline the pack
 * @param prompt          MiniMessage prompt shown with the pack request
 */
public record PackSettings(@NotNull String fileName, @NotNull String description, int packFormat, int minFormat,
        int maxFormat, boolean sendOnJoin, @NotNull String url, @NotNull String sha1, boolean useExportedHash,
        boolean required, @NotNull String prompt) {

    public static final PackSettings DEFAULT = new PackSettings("SoledomPack.zip", "Soledom generated models", 34, 34,
            97, false, "", "", true, false, "");
}
