package dev.bookreports.storage.dao;

import dev.bookreports.storage.StorageException;
import dev.bookreports.storage.model.ReportPenalty;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcPenaltyDao implements PenaltyDao {

    private final DataSource dataSource;

    public JdbcPenaltyDao(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public ReportPenalty insert(ReportPenalty penalty) {
        String sql = "INSERT INTO br_report_penalties (player_uuid, reason, applied_at, expires_at) "
                + "VALUES (?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, penalty.playerUuid().toString());
            statement.setString(2, penalty.reason());
            statement.setTimestamp(3, Timestamp.from(penalty.appliedAt()));
            statement.setTimestamp(4, penalty.expiresAt() != null ? Timestamp.from(penalty.expiresAt()) : null);
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new StorageException(
                            "Insert did not return a generated id for penalty player=" + penalty.playerUuid());
                }
                return new ReportPenalty(keys.getLong(1), penalty.playerUuid(), penalty.reason(), penalty.appliedAt(),
                        penalty.expiresAt());
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to insert penalty for player=" + penalty.playerUuid(), e);
        }
    }

    @Override
    public int countByPlayerSince(UUID playerUuid, String reason, Instant since) {
        String sql = "SELECT COUNT(*) FROM br_report_penalties WHERE player_uuid = ? "
                + "AND reason = ? AND applied_at >= ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, reason);
            statement.setTimestamp(3, Timestamp.from(since));
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to count penalties for player=" + playerUuid, e);
        }
    }
}
