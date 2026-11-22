# Item models

BTA 8.0.1 can't read item model files. With this library an item's look comes from a vanilla 1.21-style model file.

## The file

`assets/<modid>/models/item/<name>.json`:

```json
{
  "parent": "item/handheld",
  "textures": {
    "layer0": "mymod:item/ruby_sword",
    "layer1": "mymod:item/ruby_sword_glint"
  },
  "display": {
    "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}
  }
}
```

### Flat items

The parent chain ends in one of the built-in parents. BTA ships no such files, so we provide them, and a texture pack that has one overrides ours.

| Parent | Looks like |
| --- | --- |
| `item/generated` (or `builtin/generated`) | A normal item |
| `item/handheld` | A tool or weapon, held at an angle |
| `item/handheld_rod` | A fishing rod |

Textures are `layer0`, `layer1`, ... drawn on top of each other, up to the first missing number.

### 3D items

A model whose chain has `elements` is drawn as a 3D model, read by BTA's own model reader like a block. Export it from Blockbench as a "Java Block/Item" model and put it in `models/item/`.

### Keys

| Key | What it does |
| --- | --- |
| `parent` | Another item model, or a built-in parent above |
| `textures` | `layerN` for flat items, any names for 3D items. `#name` refers to another entry. Ids without a namespace get `minecraft:` |
| `display` | Poses for `gui`, `ground`, `fixed`, `head`, `thirdperson_righthand`/`lefthand`, `firstperson_righthand`/`lefthand`: `rotation` (degrees), `translation` (pixels, -80 to 80), `scale` (up to 4). Merged with the parent's. A left hand without an entry uses the right hand's |
| `gui_light` | 3D items only: `side` (lit like a block, the default) or `front` (flat-lit like a normal item) |
| `elements` | Makes it a 3D item |

Contexts that no file sets use BTA's own defaults, so most items need no `display` at all. Blockbench's extra keys (`credit`, `texture_size`, `groups`) are accepted quietly. `overrides` isn't supported yet and logs a warning.

## Registering

```java
ClientEvents.ITEM_MODEL_RELOAD.listen(Key.of(MOD_ID), dispatcher -> {
    dispatcher.addDispatch(JsonItems.model(MyItems.RUBY, "mymod:item/ruby"));
    dispatcher.addDispatch(JsonItems.model(MyItems.RUBY_SWORD, "mymod:item/ruby_sword"));
});
```

A model that can't be loaded logs one error and draws BTA's missing texture.

Block items don't need this: they use the block's model. Give the block model a `display` block for its item poses, or a parent that has one (see [model-json.md](model-json.md#block-items)).
