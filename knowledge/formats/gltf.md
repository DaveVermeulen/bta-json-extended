# glTF / GLB

Status: step 1 (static GLB/glTF block models) merged in PR #26 and checked in game by Dave (2026-11-22). Step 2 (node animations on tile entities) merged in PR #27 and checked in game by Dave (2026-11-23). Released in 1.0.0.

- glTF 2.0: `.gltf` (JSON + `.bin` + images) and `.glb` (one binary file: JSON chunk + BIN chunk). Parse with Gson plus our own buffer/accessor reader.
- Meshes: triangles from `POSITION`, `NORMAL`, `TEXCOORD_0`, `indices`; node transforms (TRS or matrix) baked for static models.
- Materials: `baseColorTexture` and `baseColorFactor`. **Risk:** textures embedded in a GLB are not in BTA's block atlas (atlas entries come from jar files). Options: require textures referenced by id in the model JSON (`"textures": {...}` like OBJ), or stitch embedded images at load (needs Platform's help). Decided 2026-11-17: start with texture ids in the model JSON; embedded images are ignored for now.
- Animations: glTF `animations` channels (translation, rotation, scale per node; LINEAR, STEP, CUBICSPLINE) converted to the same clip format the Animation agent uses, so one player drives both Blockbench and glTF animations.
- Later, maybe: skinning (joints and weights) for smooth deformation. Much bigger; node animation first.
- Static blocks use the mesh like OBJ; animated GLB is for tile entities and entities, drawn every frame.

## Our reader (`core/.../gltf/`, phase 7 step 1)

| Class | Does |
| --- | --- |
| `GltfFile.parse(id, bytes, source)` | GLB (magic `glTF`, version 2, JSON chunk + BIN chunk) or `.gltf` JSON; buffers from the BIN chunk, `data:...;base64,` URIs or files next to it (`sibling` resolves `./` and `../`, URI escapes decoded). `floats`/`ints` read accessors (byte stride, normalized ints, no bufferView = zeros). Refuses sparse accessors, out-of-range reads and `extensionsRequired` other than a few material-only ones (Draco, quantization: clear error) |
| `GltfModel.load(fileId, source)` | Walks the scene (`scene`, else the first, else every parentless node), places each mesh by its node's world matrix (TRS or column-major `matrix`), triangles/strips/fans to `ObjQuad`s with `mesh` = node name (`node_N` unnamed). Mirrored nodes swap winding. Two consecutive triangles sharing an edge (same vertex indices, opposite direction) in one plane merge to a quad `(x, w, y, u)` that keeps their diagonal; others repeat the last corner. Degenerate triangles dropped. Warnings: skins, points/lines, extra attributes, empty scene |
| Materials | `ObjMaterial(name, baseColorFactor rgb, max(emissiveFactor) as ambient, texture "#name" or "#material_N", alpha, extras.tintindex)` |
| `GltfModelJson` | `"loader": "jsonextended:gltf"` (string or `{"id"}`), `model`, `textures`, `offset` (blocks), `flip_v` (false), OBJ's other keys and defaults. `place(quads)` applies offset and flip; `unsetMaterials` |
| `obj/MeshModelJson` | Interface both model JSON records implement (culling, shading, glow, AO, render type, `texture("#var")`, `particle()`), plus the shared key readers |

- Units: 1 glTF unit = 1 block, +Y up, counter-clockwise front faces, V down (matches BTA's atlas, so no flip).
- Drawing (`bta/.../gltf/GltfModels`): `Models.load` sends the glTF loader there; bytes come through the texture packs (`Models.readBytes`). The quads go to `ObjStaticModel`, so everything in [obj.md](obj.md) about culling, light, tint and glow applies.
- `ModelCheck` skips glTF model JSON like OBJ.
- Dev block: Test GLB Lantern (9127), hand-made by `tools/make_test_glb.py`: base box around the origin (`offset` [0.5, 0, 0.5]), gem octahedron child turned 45 degrees and scaled, `emissiveFactor` 1. Tests: `GltfModelTest` (built-in GLB writer `Gltf` in the tests), `GltfModelJsonTest.devLanternLoadsWithoutWarnings`.
- Not verified yet: what Blockbench's glTF export looks like (units, centring). Check with a real export at the in-game check and note it here.

## Animations (phase 7 step 2)

- `GltfModel` keeps the scene as `nodes()` (parents first): file index, name, parent, rest TRS packed in 10 floats (`T`=0 translation, `R`=3 rotation x y z w, `S`=7 scale), `matrix` (column-major, or null; matrix nodes can't be animated, per spec) and the node's quads in its own space. `pose(Map<node, TRS>)` places everything (mirrored world matrices reverse the corner order 0,3,2,1, same two triangles); `quads()` is the rest pose.
- `GltfAnimation.read`: `animations[].channels` (`target.node`, `target.path` translation/rotation/scale; `weights` skipped with a warning; nodes outside the scene skipped) and `samplers` (`input` times, `output` values, `interpolation` LINEAR/STEP/CUBICSPLINE). Errors: wrong VECn for the path, times going down, key/value count mismatch. `length` = last key of any channel. `sample(t)` gives TRS for animated nodes (unanimated paths keep rest); clamps outside the keys; LINEAR rotation = slerp (short way), CUBICSPLINE = Hermite with tangents times the key span, then rotations normalized.
- `GltfAnimationPlayer(model)`: `play/ensure/skipToEnd(anim, Loop, now[, fade])`, `stop`, `current`, `elapsed`, `playing`, `pose(now)`; crossfade blends per node (lerp T and S, slerp R; a node in only one side blends with its rest). `sample(anim, loop, seconds)` is the static form. Own player rather than converting to Bedrock clips: see decisions.md 2026-11-22.
- `bta/.../gltf/GltfTileEntityRenderer<T>(modelId, Poser, Facing)`: reads the model JSON and file once per renderer (new renderers on pack reload); `Poser.pose(te, model, partialTick)` returns node TRS; drawn like `ModelTileEntityRenderer` (block light, `setShade1i(255)`, normals, quarter turns), plus material colour and `emissiveFactor` glow.
- Dev block: Test GLB Animated Lantern (9128), `gltf/example/AnimatedLantern` and `models/block/test_glb_animated_lantern.*` (same script). `idle` (4 s: turn in quarter keys, cubic-spline bob 0.6 to 0.66) loops; metadata bit 0 (right-click) crossfades 0.3 s to `spin` (0.5 s turn, STEP scale 0.4). Tests: `GltfAnimationTest`, `GltfModelJsonTest.devAnimatedLanternPlaysInsideItsBlock`.
