package dev.bookreports.storage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.bookreports.config.StorageType;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MigratorTest {

    private static final Logger LOGGER = Logger.getLogger("BookReportsTest");

    private HikariDataSource dataSource;

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite::memory:");
        config.setMaximumPoolSize(1);
        config.setPoolName("migrator-test-" + System.nanoTime());
        dataSource = new HikariDataSource(config);
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    @Test
    void migratesFreshDatabaseToLatestVersion() throws SQLException {
        new Migrator(dataSource, StorageType.SQLITE, LOGGER).migrate();

        assertEquals(2, schemaVersion());
        assertTrue(hasColumn("br_reports", "claim_version"));
    }

    @Test
    void runningMigrateTwiceIsANoOp() throws SQLException {
        Migrator migrator = new Migrator(dataSource, StorageType.SQLITE, LOGGER);
        migrator.migrate();

        assertDoesNotThrow(migrator::migrate);
        assertEquals(2, schemaVersion());
    }

    @Test
    void upgradesAnExistingV1DatabaseToV2WithoutLosingData() throws SQLException {
        seedLegacyV1DatabaseWithOneReport();

        new Migrator(dataSource, StorageType.SQLITE, LOGGER).migrate();

        assertEquals(2, schemaVersion());
        assertTrue(hasColumn("br_reports", "claim_version"));
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement
                        .executeQuery("SELECT claim_version FROM br_reports WHERE uuid = 'seed-uuid'")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1));
        }
    }

    private void seedLegacyV1DatabaseWithOneReport() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE br_meta (schema_version INTEGER NOT NULL)");
            statement.execute("INSERT INTO br_meta (schema_version) VALUES (1)");
            statement.execute("""
                    CREATE TABLE br_reports (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid CHAR(36) NOT NULL UNIQUE,
                        reporter_uuid CHAR(36) NOT NULL,
                        reporter_name VARCHAR(16) NOT NULL,
                        target_uuid CHAR(36) NOT NULL,
                        target_name VARCHAR(16) NOT NULL,
                        category_id VARCHAR(32) NOT NULL,
                        sub_reason_id VARCHAR(32),
                        evidence_text VARCHAR(256),
                        server VARCHAR(64) NOT NULL DEFAULT 'default',
                        status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                        priority VARCHAR(10) NOT NULL DEFAULT 'LOW',
                        reviewer_uuid CHAR(36),
                        resolution_note VARCHAR(256),
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        claimed_at TIMESTAMP,
                        resolved_at TIMESTAMP
                    )
                    """);
            statement.execute("""
                    INSERT INTO br_reports
                        (uuid, reporter_uuid, reporter_name, target_uuid, target_name, category_id, server)
                    VALUES
                        ('seed-uuid', 'reporter-uuid', 'Reporter', 'target-uuid', 'Target', 'hacks', 'default')
                    """);
        }
    }

    private int schemaVersion() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT schema_version FROM br_meta")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private boolean hasColumn(String table, String column) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (rs.getString("name").equalsIgnoreCase(column)) {
                    return true;
                }
            }
            return false;
        }
    }
}
