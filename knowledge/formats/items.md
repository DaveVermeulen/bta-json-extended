# Item model JSON (as this mod supports it)

Status: phase 2, step 1. The parser is in `core/` and tested; rendering in `bta/` waits on
`knowledge/bta/item-rendering.md` (how BTA draws items, read from the jar).

## Files

`assets/<ns>/models/item/<name>.json`, vanilla 1.21 style:

```json
{
  "parent": "item/handheld",
  "textures": {"layer0": "mymod:item/ruby_sword", "layer1": "mymod:item/ruby_sword_glint"},
  "display": {"gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}},
  "gui_light": "front"
}
```

- **Flat items**: parent chain ends in `item/generated`, `item/handheld`, `item/handheld_rod` or `builtin/generated`. These are built in, because BTA ships no such files; a pack that has one of them overrides ours. Layers are `layer0`, `layer1`, ... up to the first gap (a gap logs a warning).
- **3D items**: the nearest file in the chain with `elements` makes it a model (`elementsFrom`). The geometry is read by BTA's own model reader, as for blocks.
- **Merging**: `textures` and `display` take the parent's entries, then the child's on top. `gui_light` comes from the nearest file that sets it, and defaults to `front` for flat items and `side` for models. `#name` texture references are followed, and ids without a namespace get `minecraft:`.
- **display contexts**: `thirdperson_righthand/lefthand`, `firstperson_righthand/lefthand`, `gui`, `head`, `ground`, `fixed`. A missing left hand falls back to the right hand's entry. A context that no file sets is left to the renderer, which uses BTA's own defaults (`ItemModelStandard.DEFAULT_ITEM_*`, `ItemModelDispatcher.HANDHELD_*`). Vanilla clamps translation to ±80 pixels and scale to 4 (`DisplayTransform.clamped()`).
- **Accepted and ignored**: `credit`, `texture_size`, `groups`, `ambientocclusion`, `render_type`, `loader`, `renderlayer`, `particles`.
- **Warned and ignored**: `overrides` (not supported yet), unknown display contexts, unknown keys.
- **Errors** (one line naming the model and key, then the missing model): no file, missing parent, parent loop (or more than 32 parents), no `layer0` on a flat item, a `#reference` that doesn't resolve, a chain with neither elements nor a generated parent, a bad `gui_light`, and a transform that isn't three numbers.

## Code

| Where | What |
| --- | --- |
| `core/.../item/ItemModelFile` | One file as written: `parse(id, json)` |
| `core/.../item/ItemModels` | `new ItemModels(id -> json or null).resolve(id)` follows parents and merges; `normalize(id)` |
| `core/.../item/ResolvedItemModel` | `style()` (GENERATED, HANDHELD, HANDHELD_ROD, MODEL), `layers()`, `textures()`, `display(context)`, `guiLight()`, `elementsFrom()`, `warnings()` |
| `core/.../item/DisplayTransform` | Rotation in degrees, translation in pixels, scale; `clamped()` |
| `core/.../json/Json` | Shared strict JSON reader (moved out of `blockstate/`) |

## Open for the rendering step

- Whether BTA mirrors left-hand poses itself. If it does, an explicit vanilla `*_lefthand` entry, which is already mirrored, must not be mirrored again.
- Which atlas `item/` textures live in, and whether `HalpLibe.registerMod(id, true)` stitches `textures/item`.
- 3D items drawn from item-model elements need block-atlas textures, or a bound item atlas.
