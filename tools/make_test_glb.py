#!/usr/bin/env python3
"""Writes the dev-only GLB test models in bta/src/main/resources/assets/jsonextended/models/block/:
test_glb_lantern.glb (static) and test_glb_animated_lantern.glb (the same, with animations).

A hand-made glTF 2.0 file, so the loader has a fixture that doesn't depend on any exporter:
- node "base": a 0.75 x 0.25 x 0.75 box built around the origin (the model JSON's offset moves it to the block centre),
  material "base";
- node "gem", a child of "base": an octahedron 0.6 up, turned 45 degrees about Y and scaled 0.3, material "gem" with
  emissiveFactor 1 (it glows with emissive_ambient).
The animated one adds two animations on the gem: "idle" (one slow turn in 4 s, linear rotation keys, and a bob up and
down as a cubic spline) and "spin" (a fast turn in 0.5 s, and the gem a third bigger, stepped).
Run: python3 tools/make_test_glb.py
"""
import json
import math
import struct
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "bta/src/main/resources/assets/jsonextended/models/block"

bin_data = bytearray()
views, accessors = [], []


def add(fmt, values, type_, component, count, extra=None):
    while len(bin_data) % 4:
        bin_data.append(0)
    data = struct.pack("<" + fmt * len(values), *values)
    views.append({"buffer": 0, "byteOffset": len(bin_data), "byteLength": len(data)})
    bin_data.extend(data)
    acc = {"bufferView": len(views) - 1, "componentType": component, "count": count, "type": type_}
    if extra:
        acc.update(extra)
    accessors.append(acc)
    return len(accessors) - 1


def box(w, h, d):
    """Four corners per side, counter-clockwise seen from outside, two triangles each (as exporters write them)."""
    x0, x1, y0, y1, z0, z1 = -w / 2, w / 2, 0, h, -d / 2, d / 2
    sides = [
        [(x0, y1, z0), (x0, y1, z1), (x1, y1, z1), (x1, y1, z0)],  # up
        [(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)],  # down
        [(x0, y0, z0), (x0, y1, z0), (x1, y1, z0), (x1, y0, z0)],  # north
        [(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)],  # south
        [(x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0)],  # west
        [(x1, y0, z0), (x1, y1, z0), (x1, y1, z1), (x1, y0, z1)],  # east
    ]
    pos, uv, idx = [], [], []
    for s, corners in enumerate(sides):
        for c in corners:
            pos.extend(c)
        uv.extend([0, 0, 0, 1, 1, 1, 1, 0])
        v = s * 4
        idx.extend([v, v + 1, v + 2, v, v + 2, v + 3])
    return pos, uv, idx


