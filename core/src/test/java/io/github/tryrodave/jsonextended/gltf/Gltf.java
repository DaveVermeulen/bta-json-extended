package io.github.tryrodave.jsonextended.gltf;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/** Builds small glTF and GLB files for tests: accessors go into one buffer, the rest of the JSON is written by hand. */
final class Gltf {
    private final ByteArrayOutputStream bin = new ByteArrayOutputStream();
    private final List<String> views = new ArrayList<>();
    private final List<String> accessors = new ArrayList<>();

    /** A float accessor of {@code type} (VEC3, VEC2, ...); returns its index. */
    int floats(String type, float... values) {
        ByteBuffer b = ByteBuffer.allocate(values.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : values)
            b.putFloat(v);
        return add(b.array(), type, 5126, values.length / size(type), false);
    }

    /** An unsigned short index accessor. */
    int indices(int... values) {
        ByteBuffer b = ByteBuffer.allocate(values.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int v : values)
            b.putShort((short) v);
        return add(b.array(), "SCALAR", 5123, values.length, false);
    }

    /** A normalized unsigned byte accessor. */
    int normalizedBytes(String type, int... values) {
        byte[] b = new byte[values.length];
        for (int i = 0; i < values.length; i++)
            b[i] = (byte) values[i];
        return add(b, type, 5121, values.length / size(type), true);
    }

    private int add(byte[] data, String type, int componentType, int count, boolean normalized) {
        while (bin.size() % 4 != 0)
            bin.write(0);
        views.add("{\"buffer\":0,\"byteOffset\":" + bin.size() + ",\"byteLength\":" + data.length + "}");
        bin.writeBytes(data);
        accessors.add(String.format(Locale.ROOT, "{\"bufferView\":%d,\"componentType\":%d,\"count\":%d,\"type\":\"%s\"%s}",
                views.size() - 1, componentType, count, type, normalized ? ",\"normalized\":true" : ""));
        return accessors.size() - 1;
    }

    private static int size(String type) {
        return switch (type) {
            case "SCALAR" -> 1;
            case "VEC2" -> 2;
            case "VEC3" -> 3;
            default -> 4;
        };
    }

    /** The JSON with {@code rest} (the other top-level keys, without braces) and a buffer at {@code bufferUri} (null for GLB). */
    String json(String rest, String bufferUri) {
        String buffer = "{\"byteLength\":" + bin.size() + (bufferUri == null ? "" : ",\"uri\":\"" + bufferUri + "\"") + "}";
        return "{\"asset\":{\"version\":\"2.0\"},\"buffers\":[" + buffer + "],\"bufferViews\":[" + String.join(",", views)
                + "],\"accessors\":[" + String.join(",", accessors) + "]" + (rest.isEmpty() ? "" : "," + rest) + "}";
    }

    byte[] bin() {
        return bin.toByteArray();
    }

    String dataUri() {
        return "data:application/octet-stream;base64," + Base64.getEncoder().encodeToString(bin());
    }

    /** A GLB: header, JSON chunk (space padded), BIN chunk (zero padded). */
    byte[] glb(String rest) {
        byte[] json = json(rest, null).getBytes(StandardCharsets.UTF_8);
        int jsonLength = (json.length + 3) / 4 * 4;
        byte[] data = bin();
        int binLength = (data.length + 3) / 4 * 4;
        ByteBuffer b = ByteBuffer.allocate(12 + 8 + jsonLength + 8 + binLength).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0x46546C67).putInt(2).putInt(b.capacity());
        b.putInt(jsonLength).putInt(0x4E4F534A).put(json);
        for (int i = json.length; i < jsonLength; i++)
            b.put((byte) ' ');
        b.putInt(binLength).putInt(0x004E4942).put(data);
        return b.array();
    }

    /** A unit cube (0 to 1) as Blockbench and Blender export it: 4 corners per side, two triangles each. */
    static float[] cubePositions() {
        return new float[] {
                // up (+Y)
                0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0,
                // down (-Y)
                0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1,
                // north (-Z)
                0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0,
                // south (+Z)
                0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1,
                // west (-X)
                0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0,
                // east (+X)
                1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1};
    }

    static int[] cubeIndices() {
        int[] idx = new int[36];
        for (int f = 0; f < 6; f++) {
            int v = f * 4, i = f * 6;
            idx[i] = v;
            idx[i + 1] = v + 1;
            idx[i + 2] = v + 2;
            idx[i + 3] = v;
            idx[i + 4] = v + 2;
            idx[i + 5] = v + 3;
        }
        return idx;
    }

    static float[] cubeUvs() {
        float[] uv = new float[48];
        float[] face = {0, 0, 0, 1, 1, 1, 1, 0};
        for (int f = 0; f < 6; f++)
            System.arraycopy(face, 0, uv, f * 8, 8);
        return uv;
    }
}
