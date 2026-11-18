package io.github.tryrodave.jsonextended.pose;

/**
 * One four-corner face of a model, ready to pose and draw. Positions are in blocks (0 to 1 fills the block), UVs
 * 0 to 1 across the texture (V down, as model JSON), in counter-clockwise order seen from the front.
 *
 * @param element  index of the element (JSON) or mesh (OBJ) it came from, for {@link Rig}
 * @param texture  resolved texture id, or null when unset (draw the particle texture)
 * @param cullFace the block side named by the face's {@code cullface} ({@code down} ... {@code east}), or null
 */
public record ModelQuad(int element, float[][] positions, float[][] uvs, float[] normal, String texture,
                        int tintIndex, boolean shade, String cullFace) {
    /** The same face with new corners and normal. */
    public ModelQuad moved(float[][] positions, float[] normal) {
        return new ModelQuad(element, positions, uvs, normal, texture, tintIndex, shade, cullFace);
    }
}
