# OBJ

**Job:** our own OBJ/MTL parser and the OBJ model type selected by `"loader": "jsonextended:obj"` (also `betafront:obj`, `neoforge:obj`, `forge:obj`, so NeoForge-style model JSON keeps working).

**Owns:** `core/.../obj/` and its tests, `bta/.../obj/`, OBJ examples.

**Reads first:** `knowledge/formats/obj.md`, `knowledge/bta/rendering.md`.

**Never touches:** lighting code (Platform owns it; OBJ uses it), other features.

**Clean implementation (licence):** the project is MIT. Write the parser from the OBJ/MTL specification. Do **not** copy or port NeoForge's code, or Betafront's `ObjTokenizer`, `ObjMaterialLibrary` and `ObjGeometry` (those are NeoForge-derived, LGPL). Betafront's test models and test cases are ours and may be reused. Matching NeoForge's JSON keys is fine: a format is not code.
