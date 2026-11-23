# glTF and GLB models

A model JSON with `"loader": "jsonextended:gltf"` draws a glTF 2.0 file instead of elements, and its animations can play on a tile entity. Use the binary `.glb` (one file); a `.gltf` with its `.bin` next to it works too. Like OBJ, it works anywhere a model id goes: in blockstates (with `x`/`y` rotations), in `Models.load`, and for block items. It's drawn by the same code as OBJ models, so culling, tinting, glow and smooth lighting behave the same.

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
- **Not read**: embedded images, skins (skinned meshes are drawn in their rest pose, with a warning), morph targets, cameras and lights. Animations are read and play on tile entities (below); a block model shows the rest pose. Files that require Draco compression or mesh quantization are refused with a clear error; export without them.
- Errors and warnings name the file and the key, e.g. `mymod:models/block/lamp.glb: meshes[0].primitives[0].indices: 40 is past the last vertex (23)`.

## Animations on a tile entity

The file's own animations (node translation, rotation and scale, as Blender exports them) can play on a tile entity. Use `GltfTileEntityRenderer` with the same model JSON, and keep one `GltfAnimationPlayer` per tile entity on the client:

```java
dispatcher.assignRenderer(FanTileEntity.class, new GltfTileEntityRenderer<FanTileEntity>("mymod:block/fan",
        (fan, model, partialTick) -> {
            float now = (fan.ticks + partialTick) / 20f;            // seconds, on the tile entity's own clock
            GltfAnimation wanted = model.animations().get(fan.isOn() ? "spin" : "idle");
            if (wanted == null)
                return Map.of();                                  // rest pose
            GltfAnimationPlayer player = fan.player(model);       // one per tile entity, new when the model is
            player.ensure(wanted, Loop.LOOP, now, 0.3f);          // 0.3 s crossfade when it changes
            return player.pose(now);
        },
        fan -> fan.facingTurns()));                               // optional: quarter turns about Y
```

- Animations are found by their name in the file (`animation_0`, ... when unnamed). glTF has no loop setting, so you pick `Loop.LOOP`, `Loop.HOLD` (stay on the last frame) or `Loop.ONCE` when playing.
- `LINEAR`, `STEP` and `CUBICSPLINE` keys are played; rotations are interpolated the short way round. Morph target weights aren't played (a warning).
- `skipToEnd` shows an animation already finished, for state loaded with the world (a door that was open).
- The block itself should draw nothing in the chunk (see [tile entity models](tile-entities.md#4-hide-the-chunk-model)); `Models.load` with the same model id still gives the item and particles.
- The worked example is the Test GLB Animated Lantern: `gltf/example/AnimatedLantern.java` and `models/block/test_glb_animated_lantern.*`.

## Exporting

- **Blender**: File > Export > glTF 2.0, format glTF Binary (`.glb`). Name your materials after the texture entries you'll use in the JSON. Model with 1 unit = 1 block, and either build inside 0 to 1 or use `offset`.
- **Blockbench**: File > Export > Export glTF Model. Blockbench works in pixels and builds around the origin. Check the size and position in game and use `offset` to move it; see [limitations](limitations.md).

## Using it

From a blockstate, like any model:

```json
{"variants": {"": {"model": "mymod:block/lamp"}}}
```

Put the `.glb` next to the JSON in `models/block/`. The Test GLB Lantern is a worked example: `bta/src/main/resources/assets/jsonextended/models/block/test_glb_lantern.*`, made by `tools/make_test_glb.py`.
