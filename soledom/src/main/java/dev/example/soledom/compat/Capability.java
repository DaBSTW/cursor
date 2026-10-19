package dev.example.soledom.compat;

/** Optional server features Soledom adapts to at runtime instead of hard-coding version numbers. */
public enum Capability {
    /** {@code ItemDisplay}/{@code BlockDisplay}/{@code TextDisplay} entities (1.19.4+). */
    DISPLAY_ENTITIES,
    /** {@code Interaction} entities usable as click hitboxes (1.19.4+). */
    INTERACTION_ENTITIES,
    /** Paper's experimental data component API ({@code io.papermc.paper.datacomponent}). */
    ITEM_COMPONENTS,
    /** Adventure {@code ResourcePackRequest}/{@code ResourcePackInfo} for sending packs (1.20.3+). */
    RESOURCE_PACK_INFO_API,
    /** The server is Folia: region threads, no global main thread. */
    FOLIA_SCHEDULER,
    /** Clients read {@code min_format}/{@code max_format} in pack.mcmeta (1.21.9+). */
    NEW_PACK_FORMAT,
    /** {@code ItemMeta#setCustomModelDataComponent} with float/string/flag lists (1.21.4+). */
    CUSTOM_MODEL_DATA_COMPONENT,
    /** {@code ItemMeta#setItemModel}, the {@code minecraft:item_model} component (1.21.2+). */
    ITEM_MODEL_COMPONENT,
    /** Client item model definitions under {@code assets/<ns>/items/} (1.21.4+). */
    ITEM_MODEL_DEFINITIONS
}
