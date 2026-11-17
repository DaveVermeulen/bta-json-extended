# Prior art

## Betafront (github.com/DaveVermeulen/betafront, to be abandoned)

Reuse: OBJ test models and test cases (not the parser: it is NeoForge-derived LGPL; we write our own), texture-pack loading, smooth lighting for inner faces, the build setup and release steps. Drop: the StationAPI build and the `ObjModels.block(block, id, yRotation)` API, which blockstates replace.

## DragonFly (github.com/UselessSolutions/DragonFly, branch 7.3, BTA 7.3_04)

- Only its static models went into BTA 8. Blockstates (`BlockstateData`, `Condition`, `MetaStateInterpreter`), `BlockModelDFObj` and animation did not: read them for reference.
- Blockstates needed a Java interpreter per block; we add a JSON property map for the common case.
- Multipart conditions silently skipped unknown keys; we validate and log.
- It shipped copies of `RenderBlocks` and `LightingCache`, which broke across versions; we call BTA's own.
- Animations loaded only from the classpath; we load through texture packs.
- JitPack builds failed often; use our own Maven.
