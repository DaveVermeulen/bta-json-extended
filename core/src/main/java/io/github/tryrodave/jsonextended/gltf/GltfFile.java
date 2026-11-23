package io.github.tryrodave.jsonextended.gltf;

import io.github.tryrodave.jsonextended.json.Json;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * A glTF 2.0 file: a {@code .glb} (one binary file: a JSON chunk and a BIN chunk) or a {@code .gltf} (JSON, with
 * buffers in {@code .bin} files next to it or in {@code data:} URIs). Reads the JSON and the buffers, and accessors
 * as floats or ints. Images are not read: textures come from the model JSON (see {@link GltfModelJson}).
 */
public final class GltfFile {
    private static final int GLB_MAGIC = 0x46546C67; // "glTF"
    private static final int CHUNK_JSON = 0x4E4F534A;
    private static final int CHUNK_BIN = 0x004E4942;
    /** Extensions that don't change what we read, so a file may require them. */
    private static final Set<String> HARMLESS_EXTENSIONS = Set.of("KHR_materials_unlit",
            "KHR_materials_emissive_strength", "KHR_materials_specular", "KHR_materials_ior");

    private final String id;
    private final Map<String, Object> json;
    private final List<ByteBuffer> buffers;

    private GltfFile(String id, Map<String, Object> json, List<ByteBuffer> buffers) {
        this.id = id;
        this.json = json;
        this.buffers = buffers;
    }

    /**
     * @param id     the file id ({@code ns:path/model.glb}), for messages and to find {@code .bin} files next to it
     * @param bytes  the file's bytes
     * @param source a file id to its bytes, or null when absent (for a {@code .gltf}'s external buffers)
     */
    @SuppressWarnings("unchecked")
    public static GltfFile parse(String id, byte[] bytes, Function<String, byte[]> source) {
        String text;
        ByteBuffer glbBin = null;
        if (bytes.length >= 4 && le(bytes).getInt(0) == GLB_MAGIC) {
            ByteBuffer b = le(bytes);
            if (bytes.length < 20)
                throw new GltfException(id + ": GLB file is too short");
            int version = b.getInt(4);
            if (version != 2)
                throw new GltfException(id + ": GLB version " + version + " (only glTF 2.0 is read)");
            int length = Math.min(b.getInt(8), bytes.length);
            int at = 12;
            text = null;
            while (at + 8 <= length) {
                int chunkLength = b.getInt(at), type = b.getInt(at + 4);
                int start = at + 8;
                if (chunkLength < 0 || start + chunkLength > length)
                    throw new GltfException(id + ": GLB chunk at byte " + at + " runs past the end of the file");
                if (type == CHUNK_JSON && text == null)
                    text = new String(bytes, start, chunkLength, StandardCharsets.UTF_8);
                else if (type == CHUNK_BIN && glbBin == null)
                    glbBin = le(bytes).position(start).limit(start + chunkLength).slice().order(ByteOrder.LITTLE_ENDIAN);
                at = start + chunkLength;
            }
            if (text == null)
                throw new GltfException(id + ": GLB file has no JSON chunk");
        } else {
            text = new String(bytes, StandardCharsets.UTF_8);
            if (!text.isEmpty() && text.charAt(0) == '﻿')
                text = text.substring(1);
        }

        Object root;
        try {
            root = Json.parse(text.stripTrailing().replace("\0", ""));
        } catch (IllegalArgumentException e) {
            throw new GltfException(id + ": " + e.getMessage());
        }
        if (!(root instanceof Map<?, ?> map))
            throw new GltfException(id + ": expected a JSON object");
        Map<String, Object> json = (Map<String, Object>) map;

        if (json.get("asset") instanceof Map<?, ?> asset && asset.get("version") instanceof String version
                && !version.startsWith("2."))
            throw new GltfException(id + ": asset.version " + version + " (only glTF 2.0 is read)");
        for (Object ext : list(json, "extensionsRequired"))
            if (!HARMLESS_EXTENSIONS.contains(ext))
                throw new GltfException(id + ": extensionsRequired: " + ext + " isn't supported (export without it,"
                        + " e.g. no Draco compression or mesh quantization)");

        List<ByteBuffer> buffers = new ArrayList<>();
        List<Object> bufferList = list(json, "buffers");
        for (int i = 0; i < bufferList.size(); i++) {
            String path = "buffers[" + i + "]";
            Map<?, ?> buffer = object(id, bufferList.get(i), path);
            Object uri = buffer.get("uri");
            ByteBuffer data;
            if (uri == null) {
                if (glbBin == null)
                    throw new GltfException(id + ": " + path + ": no uri and no GLB BIN chunk");
                data = glbBin;
            } else if (uri instanceof String s && s.startsWith("data:")) {
                int comma = s.indexOf(',');
                if (comma < 0 || !s.substring(0, comma).endsWith(";base64"))
                    throw new GltfException(id + ": " + path + ".uri: only base64 data URIs are read");
                try {
                    data = le(Base64.getDecoder().decode(s.substring(comma + 1)));
                } catch (IllegalArgumentException e) {
                    throw new GltfException(id + ": " + path + ".uri: bad base64");
                }
            } else if (uri instanceof String s) {
                String file = sibling(id, decodeUri(s));
                byte[] content = source == null ? null : source.apply(file);
                if (content == null)
                    throw new GltfException(id + ": " + path + ".uri: no such file " + file);
                data = le(content);
            } else {
                throw new GltfException(id + ": " + path + ".uri: expected a string");
            }
            int byteLength = integer(id, buffer, "byteLength", path, -1);
            if (byteLength > data.capacity())
                throw new GltfException(id + ": " + path + ": byteLength " + byteLength + " but the data has "
                        + data.capacity() + " bytes");
            buffers.add(data);
        }
        return new GltfFile(id, json, buffers);
    }

