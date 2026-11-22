# glTF / GLB

Status: phase 7 step 1 (static GLB/glTF block models) written 2026-11-22, waiting for the in-game check. Step 2 is node animations on tile entities.

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
