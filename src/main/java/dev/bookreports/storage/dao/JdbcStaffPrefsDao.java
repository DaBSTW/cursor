package dev.bookreports.storage.dao;

import dev.bookreports.storage.StorageException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcStaffPrefsDao implements StaffPrefsDao {

    private final DataSource dataSource;

    public JdbcStaffPrefsDao(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public boolean notificationsEnabled(UUID playerUuid) {
        String sql = "SELECT notifications_enabled FROM br_staff_prefs WHERE player_uuid = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                return !rs.next() || rs.getInt(1) != 0;
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to read staff prefs for player=" + playerUuid, e);
        }
    }

    @Override
    public void setNotificationsEnabled(UUID playerUuid, boolean enabled) {
        // No vendor-specific UPSERT: update first, insert only if nothing matched, same as every other DAO here.
        String update = "UPDATE br_staff_prefs SET notifications_enabled = ? WHERE player_uuid = ?";
        String insert = "INSERT INTO br_staff_prefs (player_uuid, notifications_enabled) VALUES (?, ?)";
        try (Connection connection = dataSource.getConnection()) {
            int value = enabled ? 1 : 0;
            try (PreparedStatement statement = connection.prepareStatement(update)) {
                statement.setInt(1, value);
                statement.setString(2, playerUuid.toString());
                if (statement.executeUpdate() == 1) {
                    return;
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(insert)) {
                statement.setString(1, playerUuid.toString());
                statement.setInt(2, value);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to save staff prefs for player=" + playerUuid, e);
        }
    }
}
