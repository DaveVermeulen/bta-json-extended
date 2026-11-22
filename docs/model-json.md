# Model JSON and Blockbench

BTA 8.0.1 already reads vanilla-style block model JSON (`models/block/*.json`): `parent`, `textures`, `elements`, faces, rotations, `display`. Blockbench's "Java Block/Item" export works as it is. This library doesn't replace that reader. For models loaded through it (`Blockstates`, `JsonItems`, `Models.load`), it checks each file first and handles the keys BTA ignores.

## Extra keys

| Key | What we do |
| --- | --- |
| `render_type` | `translucent` puts the block in BTA's translucent pass (stained glass, ice). `solid`, `cutout`, `cutout_mipped` keep the normal pass. If any model of a blockstate is translucent, the whole block is |
| `gui_light` | For 3D item models, see [item-models.md](item-models.md) |
| `loader` | `jsonextended:obj` (or `betafront:obj`, `neoforge:obj`, `forge:obj`) makes it an OBJ model, see [obj-models.md](obj-models.md). Any other loader logs a warning, and BTA draws the elements |
| `groups` | Blockbench's outliner. Accepted quietly. [Tile entity models](tile-entities.md) use it to find the parts that move |
| `credit`, `texture_size`, element `name`/`color` | Accepted quietly |

## Block items

A block's item uses the block model, posed by the model's `display` block. A model with no `display` and no parent that has one is drawn untransformed: far too big in the hand and in the inventory. Either add a `display` block (Blockbench's Display tab writes one), or use vanilla `block/block`'s poses:

```json
"display": {
  "gui": {"rotation": [30, 225, 0], "scale": [0.625, 0.625, 0.625]},
  "ground": {"translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
  "fixed": {"scale": [0.5, 0.5, 0.5]},
  "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
  "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4, 0.4, 0.4]},
  "firstperson_lefthand": {"rotation": [0, 225, 0], "scale": [0.4, 0.4, 0.4]}
}
```

## Error messages

Each problem is one log line starting with the model id and the key, logged once per game session:

- **Errors**: broken JSON, a face without a texture, `from` past `to`, a bad rotation axis, an unknown `render_type`, a parent loop or a missing parent. A file that BTA's reader would crash on draws the missing model instead.
- **Warnings**: unknown keys, elements far outside the block, a `#texture` that's never set, a model with no elements anywhere, an unknown `loader`, `overrides`.

## Vanilla parents

BTA ships its own parents, like `minecraft:block/cube_all`, `minecraft:block/stairs` and `minecraft:block/slab/lower`. The full list with counts is in [knowledge/bta/vanilla-parents.md](../knowledge/bta/vanilla-parents.md).
