package dev.example.soledom.loader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import dev.example.soledom.api.ModelDefinition;
import dev.example.soledom.api.ModelOptions;
import dev.example.soledom.api.PartDefinition;
import dev.example.soledom.api.PartType;
import dev.example.soledom.api.RootDefinition;
import dev.example.soledom.api.RootType;
import dev.example.soledom.api.TextStyle;
import dev.example.soledom.api.Vec3;
import dev.example.soledom.core.utils.NamespacedIds;

/**
 * Parses and validates model JSON documents into {@link ModelDefinition}s.
 *
 * <p>This class is pure Java (no Bukkit calls) so it can run off the main thread and in unit tests. Checks that
 * need the server — whether {@code minecraft:stick} is a real item, whether a block state string parses — are done
 * later by the registry installer.</p>
 */
public final class ModelLoader {

    /** Highest {@code format_version} this build understands. */
    public static final int FORMAT_VERSION = 1;
    public static final int MAX_PARTS = 64;
    /** Custom model data is stored as a float since 1.21.4; integers above 2^24 lose precision. */
    public static final int MAX_CUSTOM_MODEL_DATA = 16_777_216;

    private static final Pattern PART_ID = Pattern.compile("[a-z0-9_-]+");
    private static final Pattern ANIMATION_NAME = Pattern.compile("[a-z0-9_.-]+");
    private static final Set<String> DISPLAY_TRANSFORMS = Set.of("NONE", "THIRDPERSON_LEFTHAND",
            "THIRDPERSON_RIGHTHAND", "FIRSTPERSON_LEFTHAND", "FIRSTPERSON_RIGHTHAND", "HEAD", "GUI", "GROUND", "FIXED");
    private static final Set<String> BILLBOARDS = Set.of("FIXED", "VERTICAL", "HORIZONTAL", "CENTER");
    private static final Set<String> ALIGNMENTS = Set.of("CENTER", "LEFT", "RIGHT");

    private static final Set<String> MODEL_KEYS = Set.of("format_version", "id", "display_name", "root", "hitbox",
            "parts", "offset", "rotation", "scale", "animations", "default_animation", "tags", "metadata", "options",
            "$schema", "_comment");
    private static final Set<String> ROOT_KEYS = Set.of("type", "width", "height", "responsive", "marker");
    private static final Set<String> PART_KEYS = Set.of("id", "type", "material", "custom_model_data", "model",
            "item_model", "legacy_base_model", "block", "text", "text_style", "display_transform", "billboard",
            "position", "rotation", "scale", "glow", "glow_color", "brightness", "view_range", "shadow_radius",
            "shadow_strength", "_comment");
    private static final Set<String> OPTION_KEYS = Set.of("persist", "visible_by_default", "animate_only_near_players");
    private static final Set<String> TEXT_STYLE_KEYS = Set.of("background", "line_width", "shadowed", "see_through",
            "alignment");

    /** Parses a JSON string. Warnings (unknown fields etc.) are appended to {@code warnings}. */
    public @NotNull ModelDefinition parse(@NotNull String json, @NotNull String source, @NotNull List<String> warnings)
            throws ValidationException {
        JsonElement element;
        try {
            element = JsonParser.parseString(json);
        } catch (JsonParseException e) {
            throw new ValidationException(source, "malformed JSON: " + rootMessage(e));
        }
        if (!element.isJsonObject()) {
            throw new ValidationException(source, "top level must be a JSON object");
        }
        return parse(element.getAsJsonObject(), source, warnings);
    }