    public String id() {
        return id;
    }

    /** The top-level JSON. */
    public Map<String, Object> json() {
        return json;
    }

    /** A top-level array ({@code nodes}, {@code meshes}, ...), empty when absent. */
    public List<Object> array(String key) {
        return list(json, key);
    }

    /**
     * An accessor's elements as floats, {@code count * components} long. Normalized integers become 0 to 1 (or -1
     * to 1); other integers keep their value.
     */
    public float[] floats(int accessor, String path) {
        Accessor a = accessor(accessor, path);
        float[] out = new float[a.count * a.components];
        for (int i = 0; i < a.count; i++) {
            int at = a.offset + i * a.stride;
            for (int c = 0; c < a.components; c++) {
                int p = at + c * a.componentSize;
                float v = switch (a.componentType) {
                    case 5126 -> a.data.getFloat(p);
                    case 5120 -> a.normalized ? Math.max(a.data.get(p) / 127f, -1f) : a.data.get(p);
                    case 5121 -> a.normalized ? (a.data.get(p) & 0xFF) / 255f : a.data.get(p) & 0xFF;
                    case 5122 -> a.normalized ? Math.max(a.data.getShort(p) / 32767f, -1f) : a.data.getShort(p);
                    case 5123 -> a.normalized ? (a.data.getShort(p) & 0xFFFF) / 65535f : a.data.getShort(p) & 0xFFFF;
                    default -> (float) (a.data.getInt(p) & 0xFFFFFFFFL);
                };
                out[i * a.components + c] = v;
            }
        }
        return out;
    }

    /** An accessor of unsigned integers (indices), {@code count * components} long. */
    public int[] ints(int accessor, String path) {
        Accessor a = accessor(accessor, path);
        if (a.componentType != 5121 && a.componentType != 5123 && a.componentType != 5125)
            throw new GltfException(id + ": " + path + ": indices must be unsigned byte, short or int");
        int[] out = new int[a.count * a.components];
        for (int i = 0; i < a.count; i++) {
            int at = a.offset + i * a.stride;
            for (int c = 0; c < a.components; c++) {
                int p = at + c * a.componentSize;
                out[i * a.components + c] = switch (a.componentType) {
                    case 5121 -> a.data.get(p) & 0xFF;
                    case 5123 -> a.data.getShort(p) & 0xFFFF;
                    default -> a.data.getInt(p);
                };
            }
        }
        return out;
    }

    /** Number of components of an accessor's type (SCALAR 1, VEC2 2, ...). */
    public int components(int accessor, String path) {
        return accessor(accessor, path).components;
    }

    private record Accessor(ByteBuffer data, int offset, int stride, int count, int components, int componentType,
                            int componentSize, boolean normalized) {}

