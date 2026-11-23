package io.github.tryrodave.jsonextended.gltf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tryrodave.jsonextended.animation.Animation.Loop;
import io.github.tryrodave.jsonextended.obj.ObjQuad;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GltfAnimationTest {
    private static final float EPS = 1e-4f;
    private static final float S45 = (float) Math.sin(Math.PI / 8), C45 = (float) Math.cos(Math.PI / 8);
    private static final float S90 = (float) Math.sin(Math.PI / 4), C90 = (float) Math.cos(Math.PI / 4);

    /**
     * Node 0 "arm" (translation 1,0,0) holds node 1 "hand" (a triangle at its origin). Animations:
     * "move": arm translation linear 0..2 s from (1,0,0) to (3,0,0); "turn": arm rotation 0..90 degrees about Y in 1 s;
     * "step": hand scale step; "spline": arm translation cubic spline with zero tangents.
     */
    private static GltfModel model() {
        Gltf g = new Gltf();
        int pos = g.floats("VEC3", 0, 0, 0, 1, 0, 0, 0, 1, 0);
        int t2 = g.times(0, 2), t1 = g.times(0, 1);
        int moveOut = g.floats("VEC3", 1, 0, 0, 3, 0, 0);
        int turnOut = g.floats("VEC4", 0, 0, 0, 1, 0, S90, 0, C90);
        int stepOut = g.floats("VEC3", 1, 1, 1, 2, 2, 2);
        int splineOut = g.floats("VEC3", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 0, 0, 0, 0, 0);
        String rest = "\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":" + pos + "}}]}],"
                + "\"nodes\":[{\"name\":\"arm\",\"translation\":[1,0,0],\"children\":[1]},{\"name\":\"hand\",\"mesh\":0}],"
                + "\"scenes\":[{\"nodes\":[0]}],"
                + "\"animations\":["
                + "{\"name\":\"move\",\"samplers\":[{\"input\":" + t2 + ",\"output\":" + moveOut + "}],"
                + "\"channels\":[{\"sampler\":0,\"target\":{\"node\":0,\"path\":\"translation\"}}]},"
                + "{\"name\":\"turn\",\"samplers\":[{\"input\":" + t1 + ",\"output\":" + turnOut + "}],"
                + "\"channels\":[{\"sampler\":0,\"target\":{\"node\":0,\"path\":\"rotation\"}}]},"
                + "{\"name\":\"step\",\"samplers\":[{\"input\":" + t1 + ",\"output\":" + stepOut + ",\"interpolation\":\"STEP\"}],"
                + "\"channels\":[{\"sampler\":0,\"target\":{\"node\":1,\"path\":\"scale\"}}]},"
                + "{\"samplers\":[{\"input\":" + t2 + ",\"output\":" + splineOut + ",\"interpolation\":\"CUBICSPLINE\"},"
                + "{\"input\":" + t1 + ",\"output\":" + stepOut + "}],"
                + "\"channels\":[{\"sampler\":0,\"target\":{\"node\":0,\"path\":\"translation\"}},"
                + "{\"sampler\":1,\"target\":{\"node\":1,\"path\":\"weights\"}}]}]";
        byte[] glb = g.glb(rest);
        return GltfModel.load("t:m.glb", id -> glb);
    }

    private static float[] t(Map<Integer, float[]> pose, int node) {
        float[] trs = pose.get(node);
        return new float[] {trs[GltfModel.T], trs[GltfModel.T + 1], trs[GltfModel.T + 2]};
    }

    @Test
    void readsAnimationsByNameAndLength() {
        GltfModel model = model();
        assertEquals(List.of("move", "turn", "step", "animation_3"), List.copyOf(model.animations().keySet()));
        assertEquals(2f, model.animations().get("move").length(), EPS);
        assertEquals(1f, model.animations().get("turn").length(), EPS);
        assertEquals(List.of("t:m.glb: animations[3].channels[1].target.path: 'weights' isn't played (only translation, rotation and scale)"),
                model.warnings());
        assertEquals("arm", model.nodes().get(0).name());
        assertEquals(0, model.nodes().get(1).parent());
    }

    @Test
    void linearTranslationAndClamping() {
        GltfAnimation move = model().animations().get("move");
        assertArrayEquals(new float[] {2, 0, 0}, t(move.sample(1), 0), EPS);
        assertArrayEquals(new float[] {1, 0, 0}, t(move.sample(-1), 0), EPS);
        assertArrayEquals(new float[] {3, 0, 0}, t(move.sample(5), 0), EPS);
    }

    @Test
    void rotationIsSlerped() {
        float[] trs = model().animations().get("turn").sample(0.5f).get(0);
        assertArrayEquals(new float[] {0, S45, 0, C45},
                new float[] {trs[GltfModel.R], trs[GltfModel.R + 1], trs[GltfModel.R + 2], trs[GltfModel.R + 3]}, EPS);
        // the untouched translation keeps its rest value
        assertArrayEquals(new float[] {1, 0, 0}, new float[] {trs[0], trs[1], trs[2]}, EPS);
    }

    @Test
    void stepHoldsUntilTheNextKey() {
        GltfAnimation step = model().animations().get("step");
        assertEquals(1f, step.sample(0.99f).get(1)[GltfModel.S], EPS);
        assertEquals(2f, step.sample(1f).get(1)[GltfModel.S], EPS);
    }

    @Test
    void cubicSplineWithFlatTangentsEasesInAndOut() {
        GltfAnimation spline = model().animations().get("animation_3");
        assertEquals(2f, t(spline.sample(1), 0)[0], EPS); // halfway of 0..4
        assertEquals(4 * (3 * 0.0625f - 2 * 0.015625f), t(spline.sample(0.5f), 0)[0], EPS); // smoothstep at u = 0.25
    }

    @Test
    void posingMovesChildrenWithTheirParent() {
        GltfModel model = model();
        assertArrayEquals(new float[] {1, 0, 0}, model.quads().get(0).positions()[0], EPS);
        List<ObjQuad> turned = model.pose(model.animations().get("turn").sample(1));
        // arm turned 90 degrees about Y: the hand's (1,0,0) corner goes to (0,0,-1), then the arm's +1 on X
        assertArrayEquals(new float[] {1, 0, 0}, turned.get(0).positions()[0], EPS);
        assertArrayEquals(new float[] {1, 0, -1}, turned.get(0).positions()[1], EPS);
        List<ObjQuad> scaled = model.pose(model.animations().get("step").sample(1));
        assertArrayEquals(new float[] {3, 0, 0}, scaled.get(0).positions()[1], EPS);
    }

    @Test
    void playerLoopsHoldsAndStops() {
        GltfModel model = model();
        GltfAnimation move = model.animations().get("move");
        GltfAnimationPlayer player = new GltfAnimationPlayer(model);
        player.play(move, Loop.LOOP, 10, 0);
        assertArrayEquals(new float[] {2, 0, 0}, t(player.pose(13), 0), EPS); // 3 s into a 2 s loop = 1 s
        player.play(move, Loop.HOLD, 10, 0);
        assertArrayEquals(new float[] {3, 0, 0}, t(player.pose(20), 0), EPS);
        assertTrue(player.playing(20));
        player.play(move, Loop.ONCE, 10, 0);
        assertTrue(player.pose(13).isEmpty());
        assertFalse(player.playing(13));
        player.skipToEnd(move, Loop.HOLD, 50);
        assertArrayEquals(new float[] {3, 0, 0}, t(player.pose(50), 0), EPS);
    }

    @Test
    void crossfadeBlendsFromTheLastAnimation() {
        GltfModel model = model();
        GltfAnimationPlayer player = new GltfAnimationPlayer(model);
        player.play(model.animations().get("move"), Loop.HOLD, 0, 0);   // arm at (3,0,0) from 2 s on
        player.play(model.animations().get("turn"), Loop.HOLD, 5, 1);   // turn keeps the rest translation (1,0,0)
        assertArrayEquals(new float[] {2, 0, 0}, t(player.pose(5.5f), 0), EPS);
        assertArrayEquals(new float[] {1, 0, 0}, t(player.pose(6.5f), 0), EPS);
        player.ensure(model.animations().get("turn"), Loop.HOLD, 7, 1);  // already playing: no restart
        assertEquals(7f - 5f, player.elapsed(7), EPS);
    }

    @Test
    void badSamplersAreErrors() {
        Gltf g = new Gltf();
        int times = g.times(1, 0);
        int out = g.floats("VEC3", 0, 0, 0, 1, 1, 1);
        String rest = "\"nodes\":[{}],\"animations\":[{\"samplers\":[{\"input\":" + times + ",\"output\":" + out + "}],"
                + "\"channels\":[{\"sampler\":0,\"target\":{\"node\":0,\"path\":\"rotation\"}}]}]";
        byte[] glb = g.glb(rest);
        GltfException e = assertThrows(GltfException.class, () -> GltfModel.load("t:m.glb", id -> glb));
        assertEquals("t:m.glb: animations[0].samplers[0].output: expected VEC4 for rotation", e.getMessage());
        byte[] down = g.glb(rest.replace("rotation", "scale"));
        e = assertThrows(GltfException.class, () -> GltfModel.load("t:m.glb", id -> down));
        assertEquals("t:m.glb: animations[0].samplers[0].input: times must not go down", e.getMessage());
    }
}