    public @NotNull ModelDefinition parse(@NotNull JsonObject json, @NotNull String source,
            @NotNull List<String> warnings) throws ValidationException {
        Problems problems = new Problems();
        JsonFields f = new JsonFields(problems);
        f.warnUnknownKeys(json, "", MODEL_KEYS);

        Integer formatVersion = f.optionalInt(json, "format_version", "");
        if (formatVersion != null && (formatVersion < 1 || formatVersion > FORMAT_VERSION)) {
            problems.error("format_version", "unsupported version " + formatVersion + " (this build reads up to "
                    + FORMAT_VERSION + ")");
        }

        String rawId = f.string(json, "id", "", true);
        String id = NamespacedIds.normalize(rawId, NamespacedIds.DEFAULT_NAMESPACE);
        if (rawId != null && id == null) {
            problems.error("id", "'" + rawId + "' is not a valid id; use lowercase namespace:path, e.g. soledom:golem");
        }
        String displayName = f.string(json, "display_name", "", false);

        RootDefinition root = parseRoot(json, f, problems);
        List<PartDefinition> parts = parseParts(json, f, problems, id);
        Vec3 offset = f.vec3(json, "offset", "", Vec3.ZERO, false);
        Vec3 rotation = f.vec3(json, "rotation", "", Vec3.ZERO, false);
        Vec3 scale = f.vec3(json, "scale", "", Vec3.ONE, true);

        Map<String, String> animations = parseAnimations(json, f, problems);
        String defaultAnimation = f.string(json, "default_animation", "", false);
        if (defaultAnimation != null && !animations.containsKey(defaultAnimation)) {
            problems.error("default_animation", "'" + defaultAnimation + "' is not declared in \"animations\"");
        }

        Set<String> tags = parseTags(json, f, problems);
        Map<String, String> metadata = parseMetadata(json, f, problems);
        ModelOptions options = parseOptions(json, f);

        warnings.addAll(problems.warnings().stream().map(w -> source + ": " + w).toList());
        problems.throwIfErrors(source);
        return new ModelDefinition(id, displayName == null ? id : displayName, root, parts, offset, rotation, scale,
                animations, defaultAnimation, tags, metadata, options);
    }

    private RootDefinition parseRoot(JsonObject json, JsonFields f, Problems problems) {
        // "hitbox" is accepted as an alias because it reads more naturally for interaction-only configs.
        String key = json.has("root") ? "root" : "hitbox";
        JsonObject root = f.object(json, key, "");
        if (root == null) {
            return RootDefinition.DEFAULT;
        }
        f.warnUnknownKeys(root, key, ROOT_KEYS);
        RootType type = f.enumValue(root, "type", key, RootType.class, RootType.INTERACTION);
        if (f.bool(root, "marker", key, false)) {
            type = RootType.MARKER;
        }
        double width = f.number(root, "width", key, 1.0);
        double height = f.number(root, "height", key, 1.0);
        if (width <= 0 || width > 64) {
            problems.error(key + ".width", "must be greater than 0 and at most 64");
        }
        if (height <= 0 || height > 64) {
            problems.error(key + ".height", "must be greater than 0 and at most 64");
        }
        boolean responsive = f.bool(root, "responsive", key, true);
        return new RootDefinition(type == null ? RootType.INTERACTION : type, (float) width, (float) height,
                responsive);
    }

    private List<PartDefinition> parseParts(JsonObject json, JsonFields f, Problems problems, @Nullable String modelId) {
        JsonArray array = f.array(json, "parts", "", true);
        List<PartDefinition> parts = new ArrayList<>();
        if (array == null) {
            return parts;
        }
        if (array.isEmpty()) {
            problems.error("parts", "a model needs at least one part");
        }
        if (array.size() > MAX_PARTS) {
            problems.error("parts", "too many parts (" + array.size() + "), the limit is " + MAX_PARTS);
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < array.size(); i++) {
            String path = "parts[" + i + "]";
            JsonElement element = array.get(i);
            if (!element.isJsonObject()) {
                problems.error(path, "expected an object");
                continue;
            }
            PartDefinition part = parsePart(element.getAsJsonObject(), path, f, problems, modelId);
            if (part != null) {
                if (!seen.add(part.id())) {
                    problems.error(path + ".id", "duplicate part id '" + part.id() + "'");
                }
                parts.add(part);
            }
        }
        return parts;
    }

