# OBJ / MTL

Status: phase 4 done when PR #15 merged (crystal and ramp checked in game by Dave, 2026-11-18). Step 3 (smooth lighting) in review. Betafront's parser below is NeoForge-derived (LGPL): use it only as a description of the behaviour and JSON format we stay compatible with, never as code to copy.

1. `ObjTokenizer` reads lines, joins `\` continuations, splits on whitespace. Only a `#` at line start is a comment, so `map_Kd #texture0` survives.
2. `ObjMaterialLibrary` reads `newmtl`, `Ka`, `Kd`, `Ks`, `map_Kd`, `d`, `Tr`, `Ns`, and `neoforge_TintIndex` / `forge_TintIndex` / `betafront_TintIndex` (add `jsonextended_TintIndex`).
3. `ObjGeometry.parse` builds objects, groups, meshes. A missing `mtllib` gives an empty library. `usemtl #name` with no such material makes a synthetic material whose texture is `#name`.
4. `ObjGeometry.buildQuads(mesh)`: triangles repeat their last vertex, polygons over 4 vertices are fan-split; fills face normals and default UVs; applies `flip_v`.
5. `cullSide(positions, normal)` returns the block side a quad lies flat on, used for culling and lighting.

Model JSON keys (NeoForge's): `model` (the .obj), `flip_v`, `emissive_ambient`, `automatic_culling`, `shade_quads`, `ambient_occlusion`, `textures`, `particle`.

Betafront's BTA renderer also had per-vertex smooth lighting for faces inside the block (blending the outside and inside light layers). Compare with BTA's built-in lighting before porting it.

Tests: Betafront's 15 `ObjGeometryTest` cases and fixtures (crystal, ramp) are ours and can be reused.

Improvements over Betafront to aim for: several objects or groups per model, smoothing groups and vertex normals, UV clamping, errors with file and line number.

## Our parser (`core/.../obj/`, phase 4 step 1)

| Class | Does |
| --- | --- |
| `ObjModel.load(objId, source, mtlOverride)` | Reads the OBJ, then its `mtllib` files next to it (or `mtl_override`); `quads(flipV)`, `material(mesh)`, `warnings()` |
| `ObjFile` | `v` (w divides), `vt`, `vn`, `f`/`fo` (`v`, `v/vt`, `v//vn`, `v/vt/vn`, negative = relative), `o`, `g`, `usemtl`, `mtllib`. Curves, lines, `s` etc. skipped; other unknown statements warn once. `sibling(objId, name)` |
| `ObjMesh` | Faces sharing object, group and material; `name()` is `object/group` |
| `Mtl.parse` / `ObjMaterial` | `newmtl`, `Kd` (1 or 3 numbers), `Ka` (largest channel as `ambient`), `map_Kd` (last argument, so `-s ...` options are skipped), `d`, `Tr`, `jsonextended_/betafront_/neoforge_/forge_TintIndex` |
| `ObjQuad` | Four corners in blocks, UVs 0-1 (V up, as OBJ), unit normals; `cullSide()`, `Side.nearest(normal)`, `faceNormal` |
| `ObjModelJson` | The model JSON: `LOADERS` (jsonextended/betafront/neoforge/forge `:obj`, string or `{"id"}`), `isObj(json)`, `parse` with NeoForge's keys and defaults (`flip_v` false, others true), `texture("#var")`, `particle()` |
| `ObjLines` | Statement splitter: `\` continues a line; only a `#` at the start of a line is a comment |

- File ids are `ns:path/file.obj` for `/assets/ns/path/file.obj`.
- Triangles repeat their last corner; polygons over 4 corners become a fan of quads from corner 0 (last one a triangle when odd).
- Missing `vn`: the face normal (cross product of the diagonals, counter-clockwise front). Missing `vt`: the texture corners.
- `usemtl #name` with no such material draws the model JSON's `textures` entry `#name`. Any other unknown material warns and draws with no material (the particle texture). A missing MTL file only warns when a material was needed from it (Blender always writes `mtllib`).
- Errors (`ObjException`) start with `id line N:`: bad numbers, out-of-range indices, faces under 3 corners, `w = 0`, missing OBJ file.
- `cullSide`: all four corners on a block face plane and the face normal pointing out through it.
- Tests: `ObjModelTest` (fixtures in `core/src/test/resources/obj/`, from Betafront's tests).

## Drawing it in BTA (phase 4 step 2)

- `Models.load(id)` (Platform) reads the model JSON first: an OBJ `loader` goes to `ObjModels.load`, everything else to BTA's reader. OBJ, MTL and model JSON all come through the texture packs (`Models.readFile("ns:path/file.obj")`). An `ObjException` logs one error line and gives the missing model.
- `ModelCheck` skips OBJ model files; `ObjModelJson` and `ObjModel` report their own warnings, logged once per session.
- `bta/.../obj/ObjStaticModel` implements `StaticBlockModel` as in [../bta/static-model.md](../bta/static-model.md):
  - quarter turns through `core/.../obj/QuarterTurns` (X, Y, Z about the centre; tested against BTA's `Direction.rotate*`); `uvlock` is ignored.
  - each quarter-turn combination is worked out once per model (corners, normal, cull side, light side) and kept, so chunk rebuilds don't redo it per block.
  - culling: faces with a `cullSide` (when `automatic_culling`) are skipped when `source.cullSide(world, pos, rotated side)`; checked once per side per block.
  - light, flat: a face on a block side takes the neighbour's light index, others the block's own (`LightingCache`, our own instance); `setShade1i(255)`; normal from the face (or up when `shade_quads` is false).
  - light, smooth (step 3): when `ambient_occlusion` (JSON), `GameSettings.AMBIENT_OCCLUSION` and `block.emission == 0`, `ObjSmoothLight` (Betafront's method) per vertex: corners average 4 blocks for the light index and brightness, for the outside layer and the block's own, blended by position on the side and depth; brightness goes to `setShade1i`, emissive faces stay 255.
  - colour: material `Kd` times the block colour for a tinted material; `emissive_ambient`: `Ka` brightness raises block light (0-15).
  - textures: `map_Kd` through the model JSON's `textures`; none or unset gives `textures.particle`. Icons are cached until `TextureRegistry.reloadGeneration` changes.
  - `renderLayer` from `render_type` (translucent gives 1), item poses are BTA's block defaults, `particleColorIndex` -1.
- Test blocks: Test OBJ Crystal (9122, `betafront:obj` spelling, cutout) and Test OBJ Ramp (9123, facing and upside down through a blockstate). `ObjModelJsonTest.devObjModelsLoadWithoutWarnings` checks both.
