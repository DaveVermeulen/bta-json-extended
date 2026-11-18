# Items (BTA 8.0.1)

`net.minecraft.client.render.item.model.ItemModel` (abstract), constructor `ItemModel(Item)`:

- `abstract void render(TessellatorGeneral, Entity holder, ItemStack, ...)` (remaining parameters: check the jar before use)
- `abstract void renderItemOverlayIntoGUI(TessellatorGeneral, FontRenderer, TextureManager, ...)`
- `void renderGui(TessellatorGeneral, Entity holder, ItemStack, int x, int y, byte lightIndex, float partialTick)`
- `void renderItemEntity(TessellatorGeneral, ItemStack, boolean items3d, ...)`
- `void renderFirstPerson(TessellatorGeneral, ItemRenderer, Player, ...)`
- `abstract IconCoordinate getIcon(Entity, ItemStack)`

Subclasses: `ItemModelStandard` (one icon), `ItemModelBlock`, `ItemModelBow`, `ItemModelBucket*`, ... Registered in `ItemModelDispatcher`; mods use `ModelHelper.setItemModel` or `ITEM_MODEL_RELOAD`.

Block items already use the block model's `display` via `BlockModel.getItemDisplayPos(id)`; missing left-hand poses copy the right-hand ones.

## Display transforms: no mixin needed

`ItemModel` has an overridable `DisplayPos getDisplayPos(String id)`. `ItemModelBlock` overrides it to return the block model's `getItemDisplayPos(id)` when the block renders 3D, and applies it as: translate (x mirrored for the left hand), rotate X, Y, Z in degrees (Y and Z mirrored for the left hand), then scale. So a JSON item model only has to override `getDisplayPos` with its `display` entries. Full signatures: [api/items.txt](api/items.txt).

## Creating an item (8.0.1, read from the jar)

`Item(NamespaceID namespaceId, String translationKey, int id)`, plus the convenience `Item(String translationKey, String namespaceId, int id)`. **The order differs between the two.**

- `translationKey`: dotted name without the prefix, e.g. `"ingot.iron"`. Stored as `key = "item." + translationKey`.
- `namespaceId`: the registry id and default texture, e.g. `"minecraft:item/ingot_iron"`.
  - Parsed with `NamespaceID.fromPool(...)`; a bad id throws `IllegalArgumentException`.
  - `ItemModelStandard(item)` uses it as the icon (`TextureRegistry.getTexture(item.namespaceID)`).
- `id`: the slot in `Item.itemsList`, which is `new Item[32768]`, so valid slots are 0..32767.
  - Ids 0..16383 belong to blocks (`Blocks.blocksList` is `new Block[16384]`), and block items use the block's id.
  - Plain items use 16384..32767; vanilla's go up to 16568.
  - The constructor throws if the slot or the namespace id is already taken.
- Vanilla example: `new Item("ingot.iron", "minecraft:item/ingot_iron", 16393)`.

Lang keys:
- Display name: `getKey() + ".name"`. Tooltip: `getKey() + ".desc"`.
- Without HalpLibe that is `item.<translationKey>.name`.

HalpLibe `ItemBuilder(modId).build(item)` (HalpLibe 6.2.0 source):
- Rewrites the key to `item.<modId>.<translationKey>` and fixes `Item.nameToIdMap`.
- So the lang line is `item.<modid>.<translationKey>.name=...`.
- It also applies the builder's tags, stack size, container item, max damage and creative placement.
- `setKey` is deprecated: put the key in the constructor.

No real mod example of this is in the Gradle cache. Built from the code above, for our ids:

```java
Item ruby = new ItemBuilder("jsonextended")
        .build(new Item("ruby", "jsonextended:item/ruby", 17000));
// key "item.jsonextended.ruby"; lang: item.jsonextended.ruby.name=Ruby
// default icon: assets/jsonextended/textures/item/ruby.png
```
