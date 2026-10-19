package dev.example.soledom.resourcepack;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Writes a {@link PackGenerator.GeneratedPack} to a ZIP and computes its SHA-1.
 *
 * <p>The file is written to a temporary sibling and atomically moved into place, so a server handing the file
 * out never serves a half-written ZIP. Entry timestamps are fixed, so unchanged content produces a byte-identical
 * ZIP and the same SHA-1 (clients keep their cached copy).</p>
 */
public final class PackExporter {

    /** 2020-01-01T00:00:00Z: arbitrary fixed timestamp for reproducible archives. */
    private static final long FIXED_TIME = 1_577_836_800_000L;

    public @NotNull PackResult export(@NotNull PackGenerator.GeneratedPack pack, @NotNull Path zipFile)
            throws IOException {
        Path parent = zipFile.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, ".soledom-pack", ".tmp");
        MessageDigest sha1 = sha1();
        try {
            try (OutputStream file = Files.newOutputStream(temp);
                    DigestOutputStream digest = new DigestOutputStream(file, sha1);
                    ZipOutputStream zip = new ZipOutputStream(digest)) {
                zip.setLevel(9);
                for (Map.Entry<String, byte[]> entry : pack.entries().entrySet()) {
                    ZipEntry zipEntry = new ZipEntry(entry.getKey());
                    zipEntry.setTime(FIXED_TIME);
                    zip.putNextEntry(zipEntry);
                    zip.write(entry.getValue());
                    zip.closeEntry();
                }
            }
            moveIntoPlace(temp, zipFile);
        } finally {
            Files.deleteIfExists(temp);
        }
        String hash = HexFormat.of().formatHex(sha1.digest());
        Files.writeString(sha1File(zipFile), hash + "\n", StandardCharsets.UTF_8);
        return new PackResult(zipFile.toAbsolutePath(), hash, Files.size(zipFile), pack.entries().size(),
                pack.warnings());
    }

    /** SHA-1 recorded by the last export, or {@code null} if none. */
    public static @Nullable String readExportedSha1(@NotNull Path zipFile) {
        try {
            Path file = sha1File(zipFile);
            if (!Files.isRegularFile(file)) {
                return null;
            }
            String value = Files.readString(file, StandardCharsets.UTF_8).trim();
            return value.matches("[0-9a-f]{40}") ? value : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static Path sha1File(Path zipFile) {
        return zipFile.resolveSibling(zipFile.getFileName() + ".sha1");
    }

    private static void moveIntoPlace(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static MessageDigest sha1() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is required by the Java platform", e);
        }
    }

    /**
     * Outcome of an export.
     *
     * @param zip      absolute path of the ZIP
     * @param sha1     lowercase hex SHA-1 of the ZIP, as expected by {@code resource-pack.sha1}
     * @param size     file size in bytes
     * @param entries  number of files inside
     * @param warnings generator warnings
     */
    public record PackResult(@NotNull Path zip, @NotNull String sha1, long size, int entries,
            @NotNull List<String> warnings) {
    }
}
