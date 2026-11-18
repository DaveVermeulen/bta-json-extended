package io.github.tryrodave.jsonextended.model;

import io.github.tryrodave.jsonextended.pose.ModelQuad;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns model JSON {@code elements} into quads the way vanilla Minecraft bakes them: corner order and default UVs
 * per side, face {@code rotation}, element {@code rotation} about its {@code origin} with {@code rescale}. For
 * drawing a model ourselves (posed tile entities); blocks still go through BTA's own reader.
 */
public final class ElementQuads {
    private ElementQuads() {}

    private static final String[] SIDES = {"down", "up", "north", "south", "west", "east"};

    /** Every face of every element, in element then side order. Faces without a usable shape are skipped. */
    public static List<ModelQuad> bake(ResolvedModel model) {
        List<ModelQuad> quads = new ArrayList<>();
        for (int i = 0; i < model.elements().size(); i++) {
            Map<?, ?> element = model.elements().get(i);
            float[] from = vec(element.get("from")), to = vec(element.get("to"));
            if (from == null || to == null || !(element.get("faces") instanceof Map<?, ?> faces))
                continue;
            boolean shade = !(element.get("shade") instanceof Boolean b) || b;
            for (String side : SIDES) {
                if (faces.get(side) instanceof Map<?, ?> face)
                    quads.add(face(model, i, side, face, from, to, element.get("rotation"), shade));
            }
        }
        return quads;
    }

    private static ModelQuad face(ResolvedModel model, int index, String side, Map<?, ?> face, float[] from, float[] to,
                                  Object rotation, boolean shade) {
        float x0 = from[0] / 16, y0 = from[1] / 16, z0 = from[2] / 16;
        float x1 = to[0] / 16, y1 = to[1] / 16, z1 = to[2] / 16;
        float[][] p = switch (side) {
            case "down" -> new float[][] {{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}};
            case "up" -> new float[][] {{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}};
            case "north" -> new float[][] {{x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}};
            case "south" -> new float[][] {{x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}};
            case "west" -> new float[][] {{x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}};
            default -> new float[][] {{x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}};
        };
        float[] n = switch (side) {
            case "down" -> new float[] {0, -1, 0};
            case "up" -> new float[] {0, 1, 0};
            case "north" -> new float[] {0, 0, -1};
            case "south" -> new float[] {0, 0, 1};
            case "west" -> new float[] {-1, 0, 0};
            default -> new float[] {1, 0, 0};
        };

        float[] uv = face.get("uv") instanceof List<?> list && list.size() == 4 ? vec4(list) : defaultUv(side, from, to);
        // Corners in vertex order: (u0,v0), (u0,v1), (u1,v1), (u1,v0); face rotation shifts which one each vertex takes.
        float[][] corners = {{uv[0], uv[1]}, {uv[0], uv[3]}, {uv[2], uv[3]}, {uv[2], uv[1]}};
        int shift = face.get("rotation") instanceof Double r ? Math.floorMod(r.intValue() / 90, 4) : 0;
        float[][] uvs = new float[4][];
        for (int i = 0; i < 4; i++) {
            float[] c = corners[(i + shift) % 4];
            uvs[i] = new float[] {c[0] / 16, c[1] / 16};
        }

        if (rotation instanceof Map<?, ?> r)
            rotate(p, n, r);

        String texture = face.get("texture") instanceof String t ? model.texture(t) : null;
        int tint = face.get("tintindex") instanceof Double t ? t.intValue() : -1;
        String cull = face.get("cullface") instanceof String c ? switch (c) {
            case "bottom" -> "down";
            case "top" -> "up";
            default -> c;
        } : null;
        return new ModelQuad(index, p, uvs, n, texture, tint, shade, cull);
    }

    /** Vanilla's UVs for a face without {@code uv}: the element's extent on that side of a 16x16 texture. */
    static float[] defaultUv(String side, float[] f, float[] t) {
        return switch (side) {
            case "down" -> new float[] {f[0], 16 - t[2], t[0], 16 - f[2]};
            case "up" -> new float[] {f[0], f[2], t[0], t[2]};
            case "north" -> new float[] {16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]};
            case "south" -> new float[] {f[0], 16 - t[1], t[0], 16 - f[1]};
            case "west" -> new float[] {f[2], 16 - t[1], t[2], 16 - f[1]};
            default -> new float[] {16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]};
        };
    }

    /** Element rotation: {@code angle} degrees about {@code axis} through {@code origin}; {@code rescale} stretches back. */
    private static void rotate(float[][] p, float[] n, Map<?, ?> r) {
        if (!(r.get("angle") instanceof Double angle) || angle == 0 || !(r.get("axis") instanceof String axis))
            return;
        float[] origin = vec(r.get("origin"));
        float ox = origin == null ? 0.5f : origin[0] / 16, oy = origin == null ? 0.5f : origin[1] / 16,
                oz = origin == null ? 0.5f : origin[2] / 16;
        int a = switch (axis) {
            case "x" -> 0;
            case "y" -> 1;
            default -> 2;
        };
        double rad = Math.toRadians(angle);
        float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);
        float scale = Boolean.TRUE.equals(r.get("rescale")) ? 1f / Math.abs(cos) : 1f;
        for (float[] v : p) {
            float[] d = {v[0] - ox, v[1] - oy, v[2] - oz};
            float[] q = turn(d, a, cos, sin);
            for (int i = 0; i < 3; i++)
                if (i != a)
                    q[i] *= scale;
            v[0] = q[0] + ox;
            v[1] = q[1] + oy;
            v[2] = q[2] + oz;
        }
        float[] m = turn(n, a, cos, sin);
        System.arraycopy(m, 0, n, 0, 3);
    }

    /** Right-hand rotation of {@code v} about axis {@code a} (0 x, 1 y, 2 z). */
    static float[] turn(float[] v, int a, float cos, float sin) {
        return switch (a) {
            case 0 -> new float[] {v[0], v[1] * cos - v[2] * sin, v[1] * sin + v[2] * cos};
            case 1 -> new float[] {v[0] * cos + v[2] * sin, v[1], -v[0] * sin + v[2] * cos};
            default -> new float[] {v[0] * cos - v[1] * sin, v[0] * sin + v[1] * cos, v[2]};
        };
    }

    private static float[] vec(Object o) {
        if (!(o instanceof List<?> l) || l.size() != 3)
            return null;
        float[] v = new float[3];
        for (int i = 0; i < 3; i++) {
            if (!(l.get(i) instanceof Double d))
                return null;
            v[i] = d.floatValue();
        }
        return v;
    }

    private static float[] vec4(List<?> l) {
        float[] v = new float[4];
        for (int i = 0; i < 4; i++)
            v[i] = l.get(i) instanceof Double d ? d.floatValue() : 0;
        return v;
    }
}
