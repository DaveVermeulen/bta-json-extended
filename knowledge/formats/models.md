# Model JSON (as this mod handles it)

Status: phase 3 (model JSON polish), code done, waiting for Dave's in-game check.

BTA's own reader (`org.useless.dragonfly`) still builds every model; see [../bta/model-loading.md](../bta/model-loading.md). For our blocks and items we check the file first and decide what BTA's reader is given. No mixins: vanilla and other mods' models are untouched (that comes with the vanilla restyle, see decisions.md).

## What we do with each key

| Key | BTA 8.0.1 | Us |
| --- | --- | --- |
| `credit`, `groups`, element `name`/`color`/`uuid`... | ignored | accepted silently (Blockbench writes them) |
| `texture_size` | ignored | accepted silently: Blockbench's Java export already writes UVs as 0-16; a value that isn't `[w, h]` is a warning |
| `render_type` | ignored | the nearest one in the model or its parents sets the block's render pass: `translucent` → 1, `solid`/`cutout`/`cutout_mipped`/`cutout_mipped_all`/`tripwire` → 0. Any part of a blockstate being translucent makes the whole block translucent (one pass per block). Not a known type → error line, BTA's choice kept |
| `renderlayer` (BTA's own int) | used | left alone; `render_type` wins for blocks drawn through `Blockstates` |
| `gui_light` | ignored | 3D item models: `front` turns lighting off in the inventory (like flat items), `side` keeps it on (like block items, the default) |
| `loader` | ignored | warning for now: BTA's reader draws the elements. OBJ will use it |
| `overrides` | ignored | warning: not supported yet |
| anything else at top level, in an element or a face | ignored | warning naming the key |

## Readable errors

`core/.../model/ModelCheck` reads a model and its parents (through texture packs in game) and returns problems, each one line starting with the model id and key, e.g. `jsonextended:block/x: elements[0].faces.up.texture: every face needs a texture`.

- Errors: broken JSON, not an object, a face without a texture, `from` past `to`, bad rotation axis, bad `render_type`, parent loop, missing parent.
- Warnings: unknown keys, elements far outside the block, `#variables` never set (BTA draws its default texture), no elements anywhere (draws nothing), `loader`, `overrides`.
- `ModelCheck.unreadable(problems, id)`: missing file, broken JSON, not an object, or a face without a texture. These would crash BTA's reader during a reload, so `Models.load` gives the missing model instead (plus an error line).
- Each distinct line is logged once per game session, so texture pack reloads don't repeat it.

## Code

| Class | Does |
| --- | --- |
| `core/.../model/ModelCheck` | `check(id)`, `renderType(id)`, `hasErrors`, `unreadable` |
| `bta/.../model/Models` | `load(id)` (an OBJ `loader` goes to `ObjModels`; otherwise check, log, then `BlockModelDispatcher.loadDataModel` or the missing model), `renderLayer(id)`, `read`/`readAsset` through `texturePackList` |
| `bta/.../blockstate/BlockstateModel.renderLayer()` | from its models' `render_type` |
| `bta/.../item/JsonItemModel3d.renderGui` | `gui_light` |

## Test content (dev only)

- `test_glass` (block 9120): a Blockbench export with a 32×32 texture, `render_type: translucent` and a 45° turned element. Loads with no log lines.
- `test_broken_model` (block 9121): a trailing comma; draws the missing model and logs one error, the reload doesn't crash.
- `test.gem.front` (item 17106): the 3D gem with `gui_light: front`.
- `ModelCheckTest.devModelsAreClean`: every dev model except the two broken ones checks clean.
