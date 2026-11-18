# OBJ / MTL

Status: phase 4. Step 1 (parser in `core/`) done, written from the OBJ/MTL specs; step 2 (drawing it in BTA behind `loader`) next. Betafront's parser below is NeoForge-derived (LGPL): use it only as a description of the behaviour and JSON format we stay compatible with, never as code to copy.

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
