package dev.example.soledom.blockbench;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.example.soledom.api.AnimationDefinition;
import dev.example.soledom.api.ModelDefinition;
import dev.example.soledom.loader.AnimationLoader;
import dev.example.soledom.loader.ContentLoader;
import dev.example.soledom.loader.ModelLoader;
import dev.example.soledom.loader.ValidationException;
import dev.example.soledom.registry.RegisteredModel;

/**
 * Imports every {@code .bbmodel} in {@code plugins/Soledom/blockbench/}. Converted models go through the regular
 * model/animation validation, so they behave exactly like hand-written ones.
 *
 * <p>An optional sidecar file next to the project, {@code <name>.soledom.json}, overrides model-level fields of
 * the generated model: {@code id}, {@code display_name}, {@code root}, {@code offset}, {@code rotation},
 * {@code scale}, {@code default_animation}, {@code tags}, {@code metadata} and {@code options}.</p>
 *
 * <p>Custom model data values are handed out in file-name order starting at
 * {@code blockbench.custom-model-data-start}. Adding or removing projects can shift them, so re-export the
 * resource pack after changing the folder.</p>
 */
public final class BlockbenchImporter {

    public static final String DIRECTORY = "blockbench";
    public static final String SIDECAR_SUFFIX = ".soledom.json";

    private static final Set<String> SIDECAR_KEYS = Set.of("id", "display_name", "root", "offset", "rotation",
            "scale", "default_animation", "tags", "metadata", "options");

    private final BbModelConverter converter = new BbModelConverter();
    private final ModelLoader modelLoader = new ModelLoader();
    private final AnimationLoader animationLoader = new AnimationLoader();

    public @NotNull Result importAll(@NotNull Path dataFolder, @NotNull ImportSettings settings) {
        Map<String, RegisteredModel> models = new LinkedHashMap<>();
        Map<String, AnimationDefinition> animations = new LinkedHashMap<>();
        Map<String, byte[]> packFiles = new TreeMap<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Path dir = dataFolder.resolve(DIRECTORY);
        if (!settings.enabled() || !Files.isDirectory(dir)) {
            return new Result(models, animations, packFiles, errors, warnings);
        }
        int nextCustomModelData = settings.customModelDataStart();
        for (Path file : projects(dir, errors)) {
            String relative = DIRECTORY + "/" + dir.relativize(file).toString().replace('\\', '/');
            BbModelConverter.Conversion conversion;
            try {
                JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                if (!json.isJsonObject()) {
                    errors.add(relative + ": not a Blockbench project");
                    continue;
                }
                conversion = converter.convert(json.getAsJsonObject(), file.getFileName().toString(), settings,
                        nextCustomModelData);
            } catch (IOException | RuntimeException e) {
                errors.add(relative + ": cannot read project: " + e.getMessage());
                continue;
            }
            // Advance even if the model is rejected below, so a broken file doesn't shift every later model.
            nextCustomModelData += conversion.customModelDataUsed();
            warnings.addAll(conversion.warnings());
            try {
                JsonObject modelJson = conversion.model();
                applySidecar(file, modelJson, relative, warnings);
                ModelDefinition definition = modelLoader.parse(modelJson, relative, warnings);
                Map<String, AnimationDefinition> byId = new LinkedHashMap<>();
                for (Map.Entry<String, JsonObject> animation : conversion.animations().entrySet()) {
                    AnimationDefinition parsed = animationLoader.parse(animation.getValue(),
                            relative + "#" + animation.getKey(), warnings);
                    byId.put(parsed.id(), parsed);
                }
                List<String> linkErrors = new ArrayList<>();
                Map<String, AnimationDefinition> resolved = ContentLoader.resolveAnimations(definition, byId, Map.of(),
                        linkErrors);
                if (!linkErrors.isEmpty()) {
                    linkErrors.forEach(err -> errors.add(relative + ": " + err));
                    continue;
                }
                if (models.containsKey(definition.id())) {
                    errors.add(relative + ": duplicate model id '" + definition.id() + "'");
                    continue;
                }
                models.put(definition.id(), new RegisteredModel(definition, resolved, false));
                animations.putAll(byId);
                packFiles.putAll(conversion.packFiles());
            } catch (ValidationException e) {
                e.getErrors().forEach(err -> errors.add(e.getSource() + ": " + err));
            } catch (IOException | RuntimeException e) {
                errors.add(relative + ": " + e.getMessage());
            }
        }
        return new Result(models, animations, packFiles, errors, warnings);
    }

    private static void applySidecar(Path project, JsonObject model, String relative, List<String> warnings)
            throws IOException, ValidationException {
        String name = project.getFileName().toString();
        Path sidecar = project.resolveSibling(name.substring(0, name.length() - ".bbmodel".length()) + SIDECAR_SUFFIX);
        if (!Files.isRegularFile(sidecar)) {
            return;
        }
        JsonElement json;
        try {
            json = JsonParser.parseString(Files.readString(sidecar, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            throw new ValidationException(sidecar.getFileName().toString(), "malformed JSON: " + e.getMessage());
        }
        if (!json.isJsonObject()) {
            throw new ValidationException(sidecar.getFileName().toString(), "top level must be an object");
        }
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
            if (!SIDECAR_KEYS.contains(entry.getKey())) {
                warnings.add(relative + ": " + sidecar.getFileName() + ": '" + entry.getKey()
                        + "' cannot be overridden here; ignored");
            } else if (entry.getKey().equals("metadata") && entry.getValue().isJsonObject()) {
                JsonObject merged = model.has("metadata") ? model.getAsJsonObject("metadata") : new JsonObject();
                entry.getValue().getAsJsonObject().entrySet().forEach(e -> merged.add(e.getKey(), e.getValue()));
                model.add("metadata", merged);
            } else {
                model.add(entry.getKey(), entry.getValue());
            }
        }
    }

    private static List<Path> projects(Path dir, List<String> errors) {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".bbmodel"))
                    .sorted(Comparator.comparing(p -> dir.relativize(p).toString()))
                    .toList();
        } catch (IOException e) {
            errors.add(DIRECTORY + ": cannot list directory: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Imported content.
     *
     * @param models     converted models by id
     * @param animations converted animations by id
     * @param packFiles  generated item models and textures for the resource pack, by ZIP path
     * @param errors     problems that prevented a project from loading
     * @param warnings   conversion approximations and other non-fatal issues
     */
    public record Result(@NotNull Map<String, RegisteredModel> models,
            @NotNull Map<String, AnimationDefinition> animations, @NotNull Map<String, byte[]> packFiles,
            @NotNull List<String> errors, @NotNull List<String> warnings) {
    }
}
