package dev.bookreports.storage.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import dev.bookreports.storage.TestDatabases;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcStaffPrefsDaoTest {

    private HikariDataSource dataSource;
    private StaffPrefsDao dao;

    @BeforeEach
    void setUp() {
        dataSource = TestDatabases.freshSqlite();
        dao = new JdbcStaffPrefsDao(dataSource);
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    @Test
    void defaultsToEnabledForAPlayerWithNoRow() {
        assertTrue(dao.notificationsEnabled(UUID.randomUUID()));
    }

    @Test
    void setNotificationsEnabledInsertsOnFirstCall() {
        UUID player = UUID.randomUUID();

        dao.setNotificationsEnabled(player, false);

        assertFalse(dao.notificationsEnabled(player));
    }

    @Test
    void setNotificationsEnabledUpdatesOnSubsequentCalls() {
        UUID player = UUID.randomUUID();

        dao.setNotificationsEnabled(player, false);
        dao.setNotificationsEnabled(player, true);

        assertTrue(dao.notificationsEnabled(player));
    }
}
