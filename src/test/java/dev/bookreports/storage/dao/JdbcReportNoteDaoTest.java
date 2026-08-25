package dev.bookreports.storage.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import dev.bookreports.storage.TestDatabases;
import dev.bookreports.storage.model.Priority;
import dev.bookreports.storage.model.Report;
import dev.bookreports.storage.model.ReportNote;
import dev.bookreports.storage.model.ReportStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcReportNoteDaoTest {

    private HikariDataSource dataSource;
    private ReportNoteDao noteDao;
    private ReportDao reportDao;

    @BeforeEach
    void setUp() {
        dataSource = TestDatabases.freshSqlite();
        noteDao = new JdbcReportNoteDao(dataSource);
        reportDao = new JdbcReportDao(dataSource);
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    @Test
    void insertAssignsIdAndReturnsTheStoredNote() {
        long reportId = insertReport();
        UUID author = UUID.randomUUID();

        ReportNote inserted = noteDao
                .insert(new ReportNote(0, reportId, author, "Steve", "still watching this one", Instant.now()));

        assertTrue(inserted.id() > 0);
        assertEquals("still watching this one", inserted.noteText());
    }

    @Test
    void findByReportReturnsOldestFirst() throws InterruptedException {
        long reportId = insertReport();
        UUID author = UUID.randomUUID();
        noteDao.insert(new ReportNote(0, reportId, author, "Steve", "first note", Instant.now()));
        Thread.sleep(5);
        noteDao.insert(new ReportNote(0, reportId, author, "Steve", "second note", Instant.now()));

        List<ReportNote> notes = noteDao.findByReport(reportId);

        assertEquals(2, notes.size());
        assertEquals("first note", notes.get(0).noteText());
        assertEquals("second note", notes.get(1).noteText());
    }

    @Test
    void findByReportOnlyReturnsNotesForThatReport() {
        long reportId = insertReport();
        long otherReportId = insertReport();
        noteDao.insert(new ReportNote(0, reportId, UUID.randomUUID(), "Steve", "on the right report", Instant.now()));
        noteDao.insert(
                new ReportNote(0, otherReportId, UUID.randomUUID(), "Steve", "on a different report", Instant.now()));

        List<ReportNote> notes = noteDao.findByReport(reportId);

        assertEquals(1, notes.size());
        assertEquals("on the right report", notes.get(0).noteText());
    }

    @Test
    void findByReportIsEmptyWhenNoNotesExist() {
        long reportId = insertReport();

        assertTrue(noteDao.findByReport(reportId).isEmpty());
    }

    private long insertReport() {
        Report draft = new Report(0, UUID.randomUUID(), UUID.randomUUID(), "Reporter", UUID.randomUUID(), "Target",
                "hacks", null, null, "default", ReportStatus.PENDING, Priority.LOW, null, null, Instant.now(), null,
                null, 0, null, null, null, null, null, null, false);
        return reportDao.insert(draft).id();
    }
}
