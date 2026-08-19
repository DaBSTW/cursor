package dev.bookreports.integration.proxy;

import dev.bookreports.api.event.ReportCreatedEvent;
import dev.bookreports.storage.model.Priority;
import dev.bookreports.storage.model.Report;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * Proxy sync channel {@code bookreports:sync} (SPECS.md §11 / §12): a lightweight ping so staff on other backends
 * behind the same Velocity/BungeeCord proxy learn about a new high-priority report immediately, rather than only on
 * their next queue refresh.
 *
 * <p>
 * The report itself is already visible/claimable network-wide as soon as it lands in a shared MySQL database —
 * {@code ReportDao} never filters by {@code server} — so this channel is a convenience, not a requirement. Sending
 * requires at least one connected player to relay the message through the proxy; that's a limitation of Bukkit's plugin
 * messaging API, not this class.
 */
public final class ProxySyncChannel implements PluginMessageListener, Listener {

    public static final String CHANNEL = "bookreports:sync";

    private final Plugin plugin;
    private final Logger logger;
    private final Consumer<RemoteReport> onRemoteReportCreated;

    public ProxySyncChannel(Plugin plugin, Logger logger, Consumer<RemoteReport> onRemoteReportCreated) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.onRemoteReportCreated = Objects.requireNonNull(onRemoteReportCreated, "onRemoteReportCreated");
    }

    public void register() {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
    }

    @EventHandler
    public void onReportCreated(ReportCreatedEvent event) {
        Report report = event.report();
        if (report.priority() != Priority.HIGH) {
            return;
        }
        Player relay = plugin.getServer().getOnlinePlayers().stream().findAny().orElse(null);
        if (relay == null) {
            return;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeLong(report.id());
            out.writeUTF(report.priority().name());
            out.writeUTF(report.targetName());
            out.writeUTF(report.categoryId());
            out.writeUTF(report.server());
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to encode proxy sync message", e);
            return;
        }
        relay.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) {
            return;
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            onRemoteReportCreated
                    .accept(new RemoteReport(in.readLong(), in.readUTF(), in.readUTF(), in.readUTF(), in.readUTF()));
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to decode proxy sync message", e);
        }
    }

    /** A HIGH-priority report announced by another backend on the network. */
    public record RemoteReport(long id, String priority, String targetName, String categoryId, String originServer) {
    }
}
