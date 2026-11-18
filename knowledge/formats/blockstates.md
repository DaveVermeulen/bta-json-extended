# Blockstate and model JSON (as this mod supports them)

Status: implemented in phase 1 (PR #8): parser tested in `core/`, rendering checked in game by Dave on 2026-11-18.

## Blockstate file: `assets/<ns>/blockstates/<block>.json`

```json
{
  "properties": {
    "facing": { "bits": "0-1", "values": ["south", "west", "north", "east"] },
    "lit":    { "bits": "2",   "values": ["false", "true"] }
  },
  "variants": {
    "facing=north,lit=false": { "model": "mymod:block/lamp", "y": 0 },
    "facing=east,lit=false":  { "model": "mymod:block/lamp", "y": 90 },
    "facing=south,lit=true":  [ { "model": "mymod:block/lamp_on", "y": 180, "weight": 3 }, { "model": "mymod:block/lamp_on_alt", "y": 180 } ]
  }
}
```

- `properties` is ours (BTA has no named block states): it decodes metadata bits into named values. `bits` is one bit (`"2"`) or a range (`"0-1"`) within BTA's 4 metadata bits; value index = the bits read as a number.
- A property **without** `bits` is supplied by the block's Java `StateResolver` (neighbour-dependent state: stair `shape`, fence sides). Its `values` are still listed so keys can be validated.
- Metadata whose bits point past the end of `values` (e.g. 3 for a 3-value slab type) draws the missing model and logs once.
- `variants` and `multipart` (`when` with `OR`/`AND`, `apply`) follow vanilla 1.21. `x`, `y` in multiples of 90; `uvlock`; `weight`.
- Rotation maps onto `StaticBlockModel.renderAttached(rotX, rotY, rotZ, ..., uvlock, ...)` in quarter turns.
- Results are cached per metadata value.
- Unknown property names or values are load errors, logged once with file and key. The full list of load errors the parser must catch is in `core/src/test/resources/blockstate/cases/invalid.json`.

## Test blocks and fixtures

Dev-only blocks (`bta/.../blockstate/example/BlockstateExamples.java`), each with a blockstate file in `bta/src/main/resources/assets/jsonextended/blockstates/` and models built on our own copies of the vanilla 1.21 templates (`models/block/template/`):

| Block | Exercises | Metadata |
| --- | --- | --- |
| `test_facing` | `x` and `y`, two properties in one key | facing bits 0-2 (south, west, north, east, up, down), lit bit 3 |
| `test_stairs` | 40 variants, `uvlock`, `x: 180`, resolver property | facing bits 0-1, half bit 2; `shape` from a resolver |
| `test_slab` | three models, unused meta value | type bits 0-1 (bottom, top, double) |
| `test_fence` | `multipart`, resolver-only state | none; north/east/south/west from a resolver |
| `test_weighted` | weighted list, `""` key | none |

`core/src/test/resources/` holds byte-identical copies (`BlockstateFixturesTest` checks this), plus `blockstate/cases/*.json` (expected results: meta and resolver values in, properties and parts out, with BTA quarter turns), `blockstate/conditions/` (`a|b`, `OR`, `AND`) and `blockstate/invalid/` (files that must fail to load). Placement sets metadata like the vanilla block; right-click steps through every valid metadata value.

## Code

| Where | What |
| --- | --- |
| `core/.../blockstate/Blockstate` | `parse(source, json)` checks the whole file (throws `BlockstateException`: `<file>: <key>: <reason>`); `select(meta, resolved)` returns a cached `Selection` (properties + `Choice`s, or `missing()` with an `error()`); `onWarning` gets each missing-state line once; `metaValue(property, meta)` decodes a neighbour's metadata |
| `core/.../blockstate/Part`, `Choice` | One variant (`btaRotX/Y/Z()` quarter turns); a weighted list, `pick(seed)` stable per seed (SplitMix64 of the seed, mod total weight) |
| `core/.../blockstate/StairShape` | Vanilla stair shape rule from neighbours, tested as tables |
| `core/.../json/Json` | Small strict JSON reader shared by all formats (core has no dependencies); objects keep key order |
| `bta/.../blockstate/Blockstates.model(block, id[, resolver, inventoryModel])` | Reads `assets/<ns>/blockstates/<name>.json` through the texture packs, loads every model with `BlockModelDispatcher.loadDataModel`, returns a `BlockstateModel`. Call it from a `BLOCK_MODEL_RELOAD` listener. A bad file logs one error and draws the missing model |
| `bta/.../blockstate/BlockstateModel` | `BlockModelGeneric` subclass: overrides `renderAttached` to draw each selected part with `renderAttached(rotX, rotY, 0, 0, 0, 0, uvlock, cullFaces, override)`; `getModelFromData(meta)` gives the inventory model or the first part with each resolver property's first value |
| `bta/.../blockstate/StateResolver`, `NeighbourResolvers` | `resolve(world, pos, meta, blockstate)` for properties without `bits`; ready-made `stairs(isStairs)` and `connections(connectsTo)` (fence sides: same kind of block or an opaque cube) |

Matching rules: a variant key may list only some properties; the first key in file order whose pairs all match wins. Multipart draws every entry whose `when` matches, in order. Weighted entries in multipart get different seeds per entry. Unknown keys anywhere in the file are load errors. `x`/`y` accept numbers; `bits` and condition values also accept unquoted numbers and booleans.

## Model JSON

BTA's own reader handles vanilla model JSON (see `knowledge/bta/rendering.md`). We add:
- `"loader": "jsonextended:obj"` (and `betafront:obj`, `neoforge:obj`, `forge:obj`) to pick the OBJ model type.
- Clean handling of `texture_size`, `gui_light`, `render_type` from Blockbench exports.
