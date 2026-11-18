package io.github.tryrodave.jsonextended.pose;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.tryrodave.jsonextended.model.ElementQuads;
import io.github.tryrodave.jsonextended.model.ResolvedModel;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** test_chest.json: base (element 0, no group), lid group (element 1, hinge at the back top) with a knob group. */
class RigTest {
    private static String source(String id) {
        try (InputStream in = RigTest.class.getResourceAsStream("/pose/" + id.substring(id.lastIndexOf('/') + 1) + ".json")) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private final ResolvedModel chest = ResolvedModel.load("t:block/test_chest", RigTest::source);
    private final List<ModelQuad> quads = ElementQuads.bake(chest);
    private final Rig rig = Rig.fromGroups(chest.groups(), chest.elements().size());

    @Test
    void groupsBecomeParts() {
        assertEquals(List.of("", "lid", "knob"), List.copyOf(rig.names()));
        assertEquals(List.of(0), rig.root().elements());
        assertArrayEquals(new float[] {0.5f, 9 / 16f, 15 / 16f}, rig.part("lid").origin());
        assertEquals(List.of(2), rig.part("knob").elements());
        assertEquals(18, quads.size());
    }

    @Test
    void restPoseChangesNothing() {
        List<ModelQuad> posed = rig.pose(quads, Pose.REST);
        for (int i = 0; i < quads.size(); i++)
            assertSame(quads.get(i), posed.get(i));
    }

    @Test
    void openingTheLidSwingsItUpAboutTheHinge() {
        // Lid north face, corner 0 (x 15, y 14, z 1) is (y +5, z -14) from the hinge (y 9, z 15).
        // Right-hand -90 about X maps (y, z) to (z, -y) = (-14, -5): the lid swings down into the base.
        ModelQuad lidNorth = lidFace("north");
        Pose open = Pose.REST.with("lid", PartTransform.rotation(-90, 0, 0));
        ModelQuad moved = rig.pose(quads, open).get(quads.indexOf(lidNorth));
        float[] corner = moved.positions()[0];
        assertArrayEquals(new float[] {15 / 16f, (9 - 14) / 16f, (15 - 5) / 16f}, corner, 1e-6f);
        // +90 maps (y, z) to (-z, y) = (+14, +5): the lid opens up and back.
        ModelQuad up = rig.pose(quads, Pose.REST.with("lid", PartTransform.rotation(90, 0, 0))).get(quads.indexOf(lidNorth));
        assertArrayEquals(new float[] {15 / 16f, (9 + 14) / 16f, (15 + 5) / 16f}, up.positions()[0], 1e-6f);
        // The north face now looks up.
        assertArrayEquals(new float[] {0, 1, 0}, up.normal(), 1e-6f);
    }

    @Test
    void childPartsFollowTheirParent() {
        ModelQuad knobNorth = quads.stream().filter(q -> q.element() == 2 && q.normal()[2] == -1).findFirst().orElseThrow();
        ModelQuad moved = rig.pose(quads, Pose.REST.with("lid", PartTransform.rotation(90, 0, 0))).get(quads.indexOf(knobNorth));
        // Knob top-front corner (x 9, y 11, z 0): relative to the hinge (y +2, z -15) -> (y +15, z +2).
        assertArrayEquals(new float[] {9 / 16f, (9 + 15) / 16f, (15 + 2) / 16f}, moved.positions()[0], 1e-6f);
        // The base doesn't move.
        ModelQuad baseUp = quads.stream().filter(q -> q.element() == 0 && q.normal()[1] == 1).findFirst().orElseThrow();
        assertSame(baseUp, rig.pose(quads, Pose.REST.with("lid", PartTransform.rotation(90, 0, 0))).get(quads.indexOf(baseUp)));
    }

    @Test
    void translationIsInPixelsAndScaleIsAboutThePivot() {
        ModelQuad lidNorth = lidFace("north");
        int i = quads.indexOf(lidNorth);
        ModelQuad lifted = rig.pose(quads, Pose.REST.with("lid", PartTransform.translation(0, 2, 0))).get(i);
        assertArrayEquals(new float[] {15 / 16f, 16 / 16f, 1 / 16f}, lifted.positions()[0], 1e-6f);
        ModelQuad half = rig.pose(quads, Pose.REST.with("lid", PartTransform.NONE.withScale(0.5f, 0.5f, 0.5f))).get(i);
        assertArrayEquals(new float[] {11.5f / 16, 11.5f / 16, 8 / 16f}, half.positions()[0], 1e-6f);
    }

    @Test
    void rotationAppliesXThenYThenZ() {
        // A point on +Y, turned 90 about X goes to +Z, then 90 about Y goes to +X.
        float[] p = Mat4.rotation(90, 90, 0).point(new float[] {0, 1, 0});
        assertArrayEquals(new float[] {1, 0, 0}, p, 1e-6f);
    }

    @Test
    void meshesBecomePartsForObj() {
        Rig obj = Rig.fromMeshes(List.of("Body", "Lid", "", "Lid"), Map.of("Lid", new float[] {0.5f, 0.5f, 1}));
        assertEquals(List.of("", "Body", "Lid"), List.copyOf(obj.names()));
        assertEquals(List.of(1, 3), obj.part("Lid").elements());
        assertEquals(List.of(2), obj.root().elements());
    }

    private ModelQuad lidFace(String side) {
        int axis = side.equals("north") ? 2 : 1;
        return quads.stream().filter(q -> q.element() == 1 && q.normal()[axis] == (side.equals("north") ? -1 : 1))
                .findFirst().orElseThrow();
    }

    @Test
    void lidOpensHalfwayWithLerp() {
        PartTransform half = PartTransform.lerp(PartTransform.NONE, PartTransform.rotation(90, 0, 0), 0.5f);
        assertEquals(45f, half.rx());
        assertEquals(1f, half.sx());
    }
}
