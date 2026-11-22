# Blockstates

A blockstate file picks a block's model from its state. It follows vanilla 1.21's format, plus one addition: BTA has no named block states, only 4 bits of metadata, so the file says which bits mean what.

## The file

`assets/<modid>/blockstates/<name>.json`:

```json
{
  "properties": {
    "facing": {"bits": "0-1", "values": ["south", "west", "north", "east"]},
    "lit":    {"bits": "2",   "values": ["false", "true"]}
  },
  "variants": {
    "facing=north,lit=false": {"model": "mymod:block/lamp"},
    "facing=east,lit=false":  {"model": "mymod:block/lamp", "y": 90},
    "facing=south,lit=true":  [
      {"model": "mymod:block/lamp_on", "y": 180, "weight": 3},
      {"model": "mymod:block/lamp_on_alt", "y": 180}
    ]
  }
}
```

### properties

| Key | What it does |
| --- | --- |
| `bits` | One bit (`"2"`) or a range (`"0-1"`) of the block's metadata. The value is the bits read as a number, counting from 0 into `values` |
| `values` | The names, in order. Required |

A property **without** `bits` comes from your Java code (see [State from neighbours](#state-from-neighbours)). Metadata that points past the end of `values` draws the missing model and logs once.

### variants and multipart

Both follow vanilla:

- `variants`: keys are `property=value` pairs separated by commas. A key may list only some properties; the first key in the file whose pairs all match wins. `""` matches everything.
- `multipart`: every entry whose `when` matches is drawn, in order. `when` takes `{"north": "true"}`, `{"north": "true|false"}`, `{"OR": [...]}` and `{"AND": [...]}`. An entry without `when` is always drawn.
- Each model entry: `model`, `x` and `y` (multiples of 90), `uvlock`, and `weight` in a list. A weighted pick is stable per block position.

Unknown property names, unknown values and unknown keys are load errors.

## Registering

```java
ClientEvents.BLOCK_MODEL_RELOAD.listen(Key.of(MOD_ID), dispatcher ->
        dispatcher.addDispatch(MyBlocks.LAMP, Blockstates.model(MyBlocks.LAMP, "mymod:lamp")));
```

Your block sets the metadata as usual, for example in `onPlacedByMob` with `world.setBlockDataNotify(pos, meta)`.

## State from neighbours

Stairs and fences change shape with their neighbours, which isn't stored in metadata. Declare those properties without `bits` and pass a `StateResolver`:

```java
dispatcher.addDispatch(MyBlocks.FENCE, Blockstates.model(MyBlocks.FENCE, "mymod:fence",
        NeighbourResolvers.connections(block -> block == MyBlocks.FENCE),
        "mymod:block/fence_inventory"));   // the model for the item; null uses the first part
```

- `NeighbourResolvers.stairs(isStairs)` gives vanilla's `shape` (`straight`, `inner_left`, ...). It needs metadata properties named `facing` (north/east/south/west) and `half` (bottom/top), and reads neighbouring stairs with the same layout.
- `NeighbourResolvers.connections(connectsTo)` gives `north`, `east`, `south`, `west` as `"true"`/`"false"`: true for a block `connectsTo` accepts, or an opaque cube.
- Or write your own: `(world, pos, meta, blockstate) -> Map.of("powered", ...)`. It runs on every draw, so keep it cheap. `blockstate.metaValue(property, meta)` decodes a neighbour's metadata.

The files of the test stairs and fence are good starting points: `bta/src/main/resources/assets/jsonextended/blockstates/test_stairs.json` and `test_fence.json`.
