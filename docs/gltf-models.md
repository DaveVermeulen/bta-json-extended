# glTF and GLB models

A model JSON with `"loader": "jsonextended:gltf"` draws a glTF 2.0 file instead of elements. Use the binary `.glb` (one file); a `.gltf` with its `.bin` next to it works too. Like OBJ, it works anywhere a model id goes: in blockstates (with `x`/`y` rotations), in `Models.load`, and for block items. It's drawn by the same code as OBJ models, so culling, tinting, glow and smooth lighting behave the same.

## The model JSON

```json
{
  "loader": "jsonextended:gltf",
  "model": "mymod:models/block/lamp.glb",
  "offset": [0.5, 0, 0.5],
  "textures": {
    "particle": "mymod:block/lamp",
    "glass": "mymod:block/lamp_glass",
    "metal": "mymod:block/lamp_metal"
  }
}
```

| Key | Default | What it does |
| --- | --- | --- |
| `loader` | required | `jsonextended:gltf` |
| `model` | required | The `.glb` or `.gltf` file as `namespace:path` inside `assets/<namespace>/` |
| `textures` | none | One entry per **material name** in the file, plus `particle` for break particles. An unnamed material is `material_0`, `material_1`, ... by its place in the file |
| `offset` | none | Added to every corner, in blocks. Most tools build models around the origin; `[0.5, 0, 0.5]` puts that origin at the bottom centre of the block |
| `flip_v` | `false` | Flips textures vertically. glTF already stores them the way BTA wants, so this is rarely needed |
| `automatic_culling` | `true` | Faces lying flat on a block side are hidden when a solid block is next to that side |
| `shade_quads` | `true` | Darkens faces by direction like vanilla blocks |
| `emissive_ambient` | `true` | A material's `emissiveFactor` makes it glow (block light up to 15) |
| `ambient_occlusion` | `true` | Smooth lighting when the player has it on |
| `render_type` | `solid` | `cutout` or `translucent` as for any model |

## The file

- **Units are blocks**: one glTF unit (one metre in Blender) is one block. +Y is up.
- **Nodes** place their meshes: `translation`, `rotation`, `scale` or `matrix`, parents before children, like your modelling tool shows them. Mirrored nodes (a negative scale) keep their faces the right way out.
- **Meshes**: triangles, strips and fans, with `POSITION`, `TEXCOORD_0` and `indices`. Normals come from the faces. Two triangles in a row that share an edge and lie flat together become one quad, so a cube is six faces, not twelve. Points and lines are skipped with a warning.
- **Materials**: `baseColorFactor` tints the texture, `emissiveFactor` makes it glow, and `"extras": {"tintindex": 0}` tints it with the block colour like grass. The texture comes from the model JSON's `textures`, by material name. **Images inside the file aren't used**: BTA's texture atlas only takes textures from mod jars, so put the PNG in `textures/block/` as usual.
- **Not read**: embedded images, skins (skinned meshes are drawn in their rest pose, with a warning), morph targets, cameras and lights. Files that require Draco compression or mesh quantization are refused with a clear error; export without them.
- Errors and warnings name the file and the key, e.g. `mymod:models/block/lamp.glb: meshes[0].primitives[0].indices: 40 is past the last vertex (23)`.

## Exporting

- **Blender**: File > Export > glTF 2.0, format glTF Binary (`.glb`). Name your materials after the texture entries you'll use in the JSON. Model with 1 unit = 1 block, and either build inside 0 to 1 or use `offset`.
- **Blockbench**: File > Export > Export glTF Model. Blockbench works in pixels and builds around the origin. Check the size and position in game and use `offset` to move it; see [limitations](limitations.md).

## Using it

From a blockstate, like any model:

```json
{"variants": {"": {"model": "mymod:block/lamp"}}}
```

Put the `.glb` next to the JSON in `models/block/`. The Test GLB Lantern is a worked example: `bta/src/main/resources/assets/jsonextended/models/block/test_glb_lantern.*`, made by `tools/make_test_glb.py`.
