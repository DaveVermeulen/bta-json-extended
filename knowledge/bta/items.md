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