    private @Nullable PartDefinition parsePart(JsonObject json, String path, JsonFields f, Problems problems,
            @Nullable String modelId) {
        f.warnUnknownKeys(json, path, PART_KEYS);
        String id = f.string(json, "id", path, true);
        if (id != null && !PART_ID.matcher(id).matches()) {
            problems.error(path + ".id", "'" + id + "' may only contain a-z, 0-9, '_' and '-'");
        }
        PartType type = f.enumValue(json, "type", path, PartType.class, null);

        String rawMaterial = f.string(json, "material", path, false);
        String material = normalizedId(rawMaterial, "minecraft", path + ".material", problems);
        Integer cmd = f.optionalInt(json, "custom_model_data", path);
        if (cmd != null && (cmd < 0 || cmd > MAX_CUSTOM_MODEL_DATA)) {
            problems.error(path + ".custom_model_data", "must be between 0 and " + MAX_CUSTOM_MODEL_DATA);
        }
        String model = normalizedId(f.string(json, "model", path, false), NamespacedIds.DEFAULT_NAMESPACE,
                path + ".model", problems);
        String itemModel = normalizedId(f.string(json, "item_model", path, false), NamespacedIds.DEFAULT_NAMESPACE,
                path + ".item_model", problems);
        String legacyBase = normalizedId(f.string(json, "legacy_base_model", path, false), "minecraft",
                path + ".legacy_base_model", problems);
        String block = f.string(json, "block", path, false);
        String text = f.string(json, "text", path, false);

        if (type == PartType.ITEM_DISPLAY) {
            if (rawMaterial == null) {
                problems.error(path + ".material", "ITEM_DISPLAY parts need a material, e.g. \"minecraft:stick\"");
            }
            if (model == null && cmd != null && cmd > 0 && modelId != null && id != null) {
                // Default resource location so pack export works without extra configuration.
                model = NamespacedIds.namespace(modelId) + ":item/" + NamespacedIds.path(modelId) + "/" + id;
            }
        } else if (type == PartType.BLOCK_DISPLAY && (block == null || block.isBlank())) {
            problems.error(path + ".block", "BLOCK_DISPLAY parts need a block, e.g. \"minecraft:stone\"");
        } else if (type == PartType.TEXT_DISPLAY && text == null) {
            problems.error(path + ".text", "TEXT_DISPLAY parts need a text (MiniMessage allowed)");
        }
        if (type != null && type != PartType.ITEM_DISPLAY && (material != null || cmd != null)) {
            problems.warn(path, "material/custom_model_data are only used by ITEM_DISPLAY parts");
        }

        TextStyle textStyle = parseTextStyle(json, path, f, problems);
        String transform = f.choice(json, "display_transform", path, DISPLAY_TRANSFORMS, "FIXED");
        String billboard = f.choice(json, "billboard", path, BILLBOARDS, "FIXED");
        Vec3 position = f.vec3(json, "position", path, Vec3.ZERO, false);
        Vec3 rotation = f.vec3(json, "rotation", path, Vec3.ZERO, false);
        Vec3 scale = f.vec3(json, "scale", path, Vec3.ONE, true);
        boolean glow = f.bool(json, "glow", path, false);
        Integer glowColor = f.color(json, "glow_color", path);
        PartDefinition.Brightness brightness = parseBrightness(json, path, f, problems);
        double viewRange = f.number(json, "view_range", path, 1.0);
        if (viewRange <= 0) {
            problems.error(path + ".view_range", "must be greater than 0");
        }
        double shadowRadius = f.number(json, "shadow_radius", path, 0.0);
        double shadowStrength = f.number(json, "shadow_strength", path, 1.0);
        if (shadowRadius < 0) {
            problems.error(path + ".shadow_radius", "must not be negative");
        }

        if (id == null || type == null) {
            return null;
        }
        return new PartDefinition(id, type, material, cmd, model, itemModel, legacyBase, block, text, textStyle,
                transform, billboard, position, rotation, scale, glow, glowColor, brightness, (float) viewRange,
                (float) shadowRadius, (float) shadowStrength);
    }

