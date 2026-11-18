# Item model JSON (as this mod supports it)

Status: phase 2 done (PR #11). The parser in `core/` is tested; rendering in `bta/` is built on
`knowledge/bta/item-rendering.md` and `item-rendering-3d.md` and was checked in game by Dave on 2026-11-18
(all six test items, display poses, layers, 3D gem, broken model).

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
| `bta/.../item/JsonItems.model(item, modelId)` | Reads through the texture packs, resolves, logs warnings; returns a `JsonItemModel`, a `JsonItemModel3d`, or a missing-texture `ItemModelStandard` plus one error line. Call it from `ClientEvents.ITEM_MODEL_RELOAD` and `addDispatch` the result |
| `bta/.../item/JsonItemModel` | `ItemModelStandard` subclass: BTA's own flat or extruded sprite per layer (each inside `GLRenderer.pushFrame/popFrame`), with `getDisplayPos` from JSON (translation /16, as BTA's serializer does). Unset contexts use BTA's defaults: `ItemModelStandard` for generated items, `ItemModelDispatcher.HANDHELD_*` / `HANDHELD_ROD_*` for tools and rods |
| `bta/.../item/JsonItemModel3d` | Element models: BTA reads the geometry (`BlockModelDispatcher.loadDataModel(id)`), drawn with `renderStandalone` (centred on the origin) after the pose, with the world atlas bound and `Shaders.ITEM`. The GUI copies `ItemModelBlock.renderGui` (depth kept, lighting on). `tintindex` faces are white for now |

Vanilla `display` numbers carry over unchanged. BTA's sprite and standalone block models are centred on the origin and rotate about it, which is the same as vanilla's centre pivot. BTA's built-in poses are vanilla's divided by 16.

## Not yet / to check in game

- `gui_light` is parsed but not applied: flat items get BTA's unlit GUI, and 3D items get a lit one.
- Layers are drawn at the same depth, so the in-game check is whether layer1 shows on top everywhere.
- BTA negates translate X and rotate Y/Z for `leftHanded`, and its own left-hand defaults are stored already mirrored. We hand it vanilla's left-hand entries the same way, so check that the left hand looks right.
- Item and block textures share the world atlas (HalpLibe stitches both folders), so item models may use either.
