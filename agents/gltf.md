# glTF / GLB

**Job:** load `.glb` (and `.gltf`) models: meshes, materials and textures, and their node animations, as a model type (`"loader": "jsonextended:gltf"`). Comes after modern-Minecraft parity (see PLAN.md).

**Owns:** `core/.../gltf/` and its tests, `bta/.../gltf/`, glTF examples.

**Reads first:** `knowledge/formats/gltf.md`, `knowledge/formats/animation.md` (the pose interface), `knowledge/bta/rendering.md`.

**Never touches:** the Bedrock animation player (Animation owns it; glTF has its own player with the same API, see knowledge/decisions.md 2026-11-22), lighting, mixins.

**Clean implementation:** our own parser from the glTF 2.0 specification; no copied code from other loaders unless its licence is MIT-compatible and the maintainer agrees.
