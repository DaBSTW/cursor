package dev.example.soledom.loader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import dev.example.soledom.animation.Channel;
import dev.example.soledom.animation.Interpolation;
import dev.example.soledom.animation.Keyframe;
import dev.example.soledom.animation.TransformTrack;
import dev.example.soledom.api.AnimationDefinition;
import dev.example.soledom.api.Vec3;
import dev.example.soledom.core.utils.NamespacedIds;

/**
 * Parses and validates animation JSON documents.
 *
 * <p>Keyframes may give an absolute {@code time} (seconds) or a {@code duration}: the seconds it takes to reach
 * this keyframe from the previous one. The two styles can be mixed within one track.</p>
 */
public final class AnimationLoader {

    public static final double MAX_DURATION = 3600.0;

    private static final Set<String> ANIMATION_KEYS = Set.of("format_version", "id", "duration", "loop", "priority",
            "reset_on_end", "tracks", "$schema", "_comment");
    private static final Set<String> TRACK_KEYS = Set.of("part", "channel", "interpolation", "keyframes", "_comment");
    private static final Set<String> KEYFRAME_KEYS = Set.of("time", "duration", "value", "_comment");

    public @NotNull AnimationDefinition parse(@NotNull String json, @NotNull String source,
            @NotNull List<String> warnings) throws ValidationException {
        JsonElement element;
        try {
            element = JsonParser.parseString(json);
        } catch (JsonParseException e) {
            throw new ValidationException(source, "malformed JSON: " + ModelLoader.rootMessage(e));
        }
        if (!element.isJsonObject()) {
            throw new ValidationException(source, "top level must be a JSON object");
        }
        return parse(element.getAsJsonObject(), source, warnings);
    }

    public @NotNull AnimationDefinition parse(@NotNull JsonObject json, @NotNull String source,
            @NotNull List<String> warnings) throws ValidationException {
        Problems problems = new Problems();
        JsonFields f = new JsonFields(problems);
        f.warnUnknownKeys(json, "", ANIMATION_KEYS);

        String rawId = f.string(json, "id", "", true);
        String id = NamespacedIds.normalize(rawId, NamespacedIds.DEFAULT_NAMESPACE);
        if (rawId != null && id == null) {
            problems.error("id", "'" + rawId + "' is not a valid id; use lowercase namespace:path");
        }
        Double declaredDuration = f.optionalNumber(json, "duration", "");
        if (declaredDuration != null && (declaredDuration < 0 || declaredDuration > MAX_DURATION)) {
            problems.error("duration", "must be between 0 and " + MAX_DURATION + " seconds");
        }
        boolean loop = f.bool(json, "loop", "", false);
        Integer priority = f.optionalInt(json, "priority", "");
        boolean resetOnEnd = f.bool(json, "reset_on_end", "", true);

        List<TransformTrack> tracks = new ArrayList<>();
        JsonArray array = f.array(json, "tracks", "", true);
        double lastKeyframe = 0;
        if (array != null) {
            if (array.isEmpty()) {
                problems.error("tracks", "an animation needs at least one track");
            }
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < array.size(); i++) {
                String path = "tracks[" + i + "]";
                if (!array.get(i).isJsonObject()) {
                    problems.error(path, "expected an object");
                    continue;
                }
                TransformTrack track = parseTrack(array.get(i).getAsJsonObject(), path, f, problems);
                if (track == null) {
                    continue;
                }
                if (!seen.add(track.part() + "/" + track.channel())) {
                    problems.error(path, "part '" + track.part() + "' already has a " + track.channel().name()
                            .toLowerCase(java.util.Locale.ROOT) + " track");
                }
                lastKeyframe = Math.max(lastKeyframe, track.keyframes().get(track.keyframes().size() - 1).time());
                tracks.add(track);
            }
        }
        double duration = declaredDuration == null ? lastKeyframe : declaredDuration;
        if (declaredDuration != null && lastKeyframe > declaredDuration + 1e-9) {
            problems.error("duration", "is " + declaredDuration + "s but a keyframe sits at " + lastKeyframe + "s");
        }
        if (loop && duration <= 0) {
            problems.error("duration", "a looping animation needs a duration greater than 0");
        }

        warnings.addAll(problems.warnings().stream().map(w -> source + ": " + w).toList());
        problems.throwIfErrors(source);
        return new AnimationDefinition(id, duration, loop, priority == null ? 0 : priority, resetOnEnd, tracks);
    }

    private @Nullable TransformTrack parseTrack(JsonObject json, String path, JsonFields f, Problems problems) {
        f.warnUnknownKeys(json, path, TRACK_KEYS);
        String part = f.string(json, "part", path, true);
        String rawChannel = f.string(json, "channel", path, true);
        Channel channel = rawChannel == null ? null : Channel.parse(rawChannel);
        if (rawChannel != null && channel == null) {
            problems.error(path + ".channel", "'" + rawChannel + "' is not one of translation, rotation, scale");
        }
        String rawInterpolation = f.string(json, "interpolation", path, false);
        Interpolation interpolation = rawInterpolation == null ? Interpolation.LINEAR
                : Interpolation.parse(rawInterpolation);
        if (interpolation == null) {
            problems.error(path + ".interpolation", "'" + rawInterpolation + "' is not one of linear, step");
            interpolation = Interpolation.LINEAR;
        }

        JsonArray array = f.array(json, "keyframes", path, true);
        List<Keyframe> keyframes = new ArrayList<>();
        if (array != null) {
            if (array.isEmpty()) {
                problems.error(path + ".keyframes", "a track needs at least one keyframe");
            }
            double previous = -1;
            for (int i = 0; i < array.size(); i++) {
                String kPath = path + ".keyframes[" + i + "]";
                if (!array.get(i).isJsonObject()) {
                    problems.error(kPath, "expected an object");
                    continue;
                }
                JsonObject kf = array.get(i).getAsJsonObject();
                f.warnUnknownKeys(kf, kPath, KEYFRAME_KEYS);
                Double time = f.optionalNumber(kf, "time", kPath);
                Double step = f.optionalNumber(kf, "duration", kPath);
                if (time != null && step != null) {
                    problems.error(kPath, "use either \"time\" or \"duration\", not both");
                    continue;
                }
                if (step != null && step < 0) {
                    problems.error(kPath + ".duration", "must not be negative");
                    continue;
                }
                double at = time != null ? time : (step != null ? Math.max(previous, 0) + step : (i == 0 ? 0 : -1));
                if (at < 0) {
                    problems.error(kPath, "needs a \"time\" or \"duration\"");
                    continue;
                }
                if (at < previous) {
                    problems.error(kPath + ".time", "keyframes must be in chronological order (" + at + "s comes after "
                            + previous + "s)");
                    continue;
                }
                if (!kf.has("value")) {
                    problems.error(kPath + ".value", "required [x, y, z] value is missing");
                    continue;
                }
                Vec3 value = f.vec3(kf.get("value"), kPath + ".value", channel == Channel.SCALE);
                if (value != null) {
                    keyframes.add(new Keyframe(at, value));
                    previous = at;
                }
            }
        }
        if (part == null || channel == null || keyframes.isEmpty()) {
            return null;
        }
        return new TransformTrack(part, channel, interpolation, keyframes);
    }
}
