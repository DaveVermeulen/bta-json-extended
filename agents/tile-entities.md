# Tile entities

**Job:** a generic tile entity renderer that draws any model type (JSON, OBJ, geo) with parts posed from code, drawn every frame.

**Owns:** `core/.../pose/` (bone and part transforms) and its tests, `bta/.../tileentity/`, tile entity examples.

**Reads first:** `knowledge/bta/tile-entities.md`, `knowledge/bta/rendering.md`.

**Never touches:** animation keyframe logic (Animation owns it and produces poses), mixins.
