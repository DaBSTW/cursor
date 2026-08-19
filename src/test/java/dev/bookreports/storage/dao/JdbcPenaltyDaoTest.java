package dev.bookreports.storage.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import dev.bookreports.storage.TestDatabases;
import dev.bookreports.storage.model.ReportPenalty;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcPenaltyDaoTest {

    private HikariDataSource dataSource;
    private PenaltyDao dao;

    @BeforeEach
    void setUp() {
        dataSource = TestDatabases.freshSqlite();
        dao = new JdbcPenaltyDao(dataSource);
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    @Test
    void insertAssignsIdAndPersistsFields() {
        UUID player = UUID.randomUUID();
        ReportPenalty inserted = dao.insert(new ReportPenalty(0, player, "FALSE_REPORT", Instant.now(), null));

        assertTrue(inserted.id() > 0);
        assertEquals(player, inserted.playerUuid());
        assertEquals("FALSE_REPORT", inserted.reason());
    }

    @Test
    void countByPlayerSinceFiltersByReasonAndWindow() {
        UUID player = UUID.randomUUID();
        dao.insert(new ReportPenalty(0, player, "FALSE_REPORT", Instant.now(), null));
        dao.insert(new ReportPenalty(0, player, "ABUSE_COOLDOWN", Instant.now(), null));

        assertEquals(1, dao.countByPlayerSince(player, "FALSE_REPORT", Instant.now().minusSeconds(60)));
        assertEquals(0, dao.countByPlayerSince(player, "FALSE_REPORT", Instant.now().plusSeconds(60)));
        assertEquals(0, dao.countByPlayerSince(UUID.randomUUID(), "FALSE_REPORT", Instant.now().minusSeconds(60)));
    }
}
