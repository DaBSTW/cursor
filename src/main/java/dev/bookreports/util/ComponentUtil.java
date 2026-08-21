package dev.bookreports.util;

import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

/** Small helpers for wiring the book's clickable options to the hidden internal commands. */
public final class ComponentUtil {

    private ComponentUtil() {
    }

    /**
     * Builds the command a book link runs. The label itself (not a fallback namespace prefix, which Paper plugins no
     * longer get to customize — see {@code BookReportsPlugin#registerCommands}) is what keeps this out of collisions
     * with other plugins' commands.
     */
    public static String selectCommand(UUID sessionId, String actionId) {
        return "/bookreports-select " + sessionId + " " + actionId;
    }

    public static String targetCommand(UUID targetUuid) {
        return "/bookreports-target " + targetUuid;
    }

    /** Attaches a run-command click to an already-styled (locale-sourced) component. */
    public static Component clickable(Component base, String command) {
        return base.clickEvent(ClickEvent.runCommand(command));
    }

    /** Same as {@link #clickable(Component, String)}, plus a hover tooltip. */
    public static Component clickable(Component base, String command, Component hover) {
        return base.clickEvent(ClickEvent.runCommand(command)).hoverEvent(HoverEvent.showText(hover));
    }
}
