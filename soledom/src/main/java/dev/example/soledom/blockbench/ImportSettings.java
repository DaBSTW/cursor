package dev.example.soledom.blockbench;

import org.jetbrains.annotations.NotNull;

/**
 * How Blockbench projects are turned into Soledom models ({@code config.yml → blockbench}).
 *
 * @param enabled               import {@code .bbmodel} files at all
 * @param namespace             namespace of generated model ids and resource locations
 * @param material              vanilla item every bone is rendered on (via custom model data)
 * @param customModelDataStart  first custom model data value handed out; one value per bone with cubes
 */
public record ImportSettings(boolean enabled, @NotNull String namespace, @NotNull String material,
        int customModelDataStart) {

    public static final ImportSettings DEFAULT = new ImportSettings(true, "soledom", "minecraft:paper", 20000);
}
