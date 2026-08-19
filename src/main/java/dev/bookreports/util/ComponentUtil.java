package dev.bookreports.util;

import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

/** Small helpers for wiring the book's clickable options to the hidden internal commands. */
public final class ComponentUtil {

    private ComponentUtil() {
    }

    /** Builds the fully-qualified, non-tab-completable command a book link runs. */
    public static String selectCommand(UUID sessionId, String actionId) {
        return "/breport:select " + sessionId + " " + actionId;
    }

    public static String targetCommand(UUID targetUuid) {
        return "/breport:target " + targetUuid;
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
