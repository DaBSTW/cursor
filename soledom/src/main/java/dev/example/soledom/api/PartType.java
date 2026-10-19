package dev.example.soledom.api;

/** Kind of display entity a model part is rendered with. */
public enum PartType {
    ITEM_DISPLAY,
    BLOCK_DISPLAY,
    TEXT_DISPLAY,
    /**
     * Invisible transform node with no entity. Children (parts whose {@code parent} is this part) inherit its
     * position, rotation and scale, so animating a group moves everything below it. Used for Blockbench bones
     * that hold no cubes themselves.
     */
    GROUP
}
