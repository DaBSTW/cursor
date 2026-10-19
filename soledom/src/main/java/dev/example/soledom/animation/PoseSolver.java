package dev.example.soledom.animation;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import dev.example.soledom.api.ModelDefinition;
import dev.example.soledom.api.PartDefinition;

/**
 * Resolves the part hierarchy into one model-space matrix per part:
 * {@code world(part) = world(parent) · local(part)}, with the model-level offset/rotation/scale at the top.
 * The resulting matrix is what the part's display entity renders with.
 */
public final class PoseSolver {

    private PoseSolver() {
    }

    /**
     * @param deltas animated delta per part id (return {@link TransformSnapshot#IDENTITY} for the rest pose)
     * @return matrix per part id, including {@code GROUP} parts
     */
    public static @NotNull Map<String, Matrix4f> solve(@NotNull ModelDefinition model,
            @NotNull Function<String, TransformSnapshot> deltas) {
        Matrix4f root = new Matrix4f()
                .translation(model.offset().toVector3f())
                .rotate(TransformSnapshot.eulerDegrees(model.rotation()))
                .scale(model.scale().toVector3f());
        Map<String, PartDefinition> byId = new HashMap<>();
        for (PartDefinition part : model.parts()) {
            byId.put(part.id(), part);
        }
        Map<String, Matrix4f> world = new HashMap<>();
        for (PartDefinition part : model.parts()) {
            resolve(part, byId, deltas, root, world, 0);
        }
        return world;
    }

    private static Matrix4f resolve(PartDefinition part, Map<String, PartDefinition> byId,
            Function<String, TransformSnapshot> deltas, Matrix4f root, Map<String, Matrix4f> world, int depth) {
        Matrix4f known = world.get(part.id());
        if (known != null) {
            return known;
        }
        PartDefinition parent = part.parent() == null ? null : byId.get(part.parent());
        // The loader rejects cycles; the depth guard only protects API callers that bypass it.
        Matrix4f parentMatrix = parent == null || depth > byId.size() ? root
                : resolve(parent, byId, deltas, root, world, depth + 1);
        TransformSnapshot delta = deltas.apply(part.id());
        Matrix4f matrix = new Matrix4f(parentMatrix).mul(
                (delta == null ? TransformSnapshot.IDENTITY : delta).localMatrix(part));
        world.put(part.id(), matrix);
        return matrix;
    }
}
