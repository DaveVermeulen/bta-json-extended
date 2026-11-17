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

**Open (Platform, phase 3 day 1):** where `DisplayPos` is applied for block items, and whether `ItemModel` can apply it itself or a mixin is needed. Fill in the full signatures above from the jar.