    private Accessor accessor(int index, String path) {
        List<Object> accessors = array("accessors");
        if (index < 0 || index >= accessors.size())
            throw new GltfException(id + ": " + path + ": accessor " + index + " doesn't exist");
        String at = "accessors[" + index + "]";
        Map<?, ?> a = object(id, accessors.get(index), at);
        if (a.containsKey("sparse"))
            throw new GltfException(id + ": " + at + ": sparse accessors aren't supported");
        int componentType = integer(id, a, "componentType", at, -1);
        int componentSize = switch (componentType) {
            case 5120, 5121 -> 1;
            case 5122, 5123 -> 2;
            case 5125, 5126 -> 4;
            default -> throw new GltfException(id + ": " + at + ".componentType: " + componentType + " isn't a glTF type");
        };
        int components = switch (a.get("type") instanceof String t ? t : "") {
            case "SCALAR" -> 1;
            case "VEC2" -> 2;
            case "VEC3" -> 3;
            case "VEC4", "MAT2" -> 4;
            case "MAT3" -> 9;
            case "MAT4" -> 16;
            default -> throw new GltfException(id + ": " + at + ".type: expected SCALAR, VEC2, VEC3, VEC4 or MATn");
        };
        int count = integer(id, a, "count", at, -1);
        boolean normalized = Boolean.TRUE.equals(a.get("normalized"));
        int elementSize = components * componentSize;
        if (!a.containsKey("bufferView"))
            return new Accessor(ByteBuffer.allocate(count * elementSize).order(ByteOrder.LITTLE_ENDIAN), 0,
                    elementSize, count, components, componentType, componentSize, normalized); // all zeros, per spec

        int viewIndex = integer(id, a, "bufferView", at, -1);
        List<Object> views = array("bufferViews");
        if (viewIndex < 0 || viewIndex >= views.size())
            throw new GltfException(id + ": " + at + ".bufferView: " + viewIndex + " doesn't exist");
        String vp = "bufferViews[" + viewIndex + "]";
        Map<?, ?> view = object(id, views.get(viewIndex), vp);
        int bufferIndex = integer(id, view, "buffer", vp, -1);
        if (bufferIndex < 0 || bufferIndex >= buffers.size())
            throw new GltfException(id + ": " + vp + ".buffer: " + bufferIndex + " doesn't exist");
        ByteBuffer data = buffers.get(bufferIndex);
        int viewOffset = integer(id, view, "byteOffset", vp, 0);
        int viewLength = integer(id, view, "byteLength", vp, -1);
        int stride = integer(id, view, "byteStride", vp, elementSize);
        int offset = viewOffset + integer(id, a, "byteOffset", at, 0);
        long end = count == 0 ? offset : (long) offset + (long) (count - 1) * stride + elementSize;
        if (viewOffset + (long) viewLength > data.capacity() || end > viewOffset + (long) viewLength)
            throw new GltfException(id + ": " + at + ": reads past the end of its buffer");
        return new Accessor(data, offset, stride, count, components, componentType, componentSize, normalized);
    }

    /** {@code ns:path/model.gltf} and {@code model.bin} to {@code ns:path/model.bin}. */
    static String sibling(String id, String name) {
        int slash = id.lastIndexOf('/');
        int colon = id.indexOf(':');
        String dir = slash >= 0 ? id.substring(0, slash + 1) : id.substring(0, colon + 1);
        String path = dir + name;
        // Resolve ./ and ../ so "../textures/x.bin" works.
        String prefix = path.substring(0, path.indexOf(':') + 1);
        List<String> parts = new ArrayList<>();
        for (String part : path.substring(prefix.length()).split("/")) {
            if (part.equals("..")) {
                if (!parts.isEmpty())
                    parts.remove(parts.size() - 1);
            } else if (!part.equals(".") && !part.isEmpty()) {
                parts.add(part);
            }
        }
        return prefix + String.join("/", parts);
    }

    private static String decodeUri(String uri) {
        try {
            return java.net.URLDecoder.decode(uri.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return uri;
        }
    }

    static List<Object> list(Map<?, ?> json, String key) {
        @SuppressWarnings("unchecked")
        List<Object> list = json.get(key) instanceof List<?> l ? (List<Object>) l : List.of();
        return list;
    }

    static Map<?, ?> object(String id, Object value, String path) {
        if (value instanceof Map<?, ?> map)
            return map;
        throw new GltfException(id + ": " + path + ": expected an object");
    }

    /** A whole number at {@code key}; {@code fallback} when absent, and an error when absent with fallback -1. */
    static int integer(String id, Map<?, ?> json, String key, String path, int fallback) {
        Object value = json.get(key);
        if (value == null) {
            if (fallback == -1)
                throw new GltfException(id + ": " + path + "." + key + ": missing");
            return fallback;
        }
        if (value instanceof Double d && d == Math.floor(d) && d >= 0 && d <= Integer.MAX_VALUE)
            return d.intValue();
        throw new GltfException(id + ": " + path + "." + key + ": expected a whole number, 0 or more");
    }

    private static ByteBuffer le(byte[] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    }
}
