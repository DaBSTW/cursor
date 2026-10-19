package dev.example.soledom.compat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Marker;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import dev.example.soledom.api.PartDefinition;
import dev.example.soledom.api.RootDefinition;
import dev.example.soledom.api.RootType;
import dev.example.soledom.api.TextStyle;

/** {@link VersionAdapter} for Paper 1.21 and newer, choosing code paths by detected {@link Capability}. */
public final class ModernVersionAdapter implements VersionAdapter {

    /** Vanilla clamps interpolation/teleport durations to this many ticks. */
    private static final int MAX_DURATION_TICKS = 59;

    private final ServerVersion version;
    private final FeatureFlags features;
    private final Logger logger;
    private volatile boolean customModelDataWarned;

    public ModernVersionAdapter(@NotNull ServerVersion version, @NotNull FeatureFlags features,
            @NotNull Logger logger) {
        this.version = version;
        this.features = features;
        this.logger = logger;
    }

    @Override
    public @NotNull ServerVersion version() {
        return version;
    }

    @Override
    public @NotNull FeatureFlags features() {
        return features;
    }

    @Override
    public @NotNull Entity spawnRoot(@NotNull Location location, @NotNull RootDefinition root,
            @NotNull Consumer<Entity> configure) {
        World world = requireWorld(location);
        if (root.type() == RootType.INTERACTION && features.hasInteractionEntities()) {
            return world.spawn(location, Interaction.class, interaction -> {
                interaction.setInteractionWidth(root.width());
                interaction.setInteractionHeight(root.height());
                interaction.setResponsive(root.responsive());
                configure.accept(interaction);
            });
        }
        return world.spawn(location, Marker.class, configure::accept);
    }

    @Override
    public @Nullable Display spawnPart(@NotNull Location location, @NotNull PartDefinition part,
            @NotNull Transformation transformation, @NotNull Consumer<Display> configure) {
        if (!features.hasDisplayEntities()) {
            return null;
        }
        World world = requireWorld(location);
        Consumer<Display> common = display -> {
            applyCommon(display, part, transformation);
            configure.accept(display);
        };
        return switch (part.type()) {
            case ITEM_DISPLAY -> world.spawn(location, ItemDisplay.class, display -> {
                display.setItemStack(createItem(part));
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.valueOf(part.displayTransform()));
                common.accept(display);
            });
            case BLOCK_DISPLAY -> {
                BlockData data = parseBlock(part.block());
                yield data == null ? null : world.spawn(location, BlockDisplay.class, display -> {
                    display.setBlock(data);
                    common.accept(display);
                });
            }
            case TEXT_DISPLAY -> world.spawn(location, TextDisplay.class, display -> {
                applyText(display, part);
                common.accept(display);
            });
        };
    }

    @Override
    public @NotNull ItemStack createItem(@NotNull PartDefinition part) {
        Material material = part.material() == null ? null : Material.matchMaterial(part.material());
        if (material == null || !material.isItem() || material.isAir()) {
            material = Material.BARRIER;
        }
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        if (part.customModelData() != null) {
            applyCustomModelData(meta, part.customModelData());
        }
        if (part.itemModel() != null && features.has(Capability.ITEM_MODEL_COMPONENT)) {
            NamespacedKey key = NamespacedKey.fromString(part.itemModel());
            if (key != null) {
                try {
                    ComponentItemBridge.applyItemModel(meta, key);
                } catch (LinkageError e) {
                    logger.log(Level.FINE, "item_model component unavailable", e);
                }
            }
        }
        stack.setItemMeta(meta);
        return stack;
    }

    @SuppressWarnings("deprecation") // Integer custom model data is the only option before 1.21.4.
    private void applyCustomModelData(ItemMeta meta, int value) {
        if (features.has(Capability.CUSTOM_MODEL_DATA_COMPONENT)) {
            try {
                ComponentItemBridge.applyCustomModelData(meta, value);
                return;
            } catch (LinkageError e) {
                logger.log(Level.FINE, "Custom model data component unavailable, using legacy integer", e);
            }
        }
        try {
            meta.setCustomModelData(value);
        } catch (LinkageError e) {
            if (!customModelDataWarned) {
                customModelDataWarned = true;
                logger.warning("This server exposes no custom model data API Soledom knows; items are shown without"
                        + " custom models. Please report your Paper version.");
            }
        }
    }

    @Override
    public void applyTransformation(@NotNull Display display, @NotNull Transformation transformation,
            int interpolationTicks) {
        // Delay 0 + duration N restarts client-side interpolation from the current pose to the new one.
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(Math.clamp(interpolationTicks, 0, MAX_DURATION_TICKS));
        display.setTransformation(transformation);
    }

    @Override
    public void sendResourcePack(@NotNull Player player, @NotNull String url, @Nullable String sha1Hex,
            @Nullable Component prompt, boolean required) {
        String hash = sha1Hex == null || sha1Hex.isBlank() ? "" : sha1Hex.trim().toLowerCase(Locale.ROOT);
        if (features.hasResourcePackInfoApi()) {
            UUID id = UUID.nameUUIDFromBytes(("soledom:" + url).getBytes(StandardCharsets.UTF_8));
            ResourcePackRequest.Builder request = ResourcePackRequest.resourcePackRequest()
                    .packs(ResourcePackInfo.resourcePackInfo(id, URI.create(url), hash))
                    .required(required)
                    .replace(false);
            if (prompt != null) {
                request.prompt(prompt);
            }
            player.sendResourcePacks(request.build());
            return;
        }
        byte[] bytes = hash.length() == 40 ? HexFormat.of().parseHex(hash) : null;
        player.setResourcePack(url, bytes, prompt, required);
    }

    private void applyCommon(Display display, PartDefinition part, Transformation transformation) {
        display.setPersistent(false);
        display.setBillboard(Display.Billboard.valueOf(part.billboard()));
        display.setViewRange(part.viewRange());
        display.setShadowRadius(part.shadowRadius());
        display.setShadowStrength(part.shadowStrength());
        if (part.brightness() != null) {
            display.setBrightness(new Display.Brightness(part.brightness().block(), part.brightness().sky()));
        }
        display.setGlowing(part.glow());
        if (part.glowColor() != null) {
            display.setGlowColorOverride(Color.fromRGB(part.glowColor() & 0xFFFFFF));
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(0);
        display.setTransformation(transformation);
    }

    private void applyText(TextDisplay display, PartDefinition part) {
        TextStyle style = part.textStyle();
        display.text(MiniMessage.miniMessage().deserialize(part.text() == null ? "" : part.text()));
        display.setLineWidth(style.lineWidth());
        display.setShadowed(style.shadowed());
        display.setSeeThrough(style.seeThrough());
        display.setAlignment(TextDisplay.TextAlignment.valueOf(style.alignment()));
        if (style.background() != null) {
            display.setBackgroundColor(Color.fromARGB(style.background()));
        }
    }

    private @Nullable BlockData parseBlock(@Nullable String block) {
        if (block == null) {
            return null;
        }
        try {
            return Bukkit.createBlockData(block);
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid block state '" + block + "': " + e.getMessage());
            return null;
        }
    }

    private static World requireWorld(Location location) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("location has no world");
        }
        return world;
    }
}
