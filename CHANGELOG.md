# Changelog

## Unreleased

- glTF animations (phase 7, step 2): a `.glb`'s own node animations (translation, rotation, scale; linear, step and cubic spline, rotations slerped) play on tile entities through `GltfTileEntityRenderer` and `GltfAnimationPlayer` (loop, hold or once, crossfades). Dev-only Test GLB Animated Lantern: right-click to spin.
- glTF and GLB models (phase 7, step 1): `"loader": "jsonextended:gltf"` draws a `.glb` or `.gltf` as a block model, with node transforms, materials (colour, glow, tint) and textures by material name from the model JSON, through the same renderer as OBJ. Dev-only Test GLB Lantern. Docs in `docs/gltf-models.md`.
- OBJ smooth lighting: a slanted face leaning towards a solid block (an upside-down ramp on the ground) no longer goes black; it takes the block's own light on that side.
- A texture id outside the atlas in an OBJ or tile entity model is looked up once and logged once (it was looked up again for every face every frame, silently). Both use one shared cache, `AtlasIcons`.
- JSON nested more than 512 levels deep is a normal "can't be read" error instead of a stack overflow.
- 3D item models reuse one random generator for ground clusters instead of making one per draw.
- OBJ blocks: each rotation's corners, normals and sides are worked out once per model instead of for every block on every chunk rebuild.
- Documentation for mod developers in `docs/`: installing, getting started, blockstates, item models, model JSON, OBJ, tile entity models, animations, example content and limitations. The README describes what the library does now.
- Mod icon: shown in Mod Menu (`icon` in `fabric.mod.json`, white background); a transparent version sits at the top of the README.
- Animated mobs (phase 6, step 3): `AnimatedMobRenderer` poses a geo model's bones from a Bedrock animation file, blending idle into walk by walking speed. Dev-only Test Mob, spawned with the Test Mob Spawner block.
- `AnimationPlayer.skipToEnd`: state loaded with the world shows at once (a saved open chest loads open).
- Animations in game (phase 6, step 2): `Animations.load` reads `.animation.json` files through the texture packs, and a model tile entity renderer can pose its parts from them. Dev-only Test Animated Chest opens and closes from an animation file, and its knob wiggles while it's open.
- Animation (phase 6, step 1, core only): reads Blockbench's Bedrock `.animation.json` (loop, hold, play once; linear, Catmull-Rom and step keyframes; pre/post) and samples it to a pose at any time, on top of the pose set from code; `AnimationPlayer` plays one animation per object with crossfades. Molang expressions aren't evaluated yet (a warning, and 0).
- Model tile entity renderers (phase 5, step 2): `ModelTileEntityRenderer` draws a Blockbench model for a tile entity every frame, with its groups posed from code. Dev-only Test Chest: right-click opens and closes its lid.
- Poses (phase 5, step 1, core only): model JSON elements baked to quads like vanilla, parts read from Blockbench `groups` (or OBJ objects), and part rotation, translation and scale set from code about each part's pivot, with lerp for smooth motion. Nothing in game uses it yet.
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
