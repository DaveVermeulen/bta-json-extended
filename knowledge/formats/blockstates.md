# Blockstate and model JSON (as this mod supports them)

Status: design. Update when the Blockstates agent implements it.

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

## Model JSON

BTA's own reader handles vanilla model JSON (see `knowledge/bta/rendering.md`). We add:
- `"loader": "jsonextended:obj"` (and `betafront:obj`, `neoforge:obj`, `forge:obj`) to pick the OBJ model type.
- Clean handling of `texture_size`, `gui_light`, `render_type` from Blockbench exports.
