package dev.bookreports.storage.dao;

import dev.bookreports.storage.StorageException;
import dev.bookreports.storage.model.Priority;
import dev.bookreports.storage.model.Report;
import dev.bookreports.storage.model.ReportStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/** ANSI-compatible JDBC implementation of {@link ReportDao}, works against both the SQLite and MySQL dialects. */
public final class JdbcReportDao implements ReportDao {

    private final DataSource dataSource;

    public JdbcReportDao(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Report insert(Report report) {
        String sql = "INSERT INTO br_reports (uuid, reporter_uuid, reporter_name, target_uuid, target_name, "
                + "category_id, sub_reason_id, evidence_text, server, status, priority, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        long id;
        // The insert connection must be closed before findById below borrows another one — the pool is
        // sized to 1 for SQLite, so holding both open at once would deadlock waiting for itself.
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, report.uuid().toString());
            statement.setString(2, report.reporterUuid().toString());
            statement.setString(3, report.reporterName());
            statement.setString(4, report.targetUuid().toString());
            statement.setString(5, report.targetName());
            statement.setString(6, report.categoryId());
            statement.setString(7, report.subReasonId());
            statement.setString(8, report.evidenceText());
            statement.setString(9, report.server());
            statement.setString(10, report.status().name());
            statement.setString(11, report.priority().name());
            statement.setTimestamp(12, Timestamp.from(report.createdAt()));
            statement.executeUpdate();
            id = generatedId(statement, report.uuid());
        } catch (SQLException e) {
            throw new StorageException("Failed to insert report for target=" + report.targetUuid(), e);
        }
        return findById(id)
                .orElseThrow(() -> new StorageException("Inserted report id=" + id + " could not be re-read"));
    }

    private long generatedId(Statement statement, UUID reportUuid) throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (!keys.next()) {
                throw new StorageException("Insert did not return a generated id for report uuid=" + reportUuid);
            }
            return keys.getLong(1);
        }
    }

    @Override
    public Optional<Report> findByUuid(UUID uuid) {
        String sql = "SELECT * FROM br_reports WHERE uuid = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            return queryOne(statement);
        } catch (SQLException e) {
            throw new StorageException("Failed to read report uuid=" + uuid, e);
        }
    }

    @Override
    public Optional<Report> findById(long id) {
        String sql = "SELECT * FROM br_reports WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            return queryOne(statement);
        } catch (SQLException e) {
            throw new StorageException("Failed to read report id=" + id, e);
        }
    }

    private Optional<Report> queryOne(PreparedStatement statement) throws SQLException {
        try (ResultSet rs = statement.executeQuery()) {
            return rs.next() ? Optional.of(map(rs)) : Optional.empty();
        }
    }

    @Override
    public List<Report> findByTarget(UUID targetUuid) {
        String sql = "SELECT * FROM br_reports WHERE target_uuid = ? ORDER BY created_at DESC";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, targetUuid.toString());
            return queryList(statement);
        } catch (SQLException e) {
            throw new StorageException("Failed to read report history for target=" + targetUuid, e);
        }
    }

    @Override
    public List<Report> findByStatus(ReportStatus status, int page, int pageSize) {
        if (page < 0 || pageSize < 1) {
            throw new IllegalArgumentException("page must be >= 0 and pageSize must be >= 1");
        }
        String sql = "SELECT * FROM br_reports WHERE status = ? "
                + "ORDER BY priority DESC, created_at ASC LIMIT ? OFFSET ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setInt(2, pageSize);
            statement.setInt(3, page * pageSize);
            return queryList(statement);
        } catch (SQLException e) {
            throw new StorageException("Failed to read report queue for status=" + status, e);
        }
    }

    private List<Report> queryList(PreparedStatement statement) throws SQLException {
        try (ResultSet rs = statement.executeQuery()) {
            List<Report> results = new ArrayList<>();
            while (rs.next()) {
                results.add(map(rs));
            }
            return List.copyOf(results);
        }
    }

    @Override
    public int countByReporterSince(UUID reporterUuid, Instant since) {
        String sql = "SELECT COUNT(*) FROM br_reports WHERE reporter_uuid = ? AND created_at >= ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, reporterUuid.toString());
            statement.setTimestamp(2, Timestamp.from(since));
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to count reports for reporter=" + reporterUuid, e);
        }
    }

    @Override
    public boolean updateStatus(long id, ReportStatus status, UUID reviewerUuid, String resolutionNote) {
        String sql = "UPDATE br_reports SET status = ?, reviewer_uuid = ?, resolution_note = ?, "
                + "resolved_at = ? WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            boolean resolved = status != ReportStatus.PENDING && status != ReportStatus.IN_REVIEW;
            statement.setString(1, status.name());
            statement.setString(2, reviewerUuid != null ? reviewerUuid.toString() : null);
            statement.setString(3, resolutionNote);
            statement.setTimestamp(4, resolved ? Timestamp.from(Instant.now()) : null);
            statement.setLong(5, id);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new StorageException("Failed to update status for report id=" + id, e);
        }
    }

    @Override
    public boolean claim(long id, UUID reviewerUuid) {
        String sql = "UPDATE br_reports SET reviewer_uuid = ?, claimed_at = ?, status = ?, "
                + "claim_version = claim_version + 1 WHERE id = ? AND reviewer_uuid IS NULL";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, reviewerUuid.toString());
            statement.setTimestamp(2, Timestamp.from(Instant.now()));
            statement.setString(3, ReportStatus.IN_REVIEW.name());
            statement.setLong(4, id);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new StorageException("Failed to claim report id=" + id, e);
        }
    }

    private Report map(ResultSet rs) throws SQLException {
        Timestamp claimedAt = rs.getTimestamp("claimed_at");
        Timestamp resolvedAt = rs.getTimestamp("resolved_at");
        String reviewerUuid = rs.getString("reviewer_uuid");
        return new Report(rs.getLong("id"), UUID.fromString(rs.getString("uuid")),
                UUID.fromString(rs.getString("reporter_uuid")), rs.getString("reporter_name"),
                UUID.fromString(rs.getString("target_uuid")), rs.getString("target_name"), rs.getString("category_id"),
                rs.getString("sub_reason_id"), rs.getString("evidence_text"), rs.getString("server"),
                ReportStatus.valueOf(rs.getString("status")), Priority.valueOf(rs.getString("priority")),
                reviewerUuid != null ? UUID.fromString(reviewerUuid) : null, rs.getString("resolution_note"),
                rs.getTimestamp("created_at").toInstant(), claimedAt != null ? claimedAt.toInstant() : null,
                resolvedAt != null ? resolvedAt.toInstant() : null, rs.getInt("claim_version"));
    }
}
