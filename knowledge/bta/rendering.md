# BTA 8.0.1 rendering

All names checked against the decompiled 8.0.1 merged jar (2026-11-17).

## BTA already has JSON block models

`org.useless.dragonfly` (DragonFly's static half) is part of the game jar. 1,400+ vanilla block models use it.

| Class | Role |
| --- | --- |
| `org.useless.dragonfly.data.block.mojang.BlockModelMojangData` | Parsed model JSON. `Cache.loadModelData(TexturePackList, id)`, `Cache.resetCache()`, `Cache.getMissingModel()` |
| `...data.block.mojang.Element`, `Face`, `Rotation` | `elements`, faces, element rotation |
| `...data.block.mojang.CompiledBlockModelMojangData` | Baked vertex positions, UVs, normals per element |
| `org.useless.dragonfly.models.block.StaticBlockModel` | **The interface a model type implements** (see below) |
| `...models.block.mojang.StaticBlockModelMojang` | BTA's implementation for JSON models, incl. smooth lighting |
| `org.useless.dragonfly.DisplayPos` | One `display` entry: `rotation`, `translation`, `scale` |
| `net.minecraft.client.render.block.model.generic.BlockModelGeneric<T>` | A `BlockModel` that draws a `StaticBlockModel`. `getModelFromData(meta)` picks the model; ~60 subclasses hard-code meta to model |

Load a model: `BlockModelDispatcher.loadDataModel("ns:block/name")` (public static; returns the missing model when absent). Path: `/assets/<ns>/models/<path>.json`, read through `texturePackList`.

### JSON keys BTA reads

Model: `parent`, `ambientocclusion`, `renderlayer` (BTA only), `display`, `textures`, `elements`, `particles` (BTA only: side to face index).
Element: `from`, `to`, `rotation` (`origin`, `axis`, `angle` any value, `rescale`), `shade`, `light_emission`, `faces`.
Face: `uv`, `texture`, `rotation`, `cullface`, `tintindex`.
Ignored: `loader`, `texture_size`, `gui_light`, `render_type`, `groups`.

### StaticBlockModel

```java
boolean renderStandalone(BlockModelGeneric<?> source, TessellatorGeneral t, double x, double y, double z, int meta, byte lightIndex, BlockColor color);
boolean renderAttached(BlockModelGeneric<?> source, TessellatorGeneral t, WorldSource world, TilePosc pos,
                       int rotX, int rotY, int rotZ,          // quarter turns
                       double xOff, double yOff, double zOff,
                       boolean uvlock, boolean cullFaces, IconCoordinate overrideTexture);
IconCoordinate getOverlay();
IconCoordinate getParticle(Side side);
int particleColorIndex(Side side);
int renderLayer();
DisplayPos getItemDisplayPos(String id);
```

`BlockModelGeneric.cullSide(world, pos, Direction)` decides face culling; `forceCullSelf` culls against the same block.

## Low-level API (from Betafront, verified)

| Thing | 8.0.1 name |
| --- | --- |
| Block render | `BlockModel#render(TessellatorGeneral, WorldSource, TilePosc)` |
| Item render | `renderStandalone(TessellatorGeneral, int meta, byte lightIndex)`, offset with `tessellator.offsetTranslation(±0.5, ...)` |
| Tessellator | `setLightmapCoord1i`, `setShade1i` (AO factor only), `setColorOpaque3f`, `setColor1i`, `setNormal`, `addVertexWithUV` |
| Light | `block.getLightIndex(world, TilePosc)`: byte `sky << 4 \| block`; `LightIndexHelper.lightIndex2i`, `skyLightFromIndex`, `blockLightFromIndex`, `setBlockLight`, `avg` |
| Smooth lighting | `LightingCache`: `setupCache(block, world, pos)`, `getLightIndex/getBrightness/getOpacity(dx,dy,dz)`. On only when `GameSettings.AMBIENT_OCCLUSION.value` and `block.emission == 0` |
| Sides | `Side.BOTTOM, TOP, NORTH...`; `Direction` with `rotateX/Y/Z(quarterTurns)` |
| Textures | `TextureRegistry.getTexture("ns:block/name")` returns `IconCoordinate`; **throws** for an id with no atlas folder; `getSubIconU/V` take 0..1. `TextureRegistry.reloadGeneration` changes on atlas rebuild |

## Lighting rules learned the hard way

- Never bake world brightness into vertex colour or shade: pass the light index with `setLightmapCoord1i`, or day/night freezes in the chunk.
- Directional shading comes from the normal. Unshaded faces: send an upward normal.
- Faces flat on the block edge take the neighbour's light; inner faces the block's own.

## For feature agents

You normally only need: implement `StaticBlockModel` (OBJ), or extend `BlockModelGeneric` and call `renderAttached` with rotations (blockstates). Anything else, ask Platform.
