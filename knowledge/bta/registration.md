# Registration, reloads, texture packs

## HalpLibe 6.2.0+8.0.1

- `ClientEvents.BLOCK_MODEL_RELOAD`, `ITEM_MODEL_RELOAD`, `ENTITY_RENDERER_RELOAD`, `TILE_ENTITY_RENDERER_RELOAD`, `BLOCK_COLOR_RELOAD`: `SortedBaseEvent<Consumer<Dispatcher>>`; listen with `.listen(Key.of(MOD_ID), consumer)`. Fired whenever the dispatcher is rebuilt.
- `ModelHelper.setBlockModel(block, Supplier<BlockModel<?>>)`, `setItemModel(item, Supplier<ItemModel>)`, `setEntityModel(Class, EntityRenderer)`, `setTileEntityModel(Class, TileEntityRenderer)`, `setBlockColor(...)`.
  **Deprecated for removal**, and `ModelHelper.blockModelDispatcher` is still null in the Fabric `client` entrypoint (NPE at startup). Use the `*_RELOAD` events above instead (as Betafront does).
- `ModelEntrypoint` interface: `initBlockModels`, `initItemModels`, `initEntityModels`, `initTileEntityModels`, `initBlockColors`.
- `HalpLibe.registerMod(id, true)`: the `true` stitches `assets/<id>/textures/block/*` into the atlas.
- Fabric `client` entrypoint works in BTA's loader.

## Texture packs

`Minecraft.getMinecraft().texturePackList.getResourceAsStream("/assets/ns/path")` tries the selected packs (highest first), then the default. Atlas entries come from the jars; their images come through the packs.

## What reloads models (8.0.1, read from the jar)

`TexturePackList.refresh()` is the only thing that rebuilds the model dispatchers. In order, it calls:

1. `BlockModelDispatcher.getInstance().reload()`, which runs:
   - `BlockModelMojangData.Cache.resetCache()`, so every JSON model is parsed again;
   - `dispatches.clear()`, then re-adds every vanilla block;
   - HalpLibe's `@Inject(TAIL)` mixin: `initModels` entrypoints, `initBlockModels`, then **`ClientEvents.BLOCK_MODEL_RELOAD`**.
2. `ItemModelDispatcher.getInstance().reload()`, with the same HalpLibe mixin, then **`ITEM_MODEL_RELOAD`**.
3. `textureManager.refreshTexturesAndDisplayErrors()`: restitches the atlases and logs `Active Asset Packs: ...`.
4. `renderGlobal.allChanged()`, which rebuilds the chunks.
5. Sounds, `BlockColorDispatcher.reload()`, `EntityRendererDispatcher` / `TileEntityRenderDispatcher.reload()`, renderer and `Shaders.reload()`.

So our `*_RELOAD` listeners run again on every refresh, and must re-add every model each time: the dispatch map was cleared.

Who calls `refresh()`:

| Trigger | Call chain |
| --- | --- |
| Texture pack screen | `TexturePackList.setTexturePack` / `unsetTexturePack` / `shiftPack` only set `isDirty`. `refreshIfReady()` runs `refresh()` later, if dirty, from `ScreenOptions.buttonClicked` / `keyPressed` / `mouseClicked` / `removed()` and `ScreenPaged.buttonClicked` / `onPageChanged`. So the reload happens when you click Done or leave the page, not on each click. |
| Debug reload key | `Minecraft.runTick` → `KEY_RELOAD.isPressEvent()` → private `Minecraft.forceReload()`. That logs `FORCING RELOAD!` (INFO), reloads I18n and a new `SoundEngine`, then `texturePackList.refresh()` and `renderGlobal.allChanged()`. |
| Model viewer | `ScreenModelViewer` (a lambda in `init`) calls `refresh()` directly. |

- `KEY_RELOAD` is `key.debug.reload`, default `R`, in `DEBUG_KEYS`: most likely **F3+R** (F3+T in modern MC). I didn't confirm how the debug modifier is checked.
- `KEY_RELOAD_CHUNKS` (`key.debug.reloadchunks`) only rebuilds chunks and doesn't reload models.

**Not a model reload:** `Active Asset Packs:` is logged by `CustomAtlasHandler.beforeRefreshTextures()` on *every* texture refresh. That includes option callbacks that call only `refreshTexturesAndDisplayErrors()`: texture animations, custom animations, mipmap levels, mipmap type and holiday textures. A log with extra `Active Asset Packs` lines but no `FORCING RELOAD!` and no repeated model messages was a texture-only refresh. The dispatchers and our listeners did not run.

## The missing model

- `BlockModelMojangData.Cache.getMissingModel()` returns one static `MISSING_MODEL`, built once at class load. It's the **same object** on every call and across reloads; `resetCache()` doesn't touch it, because it isn't in the cache map.
- `.asModel()` builds a **new** `StaticBlockModelMojang` each time. Each compile logs 1 error (`does not have assigned texture data!`) and 7 warnings (`#particle_*`, `#overlay`), because `MISSING_MODEL` has no `textures` map. Compile it once per reload, and only when needed.
- Its faces name `minecraft:block/missing` directly instead of a `#symbol`. With no textures map, the lookup misses, so it most likely draws `BlockModelStandard.BLOCK_TEXTURE_UNASSIGNED`, not the `block/missing` image. Use our own fallback model if the look matters.

## Blocks

- `BlockBuilder.build(name, numericId, logicSupplier)`, ids 0..16383. Every `set...` returns a **copy**: use the return value.
- Lang key from `build("example_crystal")`: `tile.<modid>.example.crystal`. Lang file `assets/<ns>/lang/en_US/en_US.lang`.
- Creative menu: only blocks with `setCreativeInventoryPlacement(new CreativeInventoryPlacement.Category(...))`.
- Placement: `BlockLogic#onPlacedByMob(World, TilePosc, Side, Mob, double, double)`, `world.setBlockDataNotify(TilePosc, int)`.
- Example blocks are dev-only: `FabricLoader.isDevelopmentEnvironment()`.
