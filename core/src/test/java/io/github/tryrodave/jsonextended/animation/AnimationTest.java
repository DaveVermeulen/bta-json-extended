package io.github.tryrodave.jsonextended.animation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.tryrodave.jsonextended.pose.PartTransform;
import io.github.tryrodave.jsonextended.pose.Pose;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnimationTest {
    private static final float EPS = 1e-3f;
    private final AnimationFile file = load();

    private static AnimationFile load() {
        try (InputStream in = AnimationTest.class.getResourceAsStream("/animation/test_chest.animation.json")) {
            return AnimationFile.parse("t:test_chest", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Keyframe key(float time, float value, Keyframe.Lerp lerp) {
        return new Keyframe(time, new float[] {value, value, value}, new float[] {value, value, value}, lerp);
    }

    @Test
    void readsLoopModesAndLengths() {
        Animation open = file.get("animation.test_chest.open");
        assertEquals(Animation.Loop.HOLD, open.loop());
        assertEquals(0.5f, open.length());
        assertEquals(Animation.Loop.LOOP, file.get("animation.test_chest.bob").loop());
        Animation snap = file.get("animation.test_chest.snap");
        assertEquals(Animation.Loop.ONCE, snap.loop());
        assertEquals(0.5f, snap.length(), "no animation_length: the last keyframe");
    }

    @Test
    void linearHalfway() {
        Channel c = new Channel(List.of(key(0, 0, Keyframe.Lerp.LINEAR), key(2, 10, Keyframe.Lerp.LINEAR)));
        assertEquals(2.5f, c.sample(0.5f)[0], EPS);
        assertEquals(0f, c.sample(-1)[0]);
        assertEquals(10f, c.sample(5)[0]);
    }

    @Test
    void catmullRomPassesThroughKeyframesAndIsSmooth() {
        Channel c = new Channel(List.of(key(0, 0, Keyframe.Lerp.CATMULL_ROM), key(1, 0, Keyframe.Lerp.CATMULL_ROM),
                key(2, 10, Keyframe.Lerp.CATMULL_ROM), key(3, 10, Keyframe.Lerp.CATMULL_ROM)));
        assertEquals(0f, c.sample(1)[0], EPS);
        assertEquals(10f, c.sample(2)[0], EPS);
        assertEquals(5f, c.sample(1.5f)[0], EPS);
        // Eases out of the flat part: slower than linear at the start of the segment.
        assertTrue(c.sample(1.25f)[0] < 2.5f);
        assertEquals(Channel.catmullRom(0, 0, 10, 10, 0.25f), c.sample(1.25f)[0], EPS);
    }

    @Test
    void stepHoldsAndPreDiffersFromPost() {
        Animation snap = file.get("animation.test_chest.snap");
        assertEquals(0f, snap.sample(0.2f).get("lid").rx(), EPS);
        // At 0.25 the value jumps from -10 (pre) to -45 (post); Bedrock -X is our +X.
        assertEquals(45f, snap.sample(0.25f).get("lid").rx(), EPS);
        assertEquals(45f, snap.sample(0.4f).get("lid").rx(), EPS);
    }

    @Test
    void bedrockAxesBecomeJavaAxes() {
        Animation open = file.get("animation.test_chest.open");
        PartTransform lid = open.sample(0.5f).get("lid");
        assertEquals(90f, lid.rx(), EPS, "Bedrock -90 X opens the lid like our +90");
        Animation.Bone bone = new Animation.Bone(new Channel(List.of(new Keyframe(0, new float[] {10, 20, 30},
                new float[] {10, 20, 30}, Keyframe.Lerp.LINEAR))), new Channel(List.of(new Keyframe(0,
                new float[] {1, 2, 3}, new float[] {1, 2, 3}, Keyframe.Lerp.LINEAR))), null);
        PartTransform t = Animation.toPart(bone, 0);
        assertArrayEquals(new float[] {-45, 0, 0, 0, 0, 1, 1, 1, 1}, file.get("animation.test_chest.snap").raw("lid", 0.3f), EPS);
        assertArrayEquals(new float[] {-10, -20, 30, -1, 2, 3, 1, 1, 1},
                new float[] {t.rx(), t.ry(), t.rz(), t.tx(), t.ty(), t.tz(), t.sx(), t.sy(), t.sz()});
    }

    @Test
    void loopsWrapOnceEndsHoldStays() {
        Animation bob = file.get("animation.test_chest.bob");
        assertEquals(0.5f, bob.sample(0.5f).get("knob").ty(), EPS);
        assertEquals(0.5f, bob.sample(4.5f).get("knob").ty(), EPS);
        assertEquals(1.25f, bob.sample(3).get("knob").sx(), EPS, "a scalar scale applies to all axes");
        Animation snap = file.get("animation.test_chest.snap");
        Pose base = Pose.REST.with("lid", PartTransform.rotation(5, 0, 0));
        assertTrue(snap.finished(0.5f));
        assertSame(base, snap.sample(0.7f, base));
        Animation open = file.get("animation.test_chest.open");
        assertFalse(open.finished(10));
        assertEquals(90f, open.sample(10).get("lid").rx(), EPS);
    }

    @Test
    void animationAddsToTheCodePose() {
        Pose base = Pose.REST.with("lid", PartTransform.rotation(5, 0, 0).withScale(2, 2, 2));
        PartTransform lid = file.get("animation.test_chest.open").sample(0.5f, base).get("lid");
        assertEquals(95f, lid.rx(), EPS);
        assertEquals(2f, lid.sx(), EPS);
    }

    @Test
    void molangGivesAWarningAndANeutralValue() {
        assertEquals(1, file.warnings().size());
        assertTrue(file.warnings().get(0).contains("snap, bone lid, position"), file.warnings().get(0));
        PartTransform lid = file.get("animation.test_chest.snap").sample(0.1f).get("lid");
        assertEquals(0f, lid.tx(), EPS);
        assertEquals(1f, lid.tz(), EPS, "number strings are read");
    }

    @Test
    void brokenFilesSayWhere() {
        AnimationException e = assertThrows(AnimationException.class, () -> AnimationFile.parse("t:x",
                "{\"animations\": {\"a\": {\"bones\": {\"b\": {\"rotation\": {\"zero\": [0, 0, 0]}}}}}}"));
        assertTrue(e.getMessage().contains("t:x a, bone b, rotation"), e.getMessage());
        assertThrows(AnimationException.class, () -> AnimationFile.parse("t:x", "{\"animations\": {\"a\": {\"bones\": "
                + "{\"b\": {\"scale\": [1, 2]}}}}}"));
        assertThrows(AnimationException.class, () -> AnimationFile.parse("t:x", "{}"));
    }

    @Test
    void playerCrossfadesAndRestarts() {
        Animation open = file.get("animation.test_chest.open");
        Animation snap = file.get("animation.test_chest.snap");
        AnimationPlayer player = new AnimationPlayer();
        player.play(open, 10);
        assertEquals(90f, player.pose(11, Pose.REST).get("lid").rx(), EPS);
        player.play(snap, 11, 0.2f);
        // 0.1 s in: snap is at 0 (step), open still at 90; halfway through the fade.
        assertEquals(45f, player.pose(11.1f, Pose.REST).get("lid").rx(), EPS);
        assertEquals(45f, player.pose(11.3f, Pose.REST).get("lid").rx(), EPS);
        assertTrue(player.playing(11.3f));
        assertFalse(player.playing(12));
        player.ensure(snap, 20, 0);
        assertFalse(player.playing(20), "ensure doesn't restart the same animation");
        player.stop();
        assertSame(Pose.REST, player.pose(30, Pose.REST));
    }

    /** Every animation file the dev client ships reads without warnings. */
    @Test
    void devAnimationsAreClean() throws IOException {
        Path dir = Path.of("../bta/src/main/resources/assets/jsonextended/animations");
        assumeTrue(Files.isDirectory(dir), "bta/ is not next to core/");
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".animation.json")).toList()) {
                AnimationFile f = AnimationFile.parse(p.getFileName().toString(), Files.readString(p));
                assertEquals(List.of(), f.warnings(), p.toString());
                assertTrue(f.animations().size() > 0, p.toString());
            }
        }
    }

    @Test
    void devChestAnimationsEndWhereTheyShould() throws IOException {
        Path p = Path.of("../bta/src/main/resources/assets/jsonextended/animations/test_chest.animation.json");
        assumeTrue(Files.isRegularFile(p), "bta/ is not next to core/");
        AnimationFile f = AnimationFile.parse("dev", Files.readString(p));
        assertEquals(90f, f.get("animation.test_chest.open").sample(5).get("lid").rx(), EPS);
        assertEquals(0f, f.get("animation.test_chest.close").sample(5).get("lid").rx(), EPS);
        assertEquals(0f, f.get("animation.test_chest.idle").sample(2).get("knob").rz(), EPS);
    }
}
