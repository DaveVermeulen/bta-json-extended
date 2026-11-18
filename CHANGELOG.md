# Changelog

## Unreleased

- Smooth lighting for OBJ models (phase 4, step 3): with the game's smooth lighting on, OBJ faces get per-corner light and shading like vanilla blocks, also for faces inside the block.
- OBJ models are drawn (phase 4, step 2): a model JSON with `"loader": "jsonextended:obj"` (or `betafront:`, `neoforge:`, `forge:obj`) draws an OBJ file, from blockstates and anywhere else BTA takes a model, with NeoForge's keys (`flip_v`, `automatic_culling`, `shade_quads`, `emissive_ambient`, `mtl_override`, `textures`) and `render_type`. Dev-only test blocks Test OBJ Crystal and Test OBJ Ramp.
- Fix: BTA's missing model is built fresh for each model that needs it, so it picks up a rebuilt atlas after a texture pack change.
- OBJ/MTL parser (phase 4, step 1): our own, written from the specs. Objects, groups, materials, relative indices, triangles and polygons as quads, block-side culling, and errors with file and line.

## 0.1.0 (2026-11-18)

First release, for BTA 8.0.1 with HalpLibe 6.2.0. It's a library for mod authors: it adds nothing to the game on its own.

Features:
- Blockstate JSON: `variants` and `multipart` (`a|b`, `OR`, `AND`), `x`/`y`/`uvlock`, weighted variants, named properties from metadata bits, and `StateResolver` for neighbour-dependent properties (stair shape and fence sides built in).
- Item model JSON: parents with `item/generated`, `item/handheld` and `item/handheld_rod` built in, any number of layered textures, `display` poses (handheld and rod defaults included), and 3D item models from `elements`.
- Model JSON polish: Blockbench exports load quietly (`credit`, `groups`, `texture_size` and element names are accepted), `render_type: translucent` puts a block in BTA's translucent pass, and `gui_light: front` draws 3D item models flat-lit in the inventory.
- Readable errors: every blockstate and model file is checked when it loads, with one log line naming the file and key. A broken file draws the missing model or texture instead of crashing.

Development:
- Gradle build: `core/` (plain Java, JUnit 5) and `bta/` (BTA 8.0.1, HalpLibe 6.2.0), joined by a root composite build. GitHub Actions builds the mod and runs the core tests on every pull request; tagging `v<version>` builds a draft GitHub release.
- Dev-only test blocks and items (only in a development environment) for every format, with expected-result fixtures for the core tests.
- Fix: the dev client crashed at startup registering the test block model; models are now added on HalpLibe's block model reload event.
- Fix: BTA's missing model was compiled once per blockstate block, logging eight texture warnings each time; it's now compiled only when needed.
