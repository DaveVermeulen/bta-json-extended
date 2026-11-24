# Tile entities and their renderers (BTA 8.0.1)

Read from the 8.0.1 jar (Vineflower) and HalpLibe 6.2.0 sources; signatures checked with `javap`. For drawing block geometry yourself, see also [static-model.md](static-model.md) and [item-rendering-3d.md](item-rendering-3d.md).

## No vanilla chest renderer

- BTA 8.0.1 has **no chest TE renderer and no lid animation**. `TileEntityChest` has no `lidAngle` or `numUsingPlayers`, and chests are plain block models.
- The vanilla `TileEntityRenderDispatcher.reload()` registers only these renderers: sign, flag, mob spawner, moving piston, flower jar, butterfly jar, gold mesh, statue.
- The two that draw **block geometry** are the useful models:
  - **`TileEntityRendererMovingPiston`**: world-style drawing (`renderAttached` / `renderNoCulling`) with real world lighting.
  - **`TileEntityRendererFlowerJar`**: a block's `renderStandalone` under the model matrix.
- Sign and statue use geo (`.geo.json`) entity models via `setModel` / `getModel`.

## The renderer class

`net.minecraft.client.render.tileentity.TileEntityRenderer<T extends TileEntity>`:
- `abstract void doRender(TessellatorGeneral tess, T te, double x, double y, double z, float partialTick)`
- `void tick()`: once per client tick, for all renderers.
- `void init(Class<? extends T>, TileEntityRenderDispatcher)` sets `renderDispatcher`.
- `void onWorldChanged(World)`; `boolean isVisible(T, ICamera, float)` (default true).
- `protected void bindTexture(String path)`: a loose texture, not an atlas.
- `final setModel(String localId, String modelId, double inflation)` / `protected getModel(String localId)`: geo models.

### What `doRender` receives

`RenderGlobal.renderEntities` → `TileEntityRenderDispatcher.renderTileEntity(tess, camera, world, te, partialTick)`:
1. Skips the TE if its squared distance > `4096 * ENTITY_RENDER_DISTANCE`, or `!isVisible(...)`.
2. `GLRenderer.setLightmapCoord1i(world.getLightmapCoord(te.x, te.y, te.z, 0))`: the **world light at the TE's own block**, as a global default.
3. `GLRenderer.setColor3f(1, 1, 1)`.
4. `doRender(tess, te, te.x - renderPosX, te.y - renderPosY, te.z - renderPosZ, partialTick)`. So **x, y, z are the block's minimum corner relative to the camera**, not 0..1, and not the block centre.

- Nothing pushes the matrix or sets a shader for you; other entities ran just before. Every renderer must set its own shader, texture and states, and use `pushFrame` / `popFrame`.
- A TE is only added to the render list if its block has `isEntityTile` **and** `TileEntityRenderDispatcher.hasRenderer(te)`. That's checked in chunk pass 0, when the chunk is built.

## Two ways to draw block quads

**A. Model-matrix way** (flower jar). Simplest for our own quads with per-part transforms:

```
GLRenderer.pushFrame();
GLRenderer.setShader(Shaders.ITEM);                  // the jar doesn't, but it should
TextureRegistry.worldAtlas.bind();                   // block + item textures live here
GLRenderer.enableState(State.BLEND);
GLRenderer.setBlendFunc(BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA);
GLRenderer.modelM4f().translate((float) x, (float) y, (float) z);   // now 0..1 = the block
// per part: pushFrame, translate(pivot), rotateX/Y/Z(radians), translate(-pivot), draw, popFrame
tess.startDrawingQuads();
tess.setLightmapCoord1i(light);   // te.worldObj.getLightmapCoord(x, y, z, 0)
tess.setColor1i(-1);              // or a tint
tess.setNormal(nx, ny, nz);       // per face, model-space; the ITEM shader shades by it
tess.addVertexWithUV(px, py, pz, icon.getSubIconU(u), icon.getSubIconV(v));
...
tess.draw();
GLRenderer.disableState(State.BLEND);
GLRenderer.popFrame();
```

- `BlockModel.renderStandalone(tess, meta, light)` draws **centred on the origin** and does its own start/draw. The jar uses translate(x, y, z), scale 0.75, translate(0.66, 0.75, 0.66) to put it back in the block.
- Our own quads in 0..1 need only `translate(x, y, z)`.

**B. World way** (moving piston). Gives per-corner world light from `LightingCache`, but no AO: the ITEM shader drops it (below).
- `worldAtlas.bind()`, `setShader(Shaders.ITEM)`, blend on, `disableState(CULL_FACE)`.
- `tess.startDrawingQuads()`, then `tess.setTranslation(x - te.x, y - te.y, z - te.z)`. `renderAttached` adds the world position back.
- Call `blockModel.renderNoCulling(tess, world, pos)` (or `renderAttached` on a `StaticBlockModel`).
- `tess.setTranslation(0, 0, 0)`, then `tess.draw()`.
- Per-part transforms are awkward here: the tessellator only offers translation. Use way A for moving parts.

