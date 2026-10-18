package dev.pregen.listener;

import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;

/**
 * Runs an action once, after the server has loaded its worlds. The plugin enables at {@code STARTUP}, before any world
 * exists, so anything that needs worlds (resuming saved tasks) has to wait for this point.
 */
public final class StartupListener implements Listener {

    private final Runnable action;
    private final AtomicBoolean done = new AtomicBoolean();

    public StartupListener(Runnable action) {
        this.action = action;
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        if (event.getType() == ServerLoadEvent.LoadType.STARTUP) {
            runOnce();
        }
    }

    /** Runs the action unless it already ran. Safe to call from several triggers. */
    public void runOnce() {
        if (done.compareAndSet(false, true)) {
            action.run();
        }
    }
}
