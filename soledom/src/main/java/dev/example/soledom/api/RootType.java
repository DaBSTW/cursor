package dev.example.soledom.api;

/** Invisible anchor entity that owns a spawned model. */
public enum RootType {
    /** An {@code Interaction} entity: anchors the model and doubles as its click hitbox. */
    INTERACTION,
    /** A {@code Marker} entity: never sent to clients, no hitbox. Cheapest option for decorative models. */
    MARKER
}
