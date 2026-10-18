package dev.pregen.selection;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * One selection per command sender, so two admins configuring at the same time never overwrite each other. Player
 * entries are dropped on quit to keep the map bounded by the online player count.
 */
public final class SelectionRegistry {

    private final Map<UUID, Selection> selections = new ConcurrentHashMap<>();

    /** Returns the sender's selection, creating it from {@code defaults} on first use. */
    public Selection get(UUID owner, Function<UUID, Selection> defaults) {
        return selections.computeIfAbsent(owner, defaults);
    }

    public Selection update(UUID owner, Function<UUID, Selection> defaults, UnaryOperator<Selection> change) {
        return selections.compute(owner,
                (key, current) -> change.apply(current == null ? defaults.apply(key) : current));
    }

    public void forget(UUID owner) {
        selections.remove(owner);
    }

    public void clear() {
        selections.clear();
    }
}
