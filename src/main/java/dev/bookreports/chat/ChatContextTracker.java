package dev.bookreports.chat;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.bookreports.util.TextSanitizer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Bounded per-player buffer of recent chat lines, so a report can carry the target's own words as evidence without the
 * reporter having to type or copy anything themselves.
 *
 * <p>
 * Purely in-memory (same reasoning as {@link dev.bookreports.service.CooldownService}): chat context is only useful
 * while it's fresh, so there's no DB round trip and nothing to persist here — the snapshot taken at report-submission
 * time is what ends up stored on the {@code Report} row itself.
 */
public final class ChatContextTracker implements Listener {

    public static final int MAX_LINES = 10;
    public static final int MAX_LINE_LENGTH = 100;
    public static final int MAX_TOTAL_LENGTH = 500;

    private final Cache<UUID, Deque<String>> buffers = Caffeine.newBuilder().maximumSize(2_000)
            .expireAfterAccess(Duration.ofMinutes(15)).build();

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        record(event.getPlayer().getUniqueId(), PlainTextComponentSerializer.plainText().serialize(event.message()));
    }

    /** Package-visible so tests can feed lines directly, without a Bukkit chat event. */
    void record(UUID playerId, String rawLine) {
        String line = TextSanitizer.stripAndTruncate(rawLine, MAX_LINE_LENGTH);
        if (line == null || line.isBlank()) {
            return;
        }
        Deque<String> lines = buffers.get(playerId, id -> new ArrayDeque<>(MAX_LINES));
        synchronized (lines) {
            if (lines.size() >= MAX_LINES) {
                lines.removeFirst();
            }
            lines.addLast(line);
        }
    }

    /** The target's buffered chat, oldest first, joined and capped for storage — {@code null} if none is buffered. */
    public String recentContext(UUID playerId) {
        Deque<String> lines = buffers.getIfPresent(playerId);
        if (lines == null) {
            return null;
        }
        String joined;
        synchronized (lines) {
            if (lines.isEmpty()) {
                return null;
            }
            joined = String.join(" | ", lines);
        }
        return joined.length() > MAX_TOTAL_LENGTH ? joined.substring(0, MAX_TOTAL_LENGTH) : joined;
    }
}
