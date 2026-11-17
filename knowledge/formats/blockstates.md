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

- `properties` is ours (BTA has no named block states): it decodes metadata bits into named values. Blocks that depend on neighbours register a Java `StateResolver` instead.
- `variants` and `multipart` (`when` with `OR`/`AND`, `apply`) follow vanilla 1.21. `x`, `y` in multiples of 90; `uvlock`; `weight`.
- Rotation maps onto `StaticBlockModel.renderAttached(rotX, rotY, rotZ, ..., uvlock, ...)` in quarter turns.
- Results are cached per metadata value.
- Unknown property names or values are load errors, logged once with file and key.

## Model JSON

BTA's own reader handles vanilla model JSON (see `knowledge/bta/rendering.md`). We add:
- `"loader": "jsonextended:obj"` (and `betafront:obj`, `neoforge:obj`, `forge:obj`) to pick the OBJ model type.
- Clean handling of `texture_size`, `gui_light`, `render_type` from Blockbench exports.
