package dev.example.soledom.compat;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

/**
 * Immutable set of capabilities detected on the running server.
 *
 * <p>Detection uses a handful of targeted class/method lookups; there is no broad reflection over server
 * internals. Client-side features (pack format, item model definitions) cannot be probed through the API, so
 * those are derived from the server version, which Paper keeps in lock-step with the protocol.</p>
 */
public final class FeatureFlags {

    private final Set<Capability> capabilities;

    public FeatureFlags(@NotNull Set<Capability> capabilities) {
        this.capabilities = capabilities.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(capabilities));
    }

    public static @NotNull FeatureFlags detect(@NotNull ServerVersion version) {
        EnumSet<Capability> found = EnumSet.noneOf(Capability.class);
        if (classExists("org.bukkit.entity.ItemDisplay")) {
            found.add(Capability.DISPLAY_ENTITIES);
        }
        if (classExists("org.bukkit.entity.Interaction")) {
            found.add(Capability.INTERACTION_ENTITIES);
        }
        if (classExists("io.papermc.paper.datacomponent.DataComponentTypes")) {
            found.add(Capability.ITEM_COMPONENTS);
        }
        if (classExists("net.kyori.adventure.resource.ResourcePackRequest")) {
            found.add(Capability.RESOURCE_PACK_INFO_API);
        }
        if (classExists("io.papermc.paper.threadedregions.RegionizedServer")) {
            found.add(Capability.FOLIA_SCHEDULER);
        }
        if (methodExists("org.bukkit.inventory.meta.ItemMeta", "setCustomModelDataComponent")) {
            found.add(Capability.CUSTOM_MODEL_DATA_COMPONENT);
        }
        if (methodExists("org.bukkit.inventory.meta.ItemMeta", "setItemModel")) {
            found.add(Capability.ITEM_MODEL_COMPONENT);
        }
        // An unknown version is assumed to be modern: every supported line (1.21+) is at least that new.
        boolean unknown = !version.isKnown();
        if (unknown || version.isAtLeast(1, 21, 4)) {
            found.add(Capability.ITEM_MODEL_DEFINITIONS);
        }
        if (unknown || version.isAtLeast(1, 21, 9)) {
            found.add(Capability.NEW_PACK_FORMAT);
        }
        return new FeatureFlags(found);
    }

    public boolean has(@NotNull Capability capability) {
        return capabilities.contains(capability);
    }

    public @NotNull Set<Capability> all() {
        return capabilities;
    }

    public boolean hasDisplayEntities() {
        return has(Capability.DISPLAY_ENTITIES);
    }

    public boolean hasInteractionEntities() {
        return has(Capability.INTERACTION_ENTITIES);
    }

    public boolean hasItemComponents() {
        return has(Capability.ITEM_COMPONENTS);
    }

    public boolean hasFolia() {
        return has(Capability.FOLIA_SCHEDULER);
    }

    public boolean hasResourcePackInfoApi() {
        return has(Capability.RESOURCE_PACK_INFO_API);
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, FeatureFlags.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private static boolean methodExists(String className, String method) {
        try {
            Class<?> type = Class.forName(className, false, FeatureFlags.class.getClassLoader());
            for (var m : type.getMethods()) {
                if (m.getName().equals(method)) {
                    return true;
                }
            }
            return false;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return capabilities.toString();
    }
}
