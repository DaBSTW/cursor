package dev.example.soledom.runtime;

import java.util.UUID;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Every {@link NamespacedKey} Soledom stores in entity {@link PersistentDataContainer}s, plus typed accessors.
 *
 * <p>Root entities carry {@link #modelId} (and optionally {@link #attachedTo}, {@link #hidden},
 * {@link #animation}). Part entities carry {@link #owner} and {@link #partId}; they are never persisted to disk,
 * so their keys only matter while the server runs.</p>
 */
public final class ModelKeys {

    public final NamespacedKey modelId;
    public final NamespacedKey owner;
    public final NamespacedKey partId;
    public final NamespacedKey attachedTo;
    public final NamespacedKey hidden;
    public final NamespacedKey animation;

    public ModelKeys(@NotNull Plugin plugin) {
        this.modelId = new NamespacedKey(plugin, "model_id");
        this.owner = new NamespacedKey(plugin, "owner");
        this.partId = new NamespacedKey(plugin, "part_id");
        this.attachedTo = new NamespacedKey(plugin, "attached_to");
        this.hidden = new NamespacedKey(plugin, "hidden");
        this.animation = new NamespacedKey(plugin, "animation");
    }

    public boolean isRoot(@NotNull Entity entity) {
        return entity.getPersistentDataContainer().has(modelId, PersistentDataType.STRING);
    }

    public boolean isPart(@NotNull Entity entity) {
        return entity.getPersistentDataContainer().has(owner, PersistentDataType.STRING);
    }

    public @Nullable String modelId(@NotNull Entity root) {
        return root.getPersistentDataContainer().get(modelId, PersistentDataType.STRING);
    }

    public @Nullable UUID owner(@NotNull Entity part) {
        return parseUuid(part.getPersistentDataContainer().get(owner, PersistentDataType.STRING));
    }

    public @Nullable UUID attachedTo(@NotNull Entity root) {
        return parseUuid(root.getPersistentDataContainer().get(attachedTo, PersistentDataType.STRING));
    }

    public @Nullable String getString(@NotNull Entity entity, @NotNull NamespacedKey key) {
        return entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public void setString(@NotNull Entity entity, @NotNull NamespacedKey key, @Nullable String value) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (value == null) {
            pdc.remove(key);
        } else {
            pdc.set(key, PersistentDataType.STRING, value);
        }
    }

    public boolean getFlag(@NotNull Entity entity, @NotNull NamespacedKey key) {
        Byte value = entity.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return value != null && value != 0;
    }

    public void setFlag(@NotNull Entity entity, @NotNull NamespacedKey key, boolean value) {
        if (value) {
            entity.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        } else {
            entity.getPersistentDataContainer().remove(key);
        }
    }

    private static @Nullable UUID parseUuid(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
