package dev.example.soledom.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import dev.example.soledom.api.AnimationDefinition;
import dev.example.soledom.api.ModelDefinition;
import dev.example.soledom.api.ModelOptions;
import dev.example.soledom.api.PartDefinition;
import dev.example.soledom.api.PartType;
import dev.example.soledom.api.RootDefinition;
import dev.example.soledom.api.TextStyle;
import dev.example.soledom.api.Vec3;

class TransformSnapshotTest {

    private static final float EPS = 1e-4f;

    static PartDefinition part(String id, PartType type, Vec3 position, Vec3 rotation, Vec3 scale, String parent) {
        return new PartDefinition(id, type, type == PartType.ITEM_DISPLAY ? "minecraft:stick" : null, null, null,
                null, null, null, null, TextStyle.DEFAULT, "FIXED", "FIXED", position, rotation, scale, false, null,
                null, 1f, 0f, 1f, parent);
    }

    private static ModelDefinition model(Vec3 offset, Vec3 rotation, Vec3 scale, PartDefinition... parts) {
        return new ModelDefinition("soledom:test", "test", RootDefinition.DEFAULT, List.of(parts), offset, rotation,
                scale, Map.of(), null, Set.of(), Map.of(), ModelOptions.DEFAULT);
    }

    private static void assertPoint(Vector3f expected, Matrix4f matrix, Vector3f local) {
        Vector3f actual = matrix.transformPosition(new Vector3f(local));
        assertEquals(expected.x, actual.x, EPS, "x");
        assertEquals(expected.y, actual.y, EPS, "y");
        assertEquals(expected.z, actual.z, EPS, "z");
    }

    @Test
    void restPoseAppliesModelTransform() {
        PartDefinition body = part("body", PartType.ITEM_DISPLAY, new Vec3(1, 0, 0), Vec3.ZERO, Vec3.ONE, null);
        ModelDefinition model = model(new Vec3(0, 1, 0), new Vec3(0, 90, 0), Vec3.uniform(2), body);

        Matrix4f m = PoseSolver.solve(model, id -> TransformSnapshot.IDENTITY).get("body");

        // (1,0,0) scaled by 2 -> (2,0,0), rotated 90° around Y -> (0,0,-2), offset -> (0,1,-2)
        assertPoint(new Vector3f(0, 1, -2), m, new Vector3f());
        assertEquals(2f, m.getScale(new Vector3f()).x, EPS);
    }

    @Test
    void childrenFollowParentRotationAndAnimation() {
        PartDefinition arm = part("arm", PartType.GROUP, new Vec3(0, 1, 0), Vec3.ZERO, Vec3.ONE, null);
        PartDefinition hand = part("hand", PartType.ITEM_DISPLAY, new Vec3(1, 0, 0), Vec3.ZERO, Vec3.ONE, "arm");
        ModelDefinition model = model(Vec3.ZERO, Vec3.ZERO, Vec3.ONE, hand, arm);

        Map<String, Matrix4f> rest = PoseSolver.solve(model, id -> TransformSnapshot.IDENTITY);
        assertPoint(new Vector3f(1, 1, 0), rest.get("hand"), new Vector3f());

        // Rotating the parent 90° around Z swings the child from +X to +Y around the parent's pivot.
        Map<String, Matrix4f> posed = PoseSolver.solve(model, id -> id.equals("arm")
                ? TransformSnapshot.IDENTITY.with(Channel.ROTATION, new Vec3(0, 0, 90))
                : TransformSnapshot.IDENTITY);
        assertPoint(new Vector3f(0, 2, 0), posed.get("hand"), new Vector3f());

        // Parent scale scales the child's offset too, like Blockbench.
        Map<String, Matrix4f> scaled = PoseSolver.solve(model, id -> id.equals("arm")
                ? TransformSnapshot.IDENTITY.with(Channel.SCALE, Vec3.uniform(2)) : TransformSnapshot.IDENTITY);
        assertPoint(new Vector3f(2, 1, 0), scaled.get("hand"), new Vector3f());
    }

    @Test
    void rotationDeltasAddInEulerSpace() {
        PartDefinition p = part("p", PartType.ITEM_DISPLAY, Vec3.ZERO, new Vec3(0, 0, 30), Vec3.ONE, null);
        Matrix4f added = TransformSnapshot.IDENTITY.with(Channel.ROTATION, new Vec3(0, 0, 60)).localMatrix(p);
        // 30° + 60° around Z = 90°: +X maps to +Y.
        assertPoint(new Vector3f(0, 1, 0), added, new Vector3f(1, 0, 0));
    }

    @Test
    void playerLoopsAndFinishes() {
        TransformTrack track = new TransformTrack("p", Channel.ROTATION, Interpolation.LINEAR,
                List.of(new Keyframe(0, Vec3.ZERO), new Keyframe(1, new Vec3(0, 90, 0))));
        AnimationPlayer looping = new AnimationPlayer("spin",
                new AnimationDefinition("soledom:spin", 1.0, true, 0, true, List.of(track)), 1.0);
        assertEquals(AnimationPlayer.Step.RUNNING, looping.advance(0.5));
        assertEquals(new Vec3(0, 45, 0), looping.sample("p", Channel.ROTATION));
        assertEquals(AnimationPlayer.Step.LOOPED, looping.advance(0.75));
        assertEquals(1, looping.loops());
        assertEquals(0.25, looping.time(), 1e-9);

        AnimationPlayer once = new AnimationPlayer("spin",
                new AnimationDefinition("soledom:spin", 1.0, false, 0, true, List.of(track)), 2.0);
        assertEquals(AnimationPlayer.Step.FINISHED, once.advance(0.5));
        assertEquals(new Vec3(0, 90, 0), once.sample("p", Channel.ROTATION));
        assertNull(once.sample("other", Channel.ROTATION));
    }
}
