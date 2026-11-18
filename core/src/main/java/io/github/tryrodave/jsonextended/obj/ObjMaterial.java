package io.github.tryrodave.jsonextended.obj;

/**
 * One material from an MTL file ({@code newmtl}), with the parts a block model uses.
 *
 * @param color     {@code Kd}, the diffuse colour as red, green, blue from 0 to 1; white when absent
 * @param ambient   {@code Ka}, the ambient colour's brightness from 0 to 1 (its largest channel); 1 makes a face
 *                  full bright when the model JSON turns {@code emissive_ambient} on
 * @param texture   {@code map_Kd}: a texture id ({@code ns:block/name}) or a {@code #variable} for the model
 *                  JSON's {@code textures}; null when absent
 * @param alpha     {@code d}, or 1 - {@code Tr}; 1 when absent
 * @param tintIndex {@code jsonextended_TintIndex} (also {@code betafront_}, {@code neoforge_} and {@code forge_}
 *                  spellings); -1 for no tint
 */
public record ObjMaterial(String name, float[] color, float ambient, String texture, float alpha, int tintIndex) {
    static final float[] WHITE = {1, 1, 1};

    /** The material an unknown {@code usemtl #variable} stands for: white, drawn with that texture variable. */
    static ObjMaterial forVariable(String name) {
        return new ObjMaterial(name, WHITE, 0, name, 1, -1);
    }
}
