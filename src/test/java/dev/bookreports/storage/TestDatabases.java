package dev.bookreports.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.bookreports.config.StorageType;
import java.util.logging.Logger;

/** Shared test fixture: a fresh, fully migrated in-memory SQLite database. */
public final class TestDatabases {

    private TestDatabases() {
    }

    public static HikariDataSource freshSqlite() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite::memory:");
        // A single connection keeps every caller pointed at the same in-memory database instead of
        // each borrow opening a brand new, empty ":memory:" instance.
        config.setMaximumPoolSize(1);
        config.setPoolName("test-" + System.nanoTime());
        HikariDataSource dataSource = new HikariDataSource(config);
        new Migrator(dataSource, StorageType.SQLITE, Logger.getLogger("BookReportsTest")).migrate();
        return dataSource;
    }
}
