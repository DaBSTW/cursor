package dev.example.soledom.blockbench;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Converts a Blockbench project ({@code .bbmodel}) into Soledom content. Pure Java: no server calls.
 *
 * <p>Mapping:</p>
 * <ul>
 *   <li>Every bone (group) becomes a part. Bones with cubes become {@code ITEM_DISPLAY} parts backed by a
 *       generated item model holding just that bone's cubes, positioned around the bone pivot. Bones without
 *       cubes become {@code GROUP} parts. Cubes outside any bone go into a synthetic {@code root} bone.</li>
 *   <li>The bone tree becomes the part hierarchy ({@code parent}); bone pivots and rotations become part
 *       {@code position}/{@code rotation}, so animations behave like in Blockbench.</li>
 *   <li>Animations become Soledom animations with the same keyframes, converted from Blockbench's animation
 *       axes to model axes.</li>
 *   <li>Embedded textures are written into the pack.</li>
 * </ul>
 *
 * <p>Minecraft limits (documented as LIMITATIONS): cube rotations must be a single axis in 22.5° steps (other
 * rotations are snapped, with a warning: rotate bones instead), item model coordinates must stay within
 * [-16, 32] (large bones are shrunk and scaled back up through the model's display transform, up to 4×), and
 * mesh elements, Molang expressions and bezier/catmull-rom curves are not supported.</p>
 */
public final class BbModelConverter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String[] FACES = {"north", "east", "south", "west", "up", "down"};
    private static final double[] ALLOWED_ANGLES = {-45, -22.5, 0, 22.5, 45};
    /** Max distance (in model pixels) of geometry from the model centre that Minecraft accepts. */
    private static final double MAX_EXTENT = 24.0;
    private static final double MAX_DISPLAY_SCALE = 4.0;

    /**
     * Converts one project.
     *
     * @param project          parsed {@code .bbmodel} JSON
     * @param fileName         file name, used for the model id and messages
     * @param settings         namespace and material
     * @param firstCustomModelData first custom model data value this model may use
     */
    public @NotNull Conversion convert(@NotNull JsonObject project, @NotNull String fileName,
            @NotNull ImportSettings settings, int firstCustomModelData) {
        return new Run(project, fileName, settings, firstCustomModelData).convert();
    }

    /**
     * Output of a conversion.
     *
     * @param model         Soledom model JSON (validated later by the regular model loader)
     * @param animations    Soledom animation JSON by local animation name
     * @param packFiles     generated pack files (item models, textures) by ZIP path
     * @param customModelDataUsed number of custom model data values consumed
     * @param warnings      conversion warnings
     */
    public record Conversion(@NotNull JsonObject model, @NotNull Map<String, JsonObject> animations,
            @NotNull Map<String, byte[]> packFiles, int customModelDataUsed, @NotNull List<String> warnings) {
    }

    /** Sanitises any name into {@code [a-z0-9_]+}. */
    static @NotNull String sanitize(@Nullable String raw, @NotNull String fallback) {
        String value = raw == null ? "" : raw.toLowerCase(Locale.ROOT).trim()
                .replaceAll("\\.(bbmodel|png)$", "")
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        return value.isEmpty() ? fallback : value;
    }

    // ------------------------------------------------------------------------------------------------------

    /** State of one conversion. */
    private static final class Run {

        private final JsonObject project;
        private final String fileName;
        private final ImportSettings settings;
        private final String modelPath;
        private final List<String> warnings = new ArrayList<>();
        private final Map<String, byte[]> packFiles = new TreeMap<>();
        private final Map<String, JsonObject> elements = new HashMap<>();
        private final Map<String, JsonObject> groupsByUuid = new HashMap<>();
        private final List<Bone> bones = new ArrayList<>();
        private final Map<String, Bone> bonesByUuid = new HashMap<>();
        private final Set<String> usedPartIds = new HashSet<>();
        private final List<Texture> textures = new ArrayList<>();
        private final double[] shift;
        private int nextCustomModelData;
        private final int firstCustomModelData;

        Run(JsonObject project, String fileName, ImportSettings settings, int firstCustomModelData) {
            this.project = project;
            this.fileName = fileName;
            this.settings = settings;
            this.modelPath = sanitize(fileName, "model");
            this.firstCustomModelData = firstCustomModelData;
            this.nextCustomModelData = firstCustomModelData;
            String format = string(object(project, "meta"), "model_format", "free");
            // Java block projects place the block in 0..16; every other format is centred on the origin.
            this.shift = format.equals("java_block") ? new double[] {-8, 0, -8} : new double[] {0, 0, 0};
        }

        Conversion convert() {
            readTextures();
            for (JsonElement element : array(project, "elements")) {
                if (element.isJsonObject()) {
                    JsonObject cube = element.getAsJsonObject();
                    String type = string(cube, "type", "cube");
                    if (!type.equals("cube")) {
                        warn("element '" + string(cube, "name", "?") + "' is a " + type
                                + ", only cubes can be converted; skipped");
                        continue;
                    }
                    elements.put(string(cube, "uuid", ""), cube);
                }
            }
            for (JsonElement group : array(project, "groups")) {
                // Blockbench 5 keeps group properties in a top-level list referenced from the outliner.
                if (group.isJsonObject()) {
                    groupsByUuid.put(string(group.getAsJsonObject(), "uuid", ""), group.getAsJsonObject());
                }
            }
            readOutliner();

            JsonArray parts = new JsonArray();
            double[] bounds = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE,
                    -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
            for (Bone bone : bones) {
                parts.add(partJson(bone, bounds));
            }

            JsonObject model = new JsonObject();
            model.addProperty("format_version", 1);
            String id = settings.namespace() + ":" + modelPath;
            model.addProperty("id", id);
            model.addProperty("display_name", string(project, "name", modelPath));
            model.add("root", hitbox(bounds));
            model.add("parts", parts);

            Map<String, JsonObject> animations = convertAnimations();
            JsonObject animationRefs = new JsonObject();
            animations.forEach((name, json) -> animationRefs.addProperty(name, json.get("id").getAsString()));
            model.add("animations", animationRefs);
            if (animations.containsKey("idle")) {
                model.addProperty("default_animation", "idle");
            }
            JsonObject metadata = new JsonObject();
            metadata.addProperty("source", "blockbench:" + fileName);
            model.add("metadata", metadata);
            return new Conversion(model, animations, packFiles, nextCustomModelData - firstCustomModelData,
                    List.copyOf(warnings));
        }

        // ---------------------------------------------------------------- textures

        private void readTextures() {
            JsonObject resolution = object(project, "resolution");
            double defaultWidth = number(resolution, "width", 16);
            double defaultHeight = number(resolution, "height", 16);
            Set<String> names = new HashSet<>();
            int index = 0;
            for (JsonElement element : array(project, "textures")) {
                JsonObject texture = element.getAsJsonObject();
                String name = unique(sanitize(string(texture, "name", "texture_" + index), "texture_" + index), names);
                String resource = settings.namespace() + ":item/" + modelPath + "/" + name;
                double uvWidth = number(texture, "uv_width", defaultWidth);
                double uvHeight = number(texture, "uv_height", defaultHeight);
                textures.add(new Texture(resource, uvWidth, uvHeight));
                String source = string(texture, "source", "");
                byte[] png = decodePng(source);
                String path = "assets/" + settings.namespace() + "/textures/item/" + modelPath + "/" + name + ".png";
                if (png != null) {
                    packFiles.put(path, png);
                } else {
                    warn("texture '" + string(texture, "name", name) + "' is not embedded in the project; place it at"
                            + " pack/" + path + " (or save the project with \"Embed textures\")");
                }
                index++;
            }
        }

        private static byte @Nullable [] decodePng(String source) {
            int comma = source.indexOf(',');
            if (!source.startsWith("data:image/png;base64,") || comma < 0) {
                return null;
            }
            try {
                return Base64.getDecoder().decode(source.substring(comma + 1).trim());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        // ---------------------------------------------------------------- bones

        private void readOutliner() {
            Bone looseRoot = null;
            for (JsonElement node : array(project, "outliner")) {
                if (node.isJsonPrimitive()) {
                    // Cube at the top level, outside any bone.
                    if (looseRoot == null) {
                        looseRoot = newBone("root", null, new double[] {0, 0, 0}, new double[] {0, 0, 0});
                    }
                    addCube(looseRoot, node.getAsString());
                } else if (node.isJsonObject()) {
                    readGroup(node.getAsJsonObject(), null);
                }
            }
        }

        private void readGroup(JsonObject node, @Nullable Bone parent) {
            String uuid = string(node, "uuid", "");
            JsonObject props = groupsByUuid.getOrDefault(uuid, node);
            if (props.has("export") && !props.get("export").getAsBoolean()) {
                return;
            }
            double[] origin = add(vector(props, "origin", 0), shift);
            double[] rotation = vector(props, "rotation", 0);
            Bone bone = newBone(string(props, "name", "bone"), parent, origin, rotation);
            bonesByUuid.put(uuid, bone);
            for (JsonElement child : array(node, "children")) {
                if (child.isJsonPrimitive()) {
                    addCube(bone, child.getAsString());
                } else if (child.isJsonObject()) {
                    readGroup(child.getAsJsonObject(), bone);
                }
            }
        }

        private Bone newBone(String name, @Nullable Bone parent, double[] origin, double[] rotation) {
            Bone bone = new Bone(unique(sanitize(name, "bone"), usedPartIds), parent, origin, rotation);
            bones.add(bone);
            return bone;
        }

        private void addCube(Bone bone, String uuid) {
            JsonObject cube = elements.get(uuid);
            if (cube == null) {
                return;
            }
            if (cube.has("visibility") && !cube.get("visibility").getAsBoolean()) {
                return;
            }
            if (cube.has("export") && !cube.get("export").getAsBoolean()) {
                return;
            }
            bone.cubes.add(cube);
        }

        // ---------------------------------------------------------------- parts

        private JsonObject partJson(Bone bone, double[] bounds) {
            JsonObject part = new JsonObject();
            part.addProperty("id", bone.id);
            double[] parentOrigin = bone.parent == null ? new double[] {0, 0, 0} : bone.parent.origin;
            part.add("position", numbers(
                    (bone.origin[0] - parentOrigin[0]) / 16.0,
                    (bone.origin[1] - parentOrigin[1]) / 16.0,
                    (bone.origin[2] - parentOrigin[2]) / 16.0));
            if (bone.rotation[0] != 0 || bone.rotation[1] != 0 || bone.rotation[2] != 0) {
                part.add("rotation", numbers(bone.rotation));
            }
            if (bone.parent != null) {
                part.addProperty("parent", bone.parent.id);
            }
            if (bone.cubes.isEmpty()) {
                part.addProperty("type", "GROUP");
                return part;
            }
            String resource = settings.namespace() + ":item/" + modelPath + "/" + bone.id;
            String path = "assets/" + settings.namespace() + "/models/item/" + modelPath + "/" + bone.id + ".json";
            packFiles.put(path, (GSON.toJson(itemModel(bone, bounds)) + "\n").getBytes(StandardCharsets.UTF_8));
            part.addProperty("type", "ITEM_DISPLAY");
            part.addProperty("material", settings.material());
            part.addProperty("custom_model_data", nextCustomModelData++);
            part.addProperty("model", resource);
            part.addProperty("display_transform", "FIXED");
            return part;
        }

        /** Item model containing one bone's cubes, centred on the bone pivot. */
        private JsonObject itemModel(Bone bone, double[] bounds) {
            double extent = 0;
            for (JsonObject cube : bone.cubes) {
                double inflate = number(cube, "inflate", 0);
                double[] from = add(vector(cube, "from", 0), shift);
                double[] to = add(vector(cube, "to", 0), shift);
                for (int axis = 0; axis < 3; axis++) {
                    double lo = Math.min(from[axis], to[axis]) - inflate;
                    double hi = Math.max(from[axis], to[axis]) + inflate;
                    extent = Math.max(extent, Math.max(Math.abs(lo - bone.origin[axis]),
                            Math.abs(hi - bone.origin[axis])));
                    bounds[axis] = Math.min(bounds[axis], lo);
                    bounds[axis + 3] = Math.max(bounds[axis + 3], hi);
                }
            }
            double shrink = Math.max(1.0, extent / MAX_EXTENT);
            if (shrink > MAX_DISPLAY_SCALE) {
                warn("bone '" + bone.id + "' reaches " + Math.round(extent / 16 * 10) / 10.0
                        + " blocks from its pivot; Minecraft only allows ~6, it will render smaller. Split it into"
                        + " smaller bones");
            }

            JsonArray elementsJson = new JsonArray();
            Set<Integer> usedTextures = new java.util.TreeSet<>();
            for (JsonObject cube : bone.cubes) {
                elementsJson.add(element(cube, bone, shrink, usedTextures));
            }
            JsonObject texturesJson = new JsonObject();
            for (int index : usedTextures) {
                if (index < textures.size()) {
                    texturesJson.addProperty(String.valueOf(index), textures.get(index).resource);
                }
            }
            if (!usedTextures.isEmpty() && usedTextures.iterator().next() < textures.size()) {
                texturesJson.addProperty("particle", textures.get(usedTextures.iterator().next()).resource);
            }

            // Item displays are drawn rotated 180° around Y by the client; this display transform undoes it and
            // scales shrunk geometry back to its real size.
            double displayScale = Math.min(shrink, MAX_DISPLAY_SCALE);
            JsonObject fixed = new JsonObject();
            fixed.add("rotation", numbers(0, 180, 0));
            fixed.add("translation", numbers(0, 0, 0));
            fixed.add("scale", numbers(displayScale, displayScale, displayScale));
            JsonObject display = new JsonObject();
            display.add("fixed", fixed);

            JsonObject json = new JsonObject();
            json.addProperty("credit", "Converted from " + fileName + " by Soledom");
            json.add("textures", texturesJson);
            json.add("elements", elementsJson);
            json.add("display", display);
            return json;
        }

        private JsonObject element(JsonObject cube, Bone bone, double shrink, Set<Integer> usedTextures) {
            double inflate = number(cube, "inflate", 0);
            double[] from = add(vector(cube, "from", 0), shift);
            double[] to = add(vector(cube, "to", 0), shift);
            double[] lo = new double[3];
            double[] hi = new double[3];
            for (int axis = 0; axis < 3; axis++) {
                lo[axis] = toModel(Math.min(from[axis], to[axis]) - inflate, bone.origin[axis], shrink);
                hi[axis] = toModel(Math.max(from[axis], to[axis]) + inflate, bone.origin[axis], shrink);
            }
            JsonObject json = new JsonObject();
            String name = string(cube, "name", "cube");
            json.addProperty("name", name);
            json.add("from", numbers(lo));
            json.add("to", numbers(hi));

            JsonObject rotation = rotation(cube, bone, shrink, name);
            if (rotation != null) {
                json.add("rotation", rotation);
            }
            JsonObject faces = new JsonObject();
            JsonObject sourceFaces = object(cube, "faces");
            for (String face : FACES) {
                JsonObject f = object(sourceFaces, face);
                if (!f.has("texture") || f.get("texture").isJsonNull()) {
                    continue;
                }
                int texture = f.get("texture").getAsInt();
                Texture tex = texture < textures.size() ? textures.get(texture) : null;
                if (tex == null) {
                    continue;
                }
                double[] uv = vector4(f, "uv");
                JsonObject out = new JsonObject();
                out.add("uv", numbers(uv[0] * 16 / tex.uvWidth, uv[1] * 16 / tex.uvHeight,
                        uv[2] * 16 / tex.uvWidth, uv[3] * 16 / tex.uvHeight));
                out.addProperty("texture", "#" + texture);
                int faceRotation = (int) number(f, "rotation", 0);
                if (faceRotation != 0) {
                    out.addProperty("rotation", faceRotation);
                }
                faces.add(face, out);
                usedTextures.add(texture);
            }
            json.add("faces", faces);
            return json;
        }

        private @Nullable JsonObject rotation(JsonObject cube, Bone bone, double shrink, String name) {
            double[] rot = vector(cube, "rotation", 0);
            int axis = -1;
            int nonZero = 0;
            for (int i = 0; i < 3; i++) {
                if (Math.abs(rot[i]) > 1e-6) {
                    nonZero++;
                    if (axis < 0 || Math.abs(rot[i]) > Math.abs(rot[axis])) {
                        axis = i;
                    }
                }
            }
            if (axis < 0) {
                return null;
            }
            if (nonZero > 1) {
                warn("cube '" + name + "' in bone '" + bone.id + "' is rotated on several axes; Minecraft allows one,"
                        + " keeping " + "xyz".charAt(axis) + " (rotate the bone instead)");
            }
            double angle = snap(rot[axis]);
            if (Math.abs(angle - rot[axis]) > 1e-3) {
                warn("cube '" + name + "' in bone '" + bone.id + "' is rotated " + rot[axis] + "°; Minecraft only"
                        + " allows -45/-22.5/0/22.5/45, using " + angle + "° (rotate the bone instead)");
            }
            if (angle == 0) {
                return null;
            }
            double[] origin = add(vector(cube, "origin", 0), shift);
            JsonObject json = new JsonObject();
            json.addProperty("angle", angle);
            json.addProperty("axis", String.valueOf("xyz".charAt(axis)));
            json.add("origin", numbers(toModel(origin[0], bone.origin[0], shrink),
                    toModel(origin[1], bone.origin[1], shrink), toModel(origin[2], bone.origin[2], shrink)));
            return json;
        }

        private static double snap(double angle) {
            double best = 0;
            for (double allowed : ALLOWED_ANGLES) {
                if (Math.abs(allowed - angle) < Math.abs(best - angle)) {
                    best = allowed;
                }
            }
            return best;
        }

        private static double toModel(double coordinate, double pivot, double shrink) {
            return round(8 + (coordinate - pivot) / shrink);
        }

        private JsonObject hitbox(double[] bounds) {
            JsonObject root = new JsonObject();
            root.addProperty("type", "INTERACTION");
            if (bounds[0] == Double.MAX_VALUE) {
                root.addProperty("width", 1.0);
                root.addProperty("height", 1.0);
                return root;
            }
            double width = Math.max(bounds[3] - bounds[0], bounds[5] - bounds[2]) / 16.0;
            double height = bounds[4] / 16.0;
            root.addProperty("width", round(Math.clamp(width, 0.2, 64)));
            root.addProperty("height", round(Math.clamp(height, 0.2, 64)));
            return root;
        }

        // ---------------------------------------------------------------- animations

        private Map<String, JsonObject> convertAnimations() {
            Map<String, JsonObject> result = new LinkedHashMap<>();
            Set<String> names = new HashSet<>();
            for (JsonElement element : array(project, "animations")) {
                JsonObject animation = element.getAsJsonObject();
                String name = unique(sanitize(string(animation, "name", "animation"), "animation"), names);
                String loopMode = string(animation, "loop", "once");
                List<JsonObject> tracks = new ArrayList<>();
                double lastKeyframe = 0;
                boolean curveWarned = false;
                JsonObject animators = object(animation, "animators");
                for (Map.Entry<String, JsonElement> entry : animators.entrySet()) {
                    JsonObject animator = entry.getValue().getAsJsonObject();
                    if (!string(animator, "type", "bone").equals("bone")) {
                        continue;
                    }
                    Bone bone = bonesByUuid.get(entry.getKey());
                    if (bone == null) {
                        bone = boneByName(string(animator, "name", ""));
                    }
                    if (bone == null) {
                        warn("animation '" + name + "' animates unknown bone '" + string(animator, "name", "?")
                                + "'; skipped");
                        continue;
                    }
                    Map<String, List<double[]>> byChannel = new TreeMap<>();
                    Map<String, Boolean> allStep = new HashMap<>();
                    for (JsonElement kf : array(animator, "keyframes")) {
                        JsonObject keyframe = kf.getAsJsonObject();
                        String channel = string(keyframe, "channel", "");
                        if (!channel.equals("rotation") && !channel.equals("position") && !channel.equals("scale")) {
                            continue;
                        }
                        String interpolation = string(keyframe, "interpolation", "linear");
                        if ((interpolation.equals("catmullrom") || interpolation.equals("bezier")) && !curveWarned) {
                            warn("animation '" + name + "' uses " + interpolation + " keyframes; converted to linear");
                            curveWarned = true;
                        }
                        allStep.merge(channel, interpolation.equals("step"), Boolean::logicalAnd);
                        double time = number(keyframe, "time", 0);
                        double[] value = dataPoint(keyframe, channel, name);
                        byChannel.computeIfAbsent(channel, k -> new ArrayList<>())
                                .add(new double[] {time, value[0], value[1], value[2]});
                        lastKeyframe = Math.max(lastKeyframe, time);
                    }
                    for (Map.Entry<String, List<double[]>> channel : byChannel.entrySet()) {
                        tracks.add(track(bone.id, channel.getKey(), channel.getValue(),
                                allStep.getOrDefault(channel.getKey(), false)));
                    }
                }
                if (tracks.isEmpty()) {
                    warn("animation '" + name + "' has no convertible bone keyframes; skipped");
                    continue;
                }
                double length = Math.max(number(animation, "length", 0), lastKeyframe);
                boolean loop = loopMode.equals("loop") && length > 0;
                JsonObject json = new JsonObject();
                json.addProperty("id", settings.namespace() + ":" + modelPath + "/" + name);
                json.addProperty("duration", round(length));
                json.addProperty("loop", loop);
                json.addProperty("reset_on_end", !loopMode.equals("hold"));
                JsonArray tracksJson = new JsonArray();
                tracks.forEach(tracksJson::add);
                json.add("tracks", tracksJson);
                result.put(name, json);
            }
            return result;
        }

        private JsonObject track(String part, String channel, List<double[]> keyframes, boolean step) {
            keyframes.sort((a, b) -> Double.compare(a[0], b[0]));
            JsonArray array = new JsonArray();
            for (double[] k : keyframes) {
                JsonObject keyframe = new JsonObject();
                keyframe.addProperty("time", round(k[0]));
                keyframe.add("value", numbers(k[1], k[2], k[3]));
                array.add(keyframe);
            }
            JsonObject json = new JsonObject();
            json.addProperty("part", part);
            json.addProperty("channel", channel.equals("position") ? "translation" : channel);
            if (step) {
                json.addProperty("interpolation", "step");
            }
            json.add("keyframes", array);
            return json;
        }

        /**
         * Reads a keyframe value and converts it from Blockbench's animation axes: Blockbench subtracts X/Y
         * rotation and X position when it applies a keyframe to a bone, and positions are in pixels.
         */
        private double[] dataPoint(JsonObject keyframe, String channel, String animation) {
            JsonArray points = array(keyframe, "data_points");
            JsonObject point = points.isEmpty() || !points.get(0).isJsonObject() ? new JsonObject()
                    : points.get(0).getAsJsonObject();
            double fallback = channel.equals("scale") ? 1 : 0;
            double x = axisValue(point, "x", fallback, animation);
            double y = axisValue(point, "y", fallback, animation);
            double z = axisValue(point, "z", fallback, animation);
            return switch (channel) {
                case "rotation" -> new double[] {round(-x), round(-y), round(z)};
                case "position" -> new double[] {round(-x / 16), round(y / 16), round(z / 16)};
                default -> new double[] {round(x), round(y), round(z)};
            };
        }

        private double axisValue(JsonObject point, String axis, double fallback, String animation) {
            JsonElement value = point.get(axis);
            if (value == null || value.isJsonNull()) {
                return fallback;
            }
            String raw = value.getAsString().trim();
            if (raw.isEmpty()) {
                return fallback;
            }
            try {
                return Double.parseDouble(raw);
            } catch (NumberFormatException e) {
                warn("animation '" + animation + "' uses the expression '" + raw + "'; expressions are not"
                        + " supported, using " + fallback);
                return fallback;
            }
        }

        private @Nullable Bone boneByName(String name) {
            String id = sanitize(name, "");
            for (Bone bone : bones) {
                if (bone.id.equals(id)) {
                    return bone;
                }
            }
            return null;
        }

        private void warn(String message) {
            warnings.add(fileName + ": " + message);
        }
    }

    private record Texture(String resource, double uvWidth, double uvHeight) {
    }

    private static final class Bone {
        final String id;
        final @Nullable Bone parent;
        final double[] origin;
        final double[] rotation;
        final List<JsonObject> cubes = new ArrayList<>();

        Bone(String id, @Nullable Bone parent, double[] origin, double[] rotation) {
            this.id = id;
            this.parent = parent;
            this.origin = origin;
            this.rotation = rotation;
        }
    }

    // ---------------------------------------------------------------- JSON helpers

    private static String unique(String base, Set<String> used) {
        String candidate = base;
        int n = 2;
        while (!used.add(candidate)) {
            candidate = base + "_" + n++;
        }
        return candidate;
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement e = parent == null ? null : parent.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
    }

    private static JsonArray array(JsonObject parent, String key) {
        JsonElement e = parent.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }

    private static String string(JsonObject parent, String key, String fallback) {
        JsonElement e = parent.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
    }

    private static double number(JsonObject parent, String key, double fallback) {
        JsonElement e = parent.get(key);
        if (e == null || !e.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return e.getAsDouble();
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static double[] vector(JsonObject parent, String key, double fallback) {
        JsonArray a = array(parent, key);
        double[] v = {fallback, fallback, fallback};
        for (int i = 0; i < Math.min(3, a.size()); i++) {
            v[i] = a.get(i).getAsDouble();
        }
        return v;
    }

    private static double[] vector4(JsonObject parent, String key) {
        JsonArray a = array(parent, key);
        double[] v = new double[4];
        for (int i = 0; i < Math.min(4, a.size()); i++) {
            v[i] = a.get(i).getAsDouble();
        }
        return v;
    }

    private static double[] add(double[] a, double[] b) {
        return new double[] {a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    private static JsonArray numbers(double... values) {
        JsonArray array = new JsonArray();
        for (double v : values) {
            array.add(round(v));
        }
        return array;
    }

    /** Rounds to 5 decimals so generated files stay readable and stable across runs. */
    private static double round(double value) {
        double r = Math.round(value * 100_000d) / 100_000d;
        return r == 0 ? 0 : r;
    }
}