    private TextStyle parseTextStyle(JsonObject json, String path, JsonFields f, Problems problems) {
        String stylePath = path + ".text_style";
        JsonObject style = f.object(json, "text_style", path);
        if (style == null) {
            return TextStyle.DEFAULT;
        }
        f.warnUnknownKeys(style, stylePath, TEXT_STYLE_KEYS);
        Integer background = f.color(style, "background", stylePath);
        Integer lineWidth = f.optionalInt(style, "line_width", stylePath);
        if (lineWidth != null && lineWidth <= 0) {
            problems.error(stylePath + ".line_width", "must be greater than 0");
        }
        return new TextStyle(background, lineWidth == null ? TextStyle.DEFAULT.lineWidth() : lineWidth,
                f.bool(style, "shadowed", stylePath, false), f.bool(style, "see_through", stylePath, false),
                f.choice(style, "alignment", stylePath, ALIGNMENTS, "CENTER"));
    }

    private PartDefinition.Brightness parseBrightness(JsonObject json, String path, JsonFields f, Problems problems) {
        JsonObject obj = f.object(json, "brightness", path);
        if (obj == null) {
            return null;
        }
        String bPath = path + ".brightness";
        Integer block = f.optionalInt(obj, "block", bPath);
        Integer sky = f.optionalInt(obj, "sky", bPath);
        int b = block == null ? 15 : block;
        int s = sky == null ? 15 : sky;
        if (b < 0 || b > 15 || s < 0 || s > 15) {
            problems.error(bPath, "block and sky must be between 0 and 15");
        }
        return new PartDefinition.Brightness(b, s);
    }

    private Map<String, String> parseAnimations(JsonObject json, JsonFields f, Problems problems) {
        JsonObject obj = f.object(json, "animations", "");
        Map<String, String> result = new LinkedHashMap<>();
        if (obj == null) {
            return result;
        }
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            String path = "animations." + entry.getKey();
            if (!ANIMATION_NAME.matcher(entry.getKey()).matches()) {
                problems.error(path, "animation names may only contain a-z, 0-9, '_', '.' and '-'");
                continue;
            }
            JsonElement value = entry.getValue();
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().isBlank()) {
                problems.error(path, "expected an animation id or a path like \"animations/idle.json\"");
                continue;
            }
            result.put(entry.getKey(), value.getAsString().trim());
        }
        return result;
    }

    private Set<String> parseTags(JsonObject json, JsonFields f, Problems problems) {
        JsonArray array = f.array(json, "tags", "", false);
        Set<String> tags = new LinkedHashSet<>();
        if (array == null) {
            return tags;
        }
        for (int i = 0; i < array.size(); i++) {
            JsonElement e = array.get(i);
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
                problems.error("tags[" + i + "]", "expected a string");
            } else {
                tags.add(e.getAsString());
            }
        }
        return tags;
    }

    private Map<String, String> parseMetadata(JsonObject json, JsonFields f, Problems problems) {
        JsonObject obj = f.object(json, "metadata", "");
        Map<String, String> result = new LinkedHashMap<>();
        if (obj == null) {
            return result;
        }
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            if (!entry.getValue().isJsonPrimitive()) {
                problems.error("metadata." + entry.getKey(), "only strings, numbers and booleans are allowed");
            } else {
                result.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return result;
    }

    private ModelOptions parseOptions(JsonObject json, JsonFields f) {
        JsonObject obj = f.object(json, "options", "");
        if (obj == null) {
            return ModelOptions.DEFAULT;
        }
        f.warnUnknownKeys(obj, "options", OPTION_KEYS);
        return new ModelOptions(f.bool(obj, "persist", "options", ModelOptions.DEFAULT.persist()),
                f.bool(obj, "visible_by_default", "options", ModelOptions.DEFAULT.visibleByDefault()),
                f.bool(obj, "animate_only_near_players", "options", ModelOptions.DEFAULT.animateOnlyNearPlayers()));
    }

    private static @Nullable String normalizedId(@Nullable String raw, String defaultNamespace, String path,
            Problems problems) {
        if (raw == null) {
            return null;
        }
        String normalized = NamespacedIds.normalize(raw, defaultNamespace);
        if (normalized == null) {
            problems.error(path, "'" + raw + "' is not a valid resource id (lowercase namespace:path)");
        }
        return normalized;
    }

    static String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
