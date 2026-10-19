package dev.example.soledom.core.utils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import dev.example.soledom.api.PartDefinition;
import dev.example.soledom.api.PartType;
import dev.example.soledom.registry.RegisteredModel;

/**
 * Second validation pass for checks that need the running server: do item ids and block states exist in this
 * Minecraft version? Runs after the pure JSON validation in {@code loader}.
 */
public final class RuntimeValidator {

    private RuntimeValidator() {
    }

    /** Returns the models that passed; problems are appended to {@code errors}. */
    public static @NotNull Map<String, RegisteredModel> validate(@NotNull Map<String, RegisteredModel> models,
            @NotNull List<String> errors) {
        Map<String, RegisteredModel> valid = new LinkedHashMap<>();
        for (RegisteredModel model : models.values()) {
            boolean ok = true;
            for (PartDefinition part : model.definition().parts()) {
                String problem = check(part);
                if (problem != null) {
                    errors.add(model.id() + ": part '" + part.id() + "': " + problem);
                    ok = false;
                }
            }
            if (ok) {
                valid.put(model.id(), model);
            }
        }
        return valid;
    }

    /** @return a problem description, or {@code null} if the part is renderable on this server */
    public static String check(@NotNull PartDefinition part) {
        if (part.type() == PartType.ITEM_DISPLAY) {
            Material material = part.material() == null ? null : Material.matchMaterial(part.material());
            if (material == null) {
                return "unknown item '" + part.material() + "' on this Minecraft version";
            }
            if (!material.isItem() || material.isAir()) {
                return "'" + part.material() + "' is not an item";
            }
        } else if (part.type() == PartType.BLOCK_DISPLAY) {
            try {
                Bukkit.createBlockData(String.valueOf(part.block()));
            } catch (IllegalArgumentException e) {
                return "invalid block state '" + part.block() + "'";
            }
        }
        return null;
    }

    /** True if {@code itemId} names a block item; used to pick the vanilla parent of legacy item models. */
    public static boolean isBlockItem(@NotNull String itemId) {
        Material material = Material.matchMaterial(itemId);
        return material != null && material.isBlock();
    }
}
