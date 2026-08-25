package dev.bookreports.storage;

import dev.bookreports.config.StorageType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * Applies incremental SQL migrations, tracked by {@code br_meta.schema_version}.
 *
 * <p>
 * No external migration framework (see SPECS.md §6.3): each migration is a plain, idempotent-by-version SQL script
 * under {@code resources/db/<dialect>/}, applied once and never re-run.
 */
public final class Migrator {

    private record Migration(int version, String fileName) {
    }

    private static final List<Migration> MIGRATIONS = List.of(new Migration(1, "V1__init.sql"),
            new Migration(2, "V2__add_claim_version.sql"), new Migration(3, "V3__staff_prefs.sql"),
            new Migration(4, "V4__chat_context.sql"), new Migration(5, "V5__sanction_audit.sql"),
            new Migration(6, "V6__coreprotect_context.sql"), new Migration(7, "V7__report_locations.sql"),
            new Migration(8, "V8__report_notes.sql"), new Migration(9, "V9__report_archive.sql"));

    private final DataSource dataSource;
    private final StorageType storageType;
    private final Logger logger;

    public Migrator(DataSource dataSource, StorageType storageType, Logger logger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.storageType = Objects.requireNonNull(storageType, "storageType");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void migrate() {
        try (Connection connection = dataSource.getConnection()) {
            ensureMetaTable(connection);
            int current = currentVersion(connection);
            List<Migration> pending = MIGRATIONS.stream().filter(migration -> migration.version() > current)
                    .sorted(Comparator.comparingInt(Migration::version)).toList();
            for (Migration migration : pending) {
                apply(connection, migration);
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to run database migrations", e);
        }
    }

    private void ensureMetaTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS br_meta (schema_version INTEGER NOT NULL)");
        }
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM br_meta")) {
            rs.next();
            if (rs.getInt(1) == 0) {
                statement.execute("INSERT INTO br_meta (schema_version) VALUES (0)");
            }
        }
    }

    private int currentVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT schema_version FROM br_meta")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private void apply(Connection connection, Migration migration) throws SQLException {
        String script = readScript(migration.fileName());
        try (Statement statement = connection.createStatement()) {
            for (String sql : script.split(";")) {
                String trimmed = sql.strip();
                if (!trimmed.isEmpty()) {
                    statement.execute(trimmed);
                }
            }
            statement.execute("UPDATE br_meta SET schema_version = " + migration.version());
        }
        logger.info("Applied database migration: version=" + migration.version() + " file=" + migration.fileName());
    }

    private String readScript(String fileName) {
        String dialect = storageType == StorageType.SQLITE ? "sqlite" : "mysql";
        String resourcePath = "/db/" + dialect + "/" + fileName;
        try (InputStream in = Migrator.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new StorageException("Missing migration script: " + resourcePath);
            }
            StringBuilder builder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.strip().startsWith("--")) {
                        builder.append(line).append('\n');
                    }
                }
            }
            return builder.toString();
        } catch (IOException e) {
            throw new StorageException("Failed to read migration script: " + resourcePath, e);
        }
    }
}
