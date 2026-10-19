package dev.example.soledom.api;

/**
 * Behavioural flags of a model.
 *
 * @param persist                 keep spawned instances across chunk unloads and restarts
 * @param visibleByDefault        show the model to every player when spawned
 * @param animateOnlyNearPlayers  skip sending animation frames when nobody is within the configured distance
 */
public record ModelOptions(boolean persist, boolean visibleByDefault, boolean animateOnlyNearPlayers) {

    public static final ModelOptions DEFAULT = new ModelOptions(true, true, true);
}
