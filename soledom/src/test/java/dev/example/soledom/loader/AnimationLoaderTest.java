package dev.example.soledom.loader;

import static dev.example.soledom.loader.ModelLoaderTest.assertHas;
import static dev.example.soledom.loader.ModelLoaderTest.resource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import dev.example.soledom.animation.Channel;
import dev.example.soledom.animation.TransformTrack;
import dev.example.soledom.api.AnimationDefinition;
import dev.example.soledom.api.Vec3;

class AnimationLoaderTest {

    private final AnimationLoader loader = new AnimationLoader();

    @Test
    void loadsBundledExamples() throws Exception {
        AnimationDefinition idle = loader.parse(resource("examples/animations/example_idle.json"), "idle",
                new ArrayList<>());
        assertEquals("soledom:example_idle", idle.id());
        assertEquals(2.0, idle.duration());
        assertTrue(idle.loop());
        assertEquals(2, idle.tracks().size());

        AnimationDefinition rotate = loader.parse(resource("examples/animations/example_rotate.json"), "rotate",
                new ArrayList<>());
        assertEquals(10, rotate.priority());
        TransformTrack head = rotate.tracks().get(1);
        // Keyframes given by "duration" accumulate: 0 -> 0.5 -> 1.5 -> 2.0
        assertEquals(1.5, head.keyframes().get(2).time(), 1e-9);
        assertEquals(2.0, head.keyframes().get(3).time(), 1e-9);
    }

    @Test
    void durationDefaultsToLastKeyframeAndPositionAliasesTranslation() throws Exception {
        AnimationDefinition animation = loader.parse("""
                {"id": "wave", "tracks": [{"part": "arm", "channel": "position",
                  "keyframes": [{"time": 0, "value": [0,0,0]}, {"time": 1.5, "value": [0,1,0]}]}]}
                """, "wave.json", new ArrayList<>());
        assertEquals(1.5, animation.duration());
        assertEquals(Channel.TRANSLATION, animation.tracks().get(0).channel());
        assertTrue(animation.resetOnEnd());
    }

    @Test
    void rejectsInvalidTimelines() {
        ValidationException e = assertThrows(ValidationException.class, () -> loader.parse("""
                {"id": "bad", "duration": 1.0, "loop": true, "tracks": [
                  {"part": "a", "channel": "spin", "keyframes": [{"time": 0, "value": [0,0,0]}]},
                  {"part": "b", "channel": "rotation", "keyframes": [
                    {"time": 0.5, "value": [0,0,0]}, {"time": 0.2, "value": [0,0,0]}, {"time": 2.0, "value": [0,0,0]}]},
                  {"part": "c", "channel": "scale", "keyframes": [{"time": 0, "duration": 1, "value": 1}]}
                ]}
                """, "bad.json", new ArrayList<>()));
        assertHas(e.getErrors(), "tracks[0].channel: 'spin' is not one of translation, rotation, scale");
        assertHas(e.getErrors(), "tracks[1].keyframes[1].time: keyframes must be in chronological order");
        assertHas(e.getErrors(), "duration: is 1.0s but a keyframe sits at 2.0s");
        assertHas(e.getErrors(), "tracks[2].keyframes[0]: use either \"time\" or \"duration\"");
    }

    @Test
    void sampleInterpolatesLinearlyAndClampsOutsideRange() throws Exception {
        TransformTrack track = loader.parse("""
                {"id": "spin", "tracks": [{"part": "p", "channel": "rotation", "keyframes": [
                  {"time": 0, "value": [0, 0, 0]}, {"time": 1, "value": [0, 180, 0]}, {"time": 2, "value": [0, 360, 0]}]}]}
                """, "spin.json", new ArrayList<>()).tracks().get(0);
        assertEquals(new Vec3(0, 90, 0), track.sample(0.5));
        assertEquals(new Vec3(0, 270, 0), track.sample(1.5));
        assertEquals(new Vec3(0, 0, 0), track.sample(-1));
        assertEquals(new Vec3(0, 360, 0), track.sample(5));
    }

    @Test
    void stepInterpolationHoldsPreviousValue() throws Exception {
        TransformTrack track = loader.parse("""
                {"id": "blink", "tracks": [{"part": "p", "channel": "scale", "interpolation": "step", "keyframes": [
                  {"time": 0, "value": 1}, {"time": 1, "value": 0}]}]}
                """, "blink.json", new ArrayList<>()).tracks().get(0);
        assertEquals(Vec3.ONE, track.sample(0.99));
        assertEquals(Vec3.ZERO, track.sample(1.0));
    }
}
