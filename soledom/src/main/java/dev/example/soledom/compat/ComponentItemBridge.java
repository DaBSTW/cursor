package dev.example.soledom.compat;

import java.util.List;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.jetbrains.annotations.NotNull;

/**
 * Calls into item APIs added in 1.21.2/1.21.4. Kept in its own class so older servers never load it: the JVM
 * resolves these references lazily, only when a method here is first executed.
 */
final class ComponentItemBridge {

    private ComponentItemBridge() {
    }

    /** 1.21.4+: custom model data is a list of floats; vanilla item definitions read index 0. */
    static void applyCustomModelData(@NotNull ItemMeta meta, int value) {
        CustomModelDataComponent component = meta.getCustomModelDataComponent();
        component.setFloats(List.of((float) value));
        meta.setCustomModelDataComponent(component);
    }

    /** 1.21.2+: {@code minecraft:item_model} component. */
    static void applyItemModel(@NotNull ItemMeta meta, @NotNull NamespacedKey key) {
        meta.setItemModel(key);
    }
}
