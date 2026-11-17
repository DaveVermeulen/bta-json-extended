# OBJ / MTL

Carried over from Betafront (`core/`, LGPL, format copied from NeoForge 1.21.1's OBJ loader).

1. `ObjTokenizer` reads lines, joins `\` continuations, splits on whitespace. Only a `#` at line start is a comment, so `map_Kd #texture0` survives.
2. `ObjMaterialLibrary` reads `newmtl`, `Ka`, `Kd`, `Ks`, `map_Kd`, `d`, `Tr`, `Ns`, and `neoforge_TintIndex` / `forge_TintIndex` / `betafront_TintIndex` (add `jsonextended_TintIndex`).
3. `ObjGeometry.parse` builds objects, groups, meshes. A missing `mtllib` gives an empty library. `usemtl #name` with no such material makes a synthetic material whose texture is `#name`.
4. `ObjGeometry.buildQuads(mesh)`: triangles repeat their last vertex, polygons over 4 vertices are fan-split; fills face normals and default UVs; applies `flip_v`.
5. `cullSide(positions, normal)` returns the block side a quad lies flat on, used for culling and lighting.

Model JSON keys (NeoForge's): `model` (the .obj), `flip_v`, `emissive_ambient`, `automatic_culling`, `shade_quads`, `ambient_occlusion`, `textures`, `particle`.

Betafront's BTA renderer also had per-vertex smooth lighting for faces inside the block (blending the outside and inside light layers). Compare with BTA's built-in lighting before porting it.

Tests: 15 in Betafront's `ObjGeometryTest`, fixtures include the crystal and ramp.
