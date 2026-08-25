package dev.bookreports.storage.dao;

import dev.bookreports.storage.StorageException;
import dev.bookreports.storage.model.ReportNote;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcReportNoteDao implements ReportNoteDao {

    private final DataSource dataSource;

    public JdbcReportNoteDao(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public ReportNote insert(ReportNote note) {
        String sql = "INSERT INTO br_report_notes (report_id, author_uuid, author_name, note_text, created_at) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, note.reportId());
            statement.setString(2, note.authorUuid().toString());
            statement.setString(3, note.authorName());
            statement.setString(4, note.noteText());
            statement.setTimestamp(5, Timestamp.from(note.createdAt()));
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new StorageException(
                            "Insert did not return a generated id for note on report id=" + note.reportId());
                }
                return new ReportNote(keys.getLong(1), note.reportId(), note.authorUuid(), note.authorName(),
                        note.noteText(), note.createdAt());
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to insert note for report id=" + note.reportId(), e);
        }
    }

    @Override
    public List<ReportNote> findByReport(long reportId) {
        String sql = "SELECT * FROM br_report_notes WHERE report_id = ? ORDER BY created_at ASC";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, reportId);
            try (ResultSet rs = statement.executeQuery()) {
                List<ReportNote> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(map(rs));
                }
                return List.copyOf(results);
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to read notes for report id=" + reportId, e);
        }
    }

    private ReportNote map(ResultSet rs) throws SQLException {
        return new ReportNote(rs.getLong("id"), rs.getLong("report_id"), UUID.fromString(rs.getString("author_uuid")),
                rs.getString("author_name"), rs.getString("note_text"), rs.getTimestamp("created_at").toInstant());
    }
}
