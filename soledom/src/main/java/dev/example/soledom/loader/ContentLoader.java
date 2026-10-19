package dev.example.soledom.loader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;

import dev.example.soledom.animation.TransformTrack;
import dev.example.soledom.api.AnimationDefinition;
import dev.example.soledom.api.ModelDefinition;
import dev.example.soledom.core.utils.NamespacedIds;
import dev.example.soledom.registry.RegisteredModel;

/**
 * Loads every model and animation file below a data folder and resolves the links between them.
 *
 * <p>Pure file IO and parsing: safe to run on an async thread. One broken file never prevents the others from
 * loading; its problems are reported in the {@link LoadReport}.</p>
 */
public final class ContentLoader {

    public static final String MODELS_DIR = "models";
    public static final String ANIMATIONS_DIR = "animations";

    private final ModelLoader modelLoader = new ModelLoader();
    private final AnimationLoader animationLoader = new AnimationLoader();

    public @NotNull LoadReport load(@NotNull Path dataFolder) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        Map<String, AnimationDefinition> animationsById = new LinkedHashMap<>();
        Map<String, AnimationDefinition> animationsByPath = new HashMap<>();
        for (Path file : listDocuments(dataFolder.resolve(ANIMATIONS_DIR), errors)) {
            String relative = relative(dataFolder, file);
            try {
                JsonElement json = read(file, relative);
                AnimationDefinition animation = animationLoader.parse(json.getAsJsonObject(), relative, warnings);
                if (animationsById.containsKey(animation.id())) {
                    errors.add(relative + ": duplicate animation id '" + animation.id() + "'");
                    continue;
                }
                animationsById.put(animation.id(), animation);
                animationsByPath.put(relative, animation);
            } catch (ValidationException e) {
                e.getErrors().forEach(err -> errors.add(e.getSource() + ": " + err));
            } catch (IOException | RuntimeException e) {
                errors.add(relative + ": " + describe(e));
            }
        }

        Map<String, RegisteredModel> models = new LinkedHashMap<>();
        java.util.Set<String> seenModelIds = new java.util.HashSet<>();
        for (Path file : listDocuments(dataFolder.resolve(MODELS_DIR), errors)) {
            String relative = relative(dataFolder, file);
            try {
                JsonElement json = read(file, relative);
                ModelDefinition model = modelLoader.parse(json.getAsJsonObject(), relative, warnings);
                if (!seenModelIds.add(model.id())) {
                    errors.add(relative + ": duplicate model id '" + model.id() + "'");
                    continue;
                }
                List<String> linkErrors = new ArrayList<>();
                Map<String, AnimationDefinition> resolved = resolveAnimations(model, animationsById, animationsByPath,
                        linkErrors);
                if (!linkErrors.isEmpty()) {
                    linkErrors.forEach(err -> errors.add(relative + ": " + err));
                    continue;
                }
                models.put(model.id(), new RegisteredModel(model, resolved, false));
            } catch (ValidationException e) {
                e.getErrors().forEach(err -> errors.add(e.getSource() + ": " + err));
            } catch (IOException | RuntimeException e) {
                errors.add(relative + ": " + describe(e));
            }
        }
        return new LoadReport(models, animationsById, errors, warnings);
    }

    /**
     * Resolves a model's animation references (ids or data-folder relative paths) and checks that every track
     * drives a part that exists in the model.
     */
    public static @NotNull Map<String, AnimationDefinition> resolveAnimations(@NotNull ModelDefinition model,
            @NotNull Map<String, AnimationDefinition> byId, @NotNull Map<String, AnimationDefinition> byPath,
            @NotNull List<String> errors) {
        Map<String, AnimationDefinition> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : model.animations().entrySet()) {
            String ref = entry.getValue();
            AnimationDefinition animation = isPathReference(ref)
                    ? byPath.get(ref.replace('\\', '/'))
                    : byId.get(NamespacedIds.normalize(ref, NamespacedIds.DEFAULT_NAMESPACE));
            if (animation == null) {
                errors.add("animations." + entry.getKey() + ": '" + ref + "' was not found"
                        + (isPathReference(ref) ? " (paths are relative to the plugin folder)" : ""));
                continue;
            }
            for (TransformTrack track : animation.tracks()) {
                if (model.part(track.part()).isEmpty()) {
                    errors.add("animations." + entry.getKey() + ": animation '" + animation.id()
                            + "' drives part '" + track.part() + "' which this model does not have");
                }
            }
            resolved.put(entry.getKey(), animation);
        }
        return resolved;
    }

    private static boolean isPathReference(String ref) {
        String lower = ref.toLowerCase(Locale.ROOT);
        return lower.endsWith(".json") || lower.endsWith(".yml") || lower.endsWith(".yaml");
    }

    private static JsonElement read(Path file, String relative) throws IOException, ValidationException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        JsonElement element;
        try {
            element = name.endsWith(".json") ? com.google.gson.JsonParser.parseString(content) : YamlJson.parse(content);
        } catch (RuntimeException e) {
            throw new ValidationException(relative, "malformed "
                    + (name.endsWith(".json") ? "JSON" : "YAML") + ": " + ModelLoader.rootMessage(e));
        }
        if (element == null || !element.isJsonObject()) {
            throw new ValidationException(relative, "top level must be an object");
        }
        return element;
    }

    private static List<Path> listDocuments(Path dir, List<String> errors) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile).filter(ContentLoader::isDocument)
                    .sorted(Comparator.comparing(Path::toString)).toList();
        } catch (IOException e) {
            errors.add(dir.getFileName() + ": cannot list directory: " + e.getMessage());
            return List.of();
        }
    }

    private static boolean isDocument(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return !name.startsWith(".") && (name.endsWith(".json") || name.endsWith(".yml") || name.endsWith(".yaml"));
    }

    private static String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static String describe(Exception e) {
        return e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
    }

    /**
     * Result of a full load.
     *
     * @param models     successfully loaded models by id
     * @param animations every valid animation by id
     * @param errors     human-readable problems, prefixed with the file they come from
     * @param warnings   non-fatal issues such as unknown fields
     */
    public record LoadReport(@NotNull Map<String, RegisteredModel> models,
            @NotNull Map<String, AnimationDefinition> animations, @NotNull List<String> errors,
            @NotNull List<String> warnings) {

        public LoadReport {
            models = Map.copyOf(models);
            animations = Map.copyOf(animations);
            errors = List.copyOf(errors);
            warnings = List.copyOf(warnings);
        }

        public @Nullable RegisteredModel model(@NotNull String id) {
            return models.get(id);
        }
    }
}
