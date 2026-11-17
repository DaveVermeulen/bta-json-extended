# Changelog

## Unreleased

- Gradle build: `core/` (plain Java, JUnit 5) and `bta/` (BTA 8.0.1, HalpLibe 6.2.0), joined by a root composite build.
- GitHub Actions builds the mod and runs the core tests on every pull request and push to `main`.
- Tag `v<version>` to build the jar into a draft GitHub release, with notes from this file.
- Dev-only "Blockbench Test" block: a Blockbench Java Block model drawn by BTA's own JSON model code.
- Dev-only blockstate test blocks (facing, stairs, slab, fence, weighted) with blockstate JSON, models, textures and expected-result fixtures for the phase 1 parser. They draw one fixed model until blockstate rendering lands.
