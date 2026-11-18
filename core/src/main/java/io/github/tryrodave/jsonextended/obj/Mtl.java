package io.github.tryrodave.jsonextended.obj;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads an MTL material library, written from the MTL specification. Unknown statements are ignored. */
public final class Mtl {
    private static final Set<String> TINT_KEYS = Set.of("jsonextended_TintIndex", "betafront_TintIndex",
            "neoforge_TintIndex", "forge_TintIndex");

    private Mtl() {}

    /** Materials by name, in file order. {@code id} names the file in error messages. */
    public static Map<String, ObjMaterial> parse(String id, String text) {
        Map<String, ObjMaterial> materials = new LinkedHashMap<>();
        Builder current = null;
        for (ObjLines.Line line : ObjLines.split(text)) {
            String keyword = line.keyword();
            if (keyword.equals("newmtl")) {
                if (line.args() < 1)
                    throw ObjException.at(id, line.number(), "newmtl needs a name");
                if (current != null)
                    materials.put(current.name, current.build());
                current = new Builder(String.join(" ", List.of(line.tokens()).subList(1, line.tokens().length)));
                continue;
            }
            if (current == null)
                continue;
            switch (keyword) {
                case "Kd" -> current.color = colour(id, line);
                case "Ka" -> {
                    float[] ka = colour(id, line);
                    current.ambient = Math.max(ka[0], Math.max(ka[1], ka[2]));
                }
                case "map_Kd" -> {
                    if (line.args() < 1)
                        throw ObjException.at(id, line.number(), "map_Kd needs a texture");
                    // Options like -s 1 1 1 come first; the texture is the last argument.
                    current.texture = line.tokens()[line.args()];
                }
                case "d" -> current.alpha = number(id, line);
                case "Tr" -> current.alpha = 1 - number(id, line);
                default -> {
                    if (TINT_KEYS.contains(keyword))
                        current.tintIndex = (int) number(id, line);
                }
            }
        }
        if (current != null)
            materials.put(current.name, current.build());
        return materials;
    }

    private static float number(String id, ObjLines.Line line) {
        if (line.args() < 1)
            throw ObjException.at(id, line.number(), line.keyword() + " needs a number");
        return ObjLines.number(id, line, 1);
    }

    /** {@code Kd r g b}; one number means grey. {@code spectral} and {@code xyz} forms aren't supported. */
    private static float[] colour(String id, ObjLines.Line line) {
        if (line.args() < 1)
            throw ObjException.at(id, line.number(), line.keyword() + " needs a colour");
        if (line.tokens()[1].equals("spectral") || line.tokens()[1].equals("xyz"))
            throw ObjException.at(id, line.number(), line.keyword() + " " + line.tokens()[1] + " isn't supported; use r g b");
        float r = ObjLines.number(id, line, 1);
        if (line.args() < 3)
            return new float[] {r, r, r};
        return new float[] {r, ObjLines.number(id, line, 2), ObjLines.number(id, line, 3)};
    }

    private static final class Builder {
        final String name;
        float[] color = ObjMaterial.WHITE;
        float ambient;
        String texture;
        float alpha = 1;
        int tintIndex = -1;

        Builder(String name) {
            this.name = name;
        }

        ObjMaterial build() {
            return new ObjMaterial(name, color, ambient, texture, alpha, tintIndex);
        }
    }
}
