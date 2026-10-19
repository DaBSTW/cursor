package dev.example.soledom.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import dev.example.soledom.api.AnimationDefinition;
import dev.example.soledom.api.ModelDefinition;
import dev.example.soledom.api.ModelOptions;
import dev.example.soledom.api.PartDefinition;
import dev.example.soledom.api.RootDefinition;
import dev.example.soledom.api.Vec3;

class TransformSnapshotTest {

    private static final float EPS = 1e-4f;

    private static ModelDefinition model(Vec3 offset, Vec3 rotation, Vec3 scale, PartDefinition part) {
        return new ModelDefinition("soledom:test", "test", RootDefinition.DEFAULT, List.of(part), offset, rotation,
                scale, Map.of(), null, Set.of(), Map.of(), ModelOptions.DEFAULT);
    }

    private static void assertVec(Vector3f expected, Vector3f actual) {
        assertEquals(expected.x, actual.x, EPS);
        assertEquals(expected.y, actual.y, EPS);
        assertEquals(expected.z, actual.z, EPS);
    }

    @Test
    void identityProducesRestPoseWithModelTransformApplied() {
        PartDefinition part = PartDefinition.item("body", "minecraft:stick", 1, null);
        PartDefinition placed = new PartDefinition(part.id(), part.type(), part.material(), part.customModelData(),
                part.model(), null, null, null, null, part.textStyle(), "FIXED", "FIXED", new Vec3(1, 0, 0),
                Vec3.ZERO, Vec3.ONE, false, null, null, 1f, 0f, 1f);
        ModelDefinition model = model(new Vec3(0, 1, 0), new Vec3(0, 90, 0), Vec3.uniform(2), placed);

        Transformation t = TransformSnapshot.IDENTITY.toTransformation(model, placed);

        // (1,0,0) scaled by 2 -> (2,0,0), rotated 90° around Y -> (0,0,-2), offset -> (0,1,-2)
        assertVec(new Vector3f(0, 1, -2), t.getTranslation());
        assertVec(new Vector3f(2, 2, 2), t.getScale());
    }

    @Test
    void animationDeltaIsAddedToRestPose() {
        PartDefinition part = PartDefinition.item("body", "minecraft:stick", 1, null);
        ModelDefinition model = model(Vec3.ZERO, Vec3.ZERO, Vec3.ONE, part);
        TransformSnapshot snapshot = TransformSnapshot.IDENTITY
                .with(Channel.TRANSLATION, new Vec3(0, 0.5, 0))
                .with(Channel.SCALE, Vec3.uniform(3));

        Transformation t = snapshot.toTransformation(model, part);

        assertVec(new Vector3f(0, 0.5f, 0), t.getTranslation());
        assertVec(new Vector3f(3, 3, 3), t.getScale());
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
        once.setPaused(true);
        assertEquals(null, once.sample("other", Channel.ROTATION));
    }
}