def octahedron():
    top, bottom = (0, 1, 0), (0, -1, 0)
    ring = [(1, 0, 0), (0, 0, -1), (-1, 0, 0), (0, 0, 1)]  # counter-clockwise seen from above
    pos, uv, idx = [], [], []
    for i in range(4):
        a, b = ring[i], ring[(i + 1) % 4]
        for tri, tuv in (((a, b, top), (0, 1, 1, 1, 0.5, 0)), ((b, a, bottom), (1, 0, 0, 0, 0.5, 1))):
            for c in tri:
                pos.extend(c)
            uv.extend(tuv)
    idx = list(range(len(pos) // 3))
    return pos, uv, idx


def primitive(pos, uv, idx, material):
    xs, ys, zs = pos[0::3], pos[1::3], pos[2::3]
    p = add("f", pos, "VEC3", 5126, len(pos) // 3,
            {"min": [min(xs), min(ys), min(zs)], "max": [max(xs), max(ys), max(zs)]})
    t = add("f", uv, "VEC2", 5126, len(uv) // 2)
    i = add("H", idx, "SCALAR", 5123, len(idx))
    return {"attributes": {"POSITION": p, "TEXCOORD_0": t}, "indices": i, "material": material}


def quat_y(degrees):
    half = math.radians(degrees) / 2
    return [0, math.sin(half), 0, math.cos(half)]


def turn(seconds, start_deg, turns_keys=4):
    """Rotation keys for one full turn about Y, in quarter steps (linear slerp needs keys under 180 degrees apart)."""
    times = [seconds * i / turns_keys for i in range(turns_keys + 1)]
    values = []
    for i in range(turns_keys + 1):
        values += quat_y(start_deg + 360 * i / turns_keys)
    return times, values


def animations():
    gem = 1
    out = []
    # idle: slow turn, and a bob as a cubic spline (in-tangent, value, out-tangent per key; flat tangents)
    t, v = turn(4.0, 45)
    turn_in = add("f", t, "SCALAR", 5126, len(t), {"min": [t[0]], "max": [t[-1]]})
    turn_out = add("f", v, "VEC4", 5126, len(t))
    bob_t = [0, 2, 4]
    bob_v = []
    for y in (0.6, 0.66, 0.6):
        bob_v += [0, 0, 0, 0, y, 0, 0, 0, 0]
    bob_in = add("f", bob_t, "SCALAR", 5126, 3, {"min": [0], "max": [4]})
    bob_out = add("f", bob_v, "VEC3", 5126, 9)
    out.append({"name": "idle",
                "samplers": [{"input": turn_in, "output": turn_out},
                             {"input": bob_in, "output": bob_out, "interpolation": "CUBICSPLINE"}],
                "channels": [{"sampler": 0, "target": {"node": gem, "path": "rotation"}},
                             {"sampler": 1, "target": {"node": gem, "path": "translation"}}]})
    # spin: a fast turn, and the gem a third bigger (stepped, so it pops)
    t, v = turn(0.5, 45)
    spin_in = add("f", t, "SCALAR", 5126, len(t), {"min": [t[0]], "max": [t[-1]]})
    spin_out = add("f", v, "VEC4", 5126, len(t))
    grow_in = add("f", [0], "SCALAR", 5126, 1, {"min": [0], "max": [0]})
    grow_out = add("f", [0.4, 0.4, 0.4], "VEC3", 5126, 1)
    out.append({"name": "spin",
                "samplers": [{"input": spin_in, "output": spin_out},
                             {"input": grow_in, "output": grow_out, "interpolation": "STEP"}],
                "channels": [{"sampler": 0, "target": {"node": gem, "path": "rotation"}},
                             {"sampler": 1, "target": {"node": gem, "path": "scale"}}]})
    return out


def write(name, animated):
    global bin_data, views, accessors
    bin_data, views, accessors = bytearray(), [], []
    gltf = {
        "asset": {"version": "2.0", "generator": "BTA JSON Extended tools/make_test_glb.py"},
        "scene": 0,
        "scenes": [{"nodes": [0]}],
        "nodes": [
            {"name": "base", "mesh": 0, "children": [1]},
            {"name": "gem", "mesh": 1, "translation": [0, 0.6, 0], "rotation": quat_y(45), "scale": [0.3, 0.3, 0.3]},
        ],
        "meshes": [
            {"name": "base", "primitives": [primitive(*box(0.75, 0.25, 0.75), 0)]},
            {"name": "gem", "primitives": [primitive(*octahedron(), 1)]},
        ],
        "materials": [
            {"name": "base", "pbrMetallicRoughness": {"baseColorFactor": [1, 1, 1, 1]}},
            {"name": "gem", "pbrMetallicRoughness": {"baseColorFactor": [0.8, 1, 0.9, 1]}, "emissiveFactor": [1, 1, 1]},
        ],
    }
    if animated:
        gltf["animations"] = animations()
    gltf["accessors"] = accessors
    gltf["bufferViews"] = views
    gltf["buffers"] = [{"byteLength": len(bin_data)}]

    js = json.dumps(gltf, separators=(",", ":")).encode()
    js += b" " * (-len(js) % 4)
    while len(bin_data) % 4:
        bin_data.append(0)
    total = 12 + 8 + len(js) + 8 + len(bin_data)
    out = struct.pack("<III", 0x46546C67, 2, total) + struct.pack("<II", len(js), 0x4E4F534A) + js \
        + struct.pack("<II", len(bin_data), 0x004E4942) + bytes(bin_data)
    (OUT / name).write_bytes(out)
    print(f"wrote {OUT / name} ({len(out)} bytes)")


write("test_glb_lantern.glb", False)
write("test_glb_animated_lantern.glb", True)
