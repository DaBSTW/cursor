package dev.example.soledom.loader;

import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

/** Converts YAML documents to Gson trees so YAML model/animation files share the JSON validation path. */
final class YamlJson {

    private static final Gson GSON = new Gson();

    private YamlJson() {
    }

    static @NotNull JsonElement parse(@NotNull String yaml) {
        // SafeConstructor: plain maps/lists/scalars only, never arbitrary object instantiation.
        Object tree = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);
        return GSON.toJsonTree(tree);
    }
}