**Lighting under `Shaders.ITEM`:**
- The world brightness comes from the lightmap coordinate (block + sky light byte). Set it per vertex, or rely on the dispatcher's global `GLRenderer.setLightmapCoord1i`.
- Directional face shading comes from the normal while global lighting is on, which it is in the world (item GUI turns it off).
- Directional shading is the same as in chunks: terrain and TEs both draw between `Lighting.enableLight()` / `disable()`, and both shaders use `lightModelAmbient + lightSourceDiffuse1 * (dir1 + dir2)` (checked in the jar's shaders, 2026-11-23).
- **No AO under ITEM.** `item.vsh`/`item.fsh` have no shade input, so `setShade1i` is dropped (moving pistons lose AO too). Only `terrain.vsh` reads it (`aLightShade`, location 4; `terrain.fsh` does `mix(1, Shade, shadeEffect)`). Vertex colour can't stand in: it only tints pixels the colour mask marks. Exact AO means drawing with `Shaders.TERRAIN`: see [terrain-buffers.md](terrain-buffers.md). `TileEntityMesh` does that since 2026-11-23, with the item path below as fallback (`-Djsonextended.tileEntityItemShader=true`).
- The item-shader fallback (`tileentity/TileEntityMesh`, also the terrain path with smooth lighting off), shared by both TE renderers): each face looks towards the side its normal points to most, and each corner blends that neighbour's light index with the block's own by depth (1 on that side's plane, 0 at the far side), per channel, as `ObjSmoothLight` does for its two layers. Opaque neighbour: own light. (First version used neighbour light only for faces flat on the edge; Dave saw the inset glTF lantern's sides stay unlit, 2026-11-23.) Light is read at most once per 50 ms; faces are rebuilt only when pose, facing or atlas change.
- Atlas: there is no separate `blockAtlas`. `TextureRegistry.worldAtlas` holds `block/`, `item/` and `particle/`.
- UVs: `icon.getSubIconU(u)` / `getSubIconV(v)` with u, v in 0..1 inside the icon (JSON uv / 16).

## The TileEntity class

`net.minecraft.core.block.entity.TileEntity` (abstract, implements `ICarriable`):
- Fields: `World worldObj`, `TilePos tilePos`.
- `void tick()`: server/world tick, every game tick.
- `abstract void readAdditionalData(CompoundTag)` / `abstract void writeAdditionalData(CompoundTag)`. These are called by the final `readFromNBT` / `writeToNBT`, which handle `x`, `y`, `z` and the `id`.
  - `writeToNBT` throws `... is missing a mapping!` if the class isn't registered.
- `void setChanged()`, `Packet getDescriptionPacket()` (default `null`), `int getBlockMeta()`, `Block<?> getBlock()`, `invalidate()` / `isInvalid()`.
- It needs a **public no-arg constructor**: `TileEntityDispatcher.createAndLoadEntity` uses `newInstance()`.

## Registering

- **TE class (common side):** `TileEntityDispatcher.addMapping(Class<? extends TileEntity>, NamespaceID)`, or HalpLibe `EntityHelper.addMapping(...)`, which calls the same. The id goes into the save file: e.g. `NamespaceID.fromPool("jsonextended", "model_te")`. Duplicate ids throw.
- **Block → TE:** `Block.withEntity(Supplier<TileEntity>)` sets `isEntityTile = true` and `entitySupplier`. HalpLibe: `BlockBuilder.setTileEntity(Supplier<TileEntity>)`; it returns a copy.
- **Renderer (client):** `ClientEvents.TILE_ENTITY_RENDERER_RELOAD.listen(Key.of(MOD_ID), d -> d.assignRenderer(OurTE.class, new OurRenderer()))`.
  - Type: `SortedBaseEvent<Consumer<TileEntityRenderDispatcher>>`. It's fired by HalpLibe's `@Inject(TAIL)` on `TileEntityRenderDispatcher.reload()`, at startup and on every `TexturePackList.refresh()` (see [registration.md](registration.md)).
  - `assignRenderer(Class<? extends T>, TileEntityRenderer<T>)` also calls `renderer.init`. Subclasses inherit a parent's renderer.
  - `ModelHelper.setTileEntityModel(Class, TileEntityRenderer)` is deprecated; use the event.

## Geo models

- `org.useless.dragonfly.data.entity.mojang.EntityGeometryMojangData` (Bedrock `.geo.json`) is drawn by `models.entity.mojang.StaticEntityModelMojang`; `BoneTransform` exists.
- Vanilla geo files are listed in `assets/minecraft/models/entity/models.json`.
- No animation runtime in 8.0.1: `pig.animation.json` and `wolf.animation.json` ship, but nothing reads them.

## Hiding the block's static model

Register `new BlockModelEmpty<>(block)` for the block, in `BLOCK_MODEL_RELOAD`. Its `render` draws nothing and its `renderStandalone` draws nothing.
- `BlockModelEmpty.shouldItemRender3d()` is overridden: probably false, not checked. So the block item then needs its own item model (or a `BlockModelGeneric` kept for the item, with `render` overridden to draw nothing).
- Give the `BlockLogic` `isSolidRender() == false` and `isCubeShaped() == false`, so neighbours keep their faces. Our dev `ModelLogic` already does this.
- `renderLayer()` only affects the chunk passes, so it doesn't matter for TE-drawn geometry; keep it 0.

## Getting state to the renderer

- **Single player:** most likely the client renders the same `World` and `TileEntity` objects that tick (I didn't verify BTA's single-player setup). A field set in `BlockLogic.onInteracted(World, TilePosc, Player, Side, double, double)`, or in `TileEntity.tick()`, is visible to `doRender` next frame. Keep `prev` and `current` values and lerp with `partialTick` for smooth motion.
- **Block events:**
  - `world.triggerEvent(TilePosc, int a, int b)` → `BlockLogic.triggerEvent(World, TilePosc, int, int)`.
  - Use it for one-shot changes, like vanilla note blocks and pistons. Server → client in multiplayer.
- **Other options:**
  - `world.markBlockNeedsUpdate(TilePosc)` re-renders the chunk; it isn't needed for TE-rendered geometry.
  - `getDescriptionPacket()` would send TE NBT to clients in multiplayer; the default is `null`.
- Multiplayer sync is not needed for the first version.
