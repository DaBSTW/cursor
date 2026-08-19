package dev.bookreports.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.bookreports.config.MySqlSettings;
import dev.bookreports.config.StorageType;
import java.io.File;
import java.util.Objects;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * Owns the HikariCP connection pool lifecycle.
 *
 * <p>
 * {@link #connect} performs blocking JDBC calls (pool warm-up plus schema migrations) and must be invoked off the
 * main/region thread — see {@link dev.bookreports.util.SchedulerAdapter#runAsync}.
 */
public final class StorageManager {

    private final Logger logger;
    private volatile HikariDataSource pool;

    public StorageManager(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public DataSource connect(StorageType type, MySqlSettings mysql, File dataFolder) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("BookReports");
        if (type == StorageType.SQLITE) {
            File dbFile = new File(dataFolder, "bookreports.db");
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            // SQLite serializes writers at the engine level; a single pooled connection avoids
            // SQLITE_BUSY contention instead of racing readers/writers across a bigger pool.
            hikariConfig.setMaximumPoolSize(1);
        } else {
            hikariConfig.setJdbcUrl("jdbc:mysql://" + mysql.host() + ":" + mysql.port() + "/" + mysql.database()
                    + "?useSSL=false&characterEncoding=utf8");
            hikariConfig.setUsername(mysql.user());
            hikariConfig.setPassword(mysql.password());
            hikariConfig.setMaximumPoolSize(mysql.poolSize());
        }

        HikariDataSource newPool = new HikariDataSource(hikariConfig);
        try {
            new Migrator(newPool, type, logger).migrate();
        } catch (RuntimeException e) {
            newPool.close();
            throw e;
        }
        this.pool = newPool;
        return newPool;
    }

    public void close() {
        HikariDataSource current = pool;
        if (current != null) {
            current.close();
        }
    }
}
