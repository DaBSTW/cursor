package dev.example.soledom.core.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.jetbrains.annotations.NotNull;

import dev.example.soledom.SoledomPlugin;
import dev.example.soledom.core.utils.NamespacedIds;
import dev.example.soledom.runtime.ModelEntityController;

/**
 * Resolves a model instance from a command argument:
 * {@code looking} (hitbox under the crosshair), {@code nearest}, an instance UUID, or a model id (nearest instance
 * of that model). Commands run on the sender's own thread, which owns the area around a player.
 */
final class TargetResolver {

    static final double NEAREST_RADIUS = 16;
    static final double MODEL_ID_RADIUS = 48;
    static final double LOOK_DISTANCE = 16;

    private final SoledomPlugin plugin;

    TargetResolver(@NotNull SoledomPlugin plugin) {
        this.plugin = plugin;
    }

    @NotNull Optional<ModelEntityController> resolve(@NotNull CommandSender sender, @NotNull String token) {
        String lower = token.toLowerCase(java.util.Locale.ROOT);
        try {
            UUID id = UUID.fromString(token);
            return plugin.manager().get(id);
        } catch (IllegalArgumentException notUuid) {
            // fall through to the player-relative forms
        }
        if (!(sender instanceof Player player)) {
            return Optional.empty();
        }
        return switch (lower) {
            case "looking" -> looking(player);
            case "nearest" -> plugin.manager().nearest(player.getLocation(), NEAREST_RADIUS, null);
            default -> {
                String modelId = NamespacedIds.normalize(token, NamespacedIds.DEFAULT_NAMESPACE);
                yield modelId == null ? Optional.empty()
                        : plugin.manager().nearest(player.getLocation(), MODEL_ID_RADIUS, modelId);
            }
        };
    }

    /** Default target when none is given: what the player looks at, else the nearest model. */
    @NotNull Optional<ModelEntityController> resolveDefault(@NotNull Player player) {
        Optional<ModelEntityController> looked = looking(player);
        return looked.isPresent() ? looked : plugin.manager().nearest(player.getLocation(), NEAREST_RADIUS, null);
    }

    private Optional<ModelEntityController> looking(Player player) {
        RayTraceResult hit = player.getWorld().rayTraceEntities(player.getEyeLocation(),
                player.getEyeLocation().getDirection(), LOOK_DISTANCE, 0.1,
                e -> !e.equals(player) && plugin.keys().isRoot(e));
        Entity entity = hit == null ? null : hit.getHitEntity();
        return entity == null ? Optional.empty() : plugin.manager().find(entity);
    }

    static @NotNull List<String> suggestions(@NotNull SoledomPlugin plugin) {
        List<String> list = new ArrayList<>(List.of("looking", "nearest"));
        list.addAll(plugin.registry().ids());
        return list;
    }
}
