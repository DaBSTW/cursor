package dev.example.soledom.runtime;

/** Lifecycle of a model instance. */
public enum ModelState {
    /** Parts are spawned and the model is live. */
    ACTIVE,
    /** Root exists but parts are not spawned, e.g. because its definition is missing after a reload. */
    DORMANT,
    /** Removed for good, or unloaded with its chunk; the controller must not be used any more. */
    REMOVED
}
