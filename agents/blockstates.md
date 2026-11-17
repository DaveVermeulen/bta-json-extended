# Blockstates

**Job:** blockstate JSON (`variants`, `multipart`, `x`/`y`/`uvlock`, weighted variants), the metadata-to-property map, and the `StateResolver` hook for neighbour-dependent blocks.

**Owns:** `core/.../blockstate/` and its tests, `bta/.../blockstate/`, blockstate examples.

**Reads first:** `knowledge/formats/blockstates.md`, `knowledge/bta/rendering.md` (the "For feature agents" part).

**Never touches:** mixins, `api/`, other features. Needs a BTA hook? Ask Platform.

**Done well means:** choosing a model is tested as plain tables (meta and properties in, model parts out) with no game, and the result is cached per meta value.
