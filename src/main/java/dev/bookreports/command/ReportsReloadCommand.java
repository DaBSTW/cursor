package dev.bookreports.command;

import dev.bookreports.config.BookReportsConfig;
import dev.bookreports.config.ConfigManager;
import dev.bookreports.config.ConfigurationException;
import dev.bookreports.config.LocaleManager;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/** {@code /reportsreload} — reloads config and locale without touching active book sessions. */
public final class ReportsReloadCommand implements CommandExecutor {

    private final ConfigManager configManager;
    private final LocaleManager localeManager;
    private final Logger logger;

    public ReportsReloadCommand(ConfigManager configManager, LocaleManager localeManager, Logger logger) {
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        this.localeManager = Objects.requireNonNull(localeManager, "localeManager");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            BookReportsConfig reloaded = configManager.load();
            localeManager.load(reloaded.locale());
            sender.sendMessage(localeManager.get("command.reload-success"));
        } catch (ConfigurationException e) {
            logger.log(Level.SEVERE, "Reload rejected: invalid configuration", e);
            sender.sendMessage(
                    localeManager.get("command.reload-failed", Map.of("reason", String.valueOf(e.getMessage()))));
        }
        return true;
    }
}
