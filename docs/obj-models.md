# OBJ models

A model JSON with an OBJ `loader` draws a Wavefront `.obj` file instead of elements. It works anywhere a model id goes: in blockstates (with `x`/`y` rotations), in `Models.load`, and for block items. The keys follow NeoForge's OBJ loader, so NeoForge and Betafront models carry over.

## The model JSON

```json
{
  "loader": "jsonextended:obj",
  "model": "mymod:models/block/lamp.obj",
  "flip_v": true,
  "textures": {
    "particle": "mymod:block/lamp",
    "texture0": "mymod:block/lamp"
  }
}
```

| Key | Default | What it does |
| --- | --- | --- |
| `loader` | required | `jsonextended:obj`. `betafront:obj`, `neoforge:obj` and `forge:obj` work too |
| `model` | required | The `.obj` file as `namespace:path` inside `assets/<namespace>/` |
| `textures` | none | Names to texture ids. `usemtl #texture0` or MTL `map_Kd #texture0` refers to `texture0` here. Set `particle` for break particles |
| `flip_v` | `false` | Flips textures vertically. Blender and Blockbench exports usually need `true` |
| `automatic_culling` | `true` | Faces lying flat on a block side are hidden when a solid block is next to that side |
| `shade_quads` | `true` | Darkens faces by direction like vanilla blocks |
| `emissive_ambient` | `true` | An MTL `Ka` above 0 makes that material glow |
| `ambient_occlusion` | `true` | Smooth lighting when the player has it on (not for blocks that give off light, like vanilla) |
| `mtl_override` | none | Use this `.mtl` instead of the file's `mtllib` line |
| `render_type` | `solid` | `translucent` for see-through textures, as for any model |

## The .obj file

- **Units are blocks**: `0` to `1` fills one block. Blockbench works in pixels, so scale its OBJ export to 1/16.
- **Faces** can be triangles, quads or larger convex polygons.
- **Materials**: the simplest setup has no `.mtl`. Name the material `#texture0` in your modelling tool and map `texture0` in the JSON. With an `.mtl`, `map_Kd` can be a texture id or `#name`.
- Read: `v`, `vt`, `vn`, `f` (with negative indices and `v//vn`), `o`, `g`, `usemtl`, `mtllib`. Other lines are ignored.
- MTL: `newmtl`, `map_Kd`, `Ka` (glow), and `jsonextended_TintIndex` (also `neoforge_`, `forge_`, `betafront_`) for block colour tinting like grass, where the face colour is `Kd` times the block colour. `d` and `Tr` are read but don't make faces see-through; use a texture with alpha and `render_type`.
- Errors name the file and line.

## Using it

From a blockstate, like any model:

```json
{"variants": {"": {"model": "mymod:block/lamp"}}}
```

Or for a block without a blockstate file:

```java
dispatcher.addDispatch(MyBlocks.LAMP, new BlockModelGeneric<>(MyBlocks.LAMP, Models.load("mymod:block/lamp")));
```

Put the `.obj` (and `.mtl`) next to the JSON in `models/block/`. The test crystal and ramp are worked examples: `bta/src/main/resources/assets/jsonextended/models/block/test_obj_*`.
