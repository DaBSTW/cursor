package dev.bookreports.storage.dao;

import java.util.UUID;

public interface StaffPrefsDao {

    /** {@code true} (the default) unless the player has explicitly turned notifications off. */
    boolean notificationsEnabled(UUID playerUuid);

    void setNotificationsEnabled(UUID playerUuid, boolean enabled);
}
