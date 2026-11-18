# Changelog

## Unreleased

- Gradle build: `core/` (plain Java, JUnit 5) and `bta/` (BTA 8.0.1, HalpLibe 6.2.0), joined by a root composite build.
- GitHub Actions builds the mod and runs the core tests on every pull request and push to `main`.
- Tag `v<version>` to build the jar into a draft GitHub release, with notes from this file.
- Dev-only "Blockbench Test" block: a Blockbench Java Block model drawn by BTA's own JSON model code.
- Fix: the dev client crashed at startup registering the test block model; models are now added on HalpLibe's block model reload event.
- Dev-only blockstate test blocks (facing, stairs, slab, fence, weighted) with blockstate JSON, models, textures and expected-result fixtures for the phase 1 parser.
- Blockstate JSON (phase 1): `variants` and `multipart` (`a|b`, `OR`, `AND`), `x`/`y`/`uvlock`, weighted variants, named properties from metadata bits, and `StateResolver` for neighbour-dependent properties (stair shape and fence sides built in). Every file is checked when loaded, with one log line naming the file and key. The dev test blocks are now drawn from their blockstate files.
- Item model JSON parser (phase 2): parents with `item/generated`, `item/handheld` and `item/handheld_rod` built in, layered textures, 3D models from `elements`, merged `display` and `gui_light`, and one log line per bad file.
- Item models are drawn (phase 2): flat items with any number of layers and their `display` poses (handheld and rod defaults included), and 3D item models from `elements`. Six dev-only test items.
- Model JSON polish (phase 3): Blockbench exports load quietly (`credit`, `groups`, `texture_size` and element names are accepted), `render_type: translucent` puts a block in BTA's translucent pass, `gui_light: front` draws 3D item models flat-lit in the inventory, and every model is checked when loaded with one log line naming the model and key. A model BTA's reader would crash on (broken JSON, a face without a texture) draws the missing model instead. Dev-only test blocks Test Glass and Test Broken Model, and item Test Gem Front.
