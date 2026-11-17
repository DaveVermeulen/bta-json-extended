# Registration, reloads, texture packs

## HalpLibe 6.2.0+8.0.1

- `ClientEvents.BLOCK_MODEL_RELOAD`, `ITEM_MODEL_RELOAD`, `ENTITY_RENDERER_RELOAD`, `TILE_ENTITY_RENDERER_RELOAD`, `BLOCK_COLOR_RELOAD`: `SortedBaseEvent<Consumer<Dispatcher>>`; listen with `.listen(Key.of(MOD_ID), consumer)`. Fired whenever the dispatcher is rebuilt.
- `ModelHelper.setBlockModel(block, Supplier<BlockModel<?>>)`, `setItemModel(item, Supplier<ItemModel>)`, `setEntityModel(Class, EntityRenderer)`, `setTileEntityModel(Class, TileEntityRenderer)`, `setBlockColor(...)`.
  **Deprecated for removal**, and `ModelHelper.blockModelDispatcher` is still null in the Fabric `client` entrypoint (NPE at startup). Use the `*_RELOAD` events above instead (as Betafront does).
- `ModelEntrypoint` interface: `initBlockModels`, `initItemModels`, `initEntityModels`, `initTileEntityModels`, `initBlockColors`.
- `HalpLibe.registerMod(id, true)`: the `true` stitches `assets/<id>/textures/block/*` into the atlas.
- Fabric `client` entrypoint works in BTA's loader.

## Texture packs

`Minecraft.getMinecraft().texturePackList.getResourceAsStream("/assets/ns/path")` tries selected packs (highest first), then the default. Changing packs calls `refresh()`, which rebuilds `BlockModelDispatcher` (so `BLOCK_MODEL_RELOAD` fires and `BlockModelMojangData.Cache` is reset) and the atlas. Atlas entries come from the jars; their images come through the packs.

## Blocks

- `BlockBuilder.build(name, numericId, logicSupplier)`, ids 0..16383. Every `set...` returns a **copy**: use the return value.
- Lang key from `build("example_crystal")`: `tile.<modid>.example.crystal`. Lang file `assets/<ns>/lang/en_US/en_US.lang`.
- Creative menu: only blocks with `setCreativeInventoryPlacement(new CreativeInventoryPlacement.Category(...))`.
- Placement: `BlockLogic#onPlacedByMob(World, TilePosc, Side, Mob, double, double)`, `world.setBlockDataNotify(TilePosc, int)`.
- Example blocks are dev-only: `FabricLoader.isDevelopmentEnvironment()`.
