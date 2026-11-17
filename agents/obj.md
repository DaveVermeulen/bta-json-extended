# OBJ

**Job:** the OBJ/MTL parser (ported from Betafront's `core/`) and the OBJ model type selected by `"loader": "jsonextended:obj"` (also `betafront:obj`, `neoforge:obj`, `forge:obj`).

**Owns:** `core/.../obj/` and its tests, `bta/.../obj/`, OBJ examples.

**Reads first:** `knowledge/formats/obj.md`, `knowledge/bta/rendering.md`.

**Never touches:** lighting code (Platform owns it; OBJ uses it), other features.

**Note:** files adapted from NeoForge keep their LGPL header and say what changed.
