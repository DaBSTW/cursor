package dev.example.soledom.core.config;

import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import dev.example.soledom.blockbench.ImportSettings;
import dev.example.soledom.core.utils.NamespacedIds;

/**
 * Immutable snapshot of {@code config.yml}. Replaced as a whole on reload, so readers never see a half-applied
 * configuration.
 *
 * @param debug                  verbose logging
 * @param animationTickRate      ticks between animation frames (1-20)
 * @param maxAnimationDistance   players farther than this do not receive animation frames
 * @param animateOnlyNearPlayers global switch for distance culling
 * @param persistModels          keep models across restarts (models can opt out individually)
 * @param autoCleanup            remove models whose definition no longer exists, and stray parts
 * @param maxModelsPerChunk      spawn limit per chunk, 0 for unlimited
 * @param pack                   resource pack settings
 * @param blockbench             Blockbench project import settings
 */
public record SoledomConfig(boolean debug, int animationTickRate, double maxAnimationDistance,
        boolean animateOnlyNearPlayers, boolean persistModels, boolean autoCleanup, int maxModelsPerChunk,
        @NotNull PackSettings pack, @NotNull ImportSettings blockbench) {

    public static @NotNull SoledomConfig from(@NotNull FileConfiguration config, @NotNull Logger logger) {
        int tickRate = clamp(config.getInt("animation.tick-rate", 2), 1, 20, "animation.tick-rate", logger);
        double distance = config.getDouble("animation.max-distance", 64.0);
        if (distance <= 0) {
            logger.warning("animation.max-distance must be positive, using 64");
            distance = 64.0;
        }
        int maxPerChunk = clamp(config.getInt("models.max-per-chunk", 32), 0, 4096, "models.max-per-chunk", logger);

        ConfigurationSection rp = config.getConfigurationSection("resource-pack");
        PackSettings defaults = PackSettings.DEFAULT;
        PackSettings pack = defaults;
        if (rp != null) {
            int minFormat = Math.max(1, rp.getInt("min-format", defaults.minFormat()));
            int maxFormat = rp.getInt("max-format", defaults.maxFormat());
            if (maxFormat < minFormat) {
                logger.warning("resource-pack.max-format is lower than min-format, using min-format for both");
                maxFormat = minFormat;
            }
            String fileName = rp.getString("file-name", defaults.fileName());
            if (fileName == null || fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")) {
                logger.warning("resource-pack.file-name must be a plain file name, using " + defaults.fileName());
                fileName = defaults.fileName();
            }
            pack = new PackSettings(fileName, rp.getString("description", defaults.description()),
                    Math.max(1, rp.getInt("pack-format", minFormat)), minFormat, maxFormat,
                    rp.getBoolean("send-on-join", false), rp.getString("url", ""), rp.getString("sha1", ""),
                    rp.getBoolean("use-exported-hash", true), rp.getBoolean("required", false),
                    rp.getString("prompt", ""));
        }
        return new SoledomConfig(config.getBoolean("debug", false), tickRate, distance,
                config.getBoolean("animation.only-near-players", true),
                config.getBoolean("models.persist-across-restart", true),
                config.getBoolean("models.auto-cleanup", true), maxPerChunk, pack, blockbench(config, logger));
    }

    private static ImportSettings blockbench(FileConfiguration config, Logger logger) {
        ImportSettings defaults = ImportSettings.DEFAULT;
        String namespace = NamespacedIds.normalize(config.getString("blockbench.namespace", defaults.namespace())
                + ":x", defaults.namespace());
        String material = NamespacedIds.normalize(config.getString("blockbench.material", defaults.material()),
                "minecraft");
        if (namespace == null || material == null) {
            logger.warning("blockbench.namespace/material are not valid ids, using the defaults");
            namespace = defaults.namespace() + ":x";
            material = defaults.material();
        }
        int start = clamp(config.getInt("blockbench.custom-model-data-start", defaults.customModelDataStart()), 1,
                16_000_000, "blockbench.custom-model-data-start", logger);
        return new ImportSettings(config.getBoolean("blockbench.enabled", true), NamespacedIds.namespace(namespace),
                material, start);
    }

    private static int clamp(int value, int min, int max, String key, Logger logger) {
        if (value < min || value > max) {
            int clamped = Math.max(min, Math.min(max, value));
            logger.warning(key + " must be between " + min + " and " + max + ", using " + clamped);
            return clamped;
        }
        return value;
    }
}
