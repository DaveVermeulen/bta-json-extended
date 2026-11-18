# Writing a `StaticBlockModel` (BTA 8.0.1)

How BTA's own `org.useless.dragonfly.models.block.mojang.StaticBlockModelMojang` implements the interface, read from the 8.0.1 jar. Logic is paraphrased; signatures were checked with `javap`. Use this as the reference for the OBJ model (and any non-JSON model).

## The interface

```
interface StaticBlockModel {
  boolean renderStandalone(BlockModelGeneric<? extends BlockLogic> source, TessellatorGeneral tess,
                           double x, double y, double z, int meta, byte lightIndex, BlockColor color);
  boolean renderAttached(BlockModelGeneric<? extends BlockLogic> source, TessellatorGeneral tess,
                         WorldSource world, TilePosc pos, int rotX, int rotY, int rotZ,
                         double xOff, double yOff, double zOff, boolean uvlock, boolean cullFaces,
                         @Nullable IconCoordinate overrideTexture);
  @Nullable IconCoordinate getOverlay();
  @Nullable IconCoordinate getParticle(Side side);
  int particleColorIndex(Side side);
  int renderLayer();
  DisplayPos getItemDisplayPos(String id);
}
```

`BlockModelGeneric.render(tess, world, pos)` calls `staticModel.renderAttached(this, tess, world, pos, 0, 0, 0, 0, 0, 0, false, true, null)`:
- `renderNoCulling` passes `cullFaces = false`.
- `renderWithOverrideTexture` passes a texture; `RenderGlobal.drawBlockBreaking` uses it for the **breaking cracks**.
- Our blockstate code is what passes non-zero rotations and offsets.

## `renderAttached` (the world/chunk path)

**Tessellator:** it does **not** call `startDrawingQuads()` or `draw()`. The chunk builder (`ChunkRendererMultiDraw`) has already started quads and translated to the render region. Just add vertices.

**Return value:** `true` if at least one face was emitted. A model that draws nothing returns `false`.

### Per face, in order

1. **Cull:**
   - If `cullFaces` and the face has a `cullface` direction, rotate it: `dir.rotateX(rotX).rotateY(rotY).rotateZ(rotZ)`.
   - Skip the face if a per-call 6-bit cache already says that side is culled. Otherwise skip it if `source.cullSide(world, pos, dir)` is true, and set the bit.
   - `BlockModelGeneric.cullSide` = the neighbour at `pos + dir` is an opaque cube. With `forceCullSelf(true)`, the same block type also culls.
2. **Texture:** `overrideTexture` if non-null (breaking cracks), else the face's resolved icon.
3. **Facing:** `element.directions[face]` rotated the same way. The face's "up" and "left" vectors are also rotated by quarter turns (radians `rot * PI/2`) and rounded to ints; they choose the light samples.
4. **Tint:**
   - If `tintIndex >= 0`: `BlockColor c = BlockColorDispatcher.getInstance().getDispatch(source.block)` (fetched once per call), then `c.getWorldColor(world, pos, tintIndex)` → RGB int → r, g, b in 0..1.
   - Otherwise white.
5. **Light** (see below), then **4 vertices**. For each:

```
tess.setColorOpaque3f(r, g, b);
tess.setLightmapCoord1i(lightIndex for this corner);   // packed block+sky light byte
tess.setShade1i((int)(shade * 255));                   // 0..255 brightness/AO
tess.addVertexWithUV(p.x + pos.x + xOff, p.y + pos.y + yOff, p.z + pos.z + zOff,
                     icon.getSubIconU(u), icon.getSubIconV(v));   // u, v in 0..1 within the icon
```

- Before the 4 vertices: `setNormal(rotated face normal)` if the element has `shade`, otherwise `setNormal(0, 1, 0)`.
- After them: `setNormal(0, 1, 0)` again.
- Positions are block-local 0..1, then shifted to world coordinates by `pos` plus the offset.

### Quarter-turn rotation

For each vertex: `p.sub(0.5).rotateX(rotX * PI/2).rotateY(rotY * PI/2).rotateZ(rotZ * PI/2).add(0.5)`.
- The order is **X, then Y, then Z**, around the **block centre**, using JOML's right-hand rule.
- So **`rotY = 1` turns east into north**: the east face centre (1, 0.5, 0.5) goes to (0.5, 0.5, 0), the north face centre, and the normal (1, 0, 0) goes to (0, 0, -1).
- `Direction.rotateY(1)` agrees: N→W→S→E→N. Similarly `rotateX(1)`: DOWN→NORTH→UP→SOUTH, and `rotateZ(1)`: DOWN→EAST→UP→WEST.
- **Watch out:** vanilla blockstate `"y": 90` turns north → **east** (clockwise from above). That is `rotY = -1` (or 3) here, not `+1`.
- **uvlock:** `rotateUV(uv, rotX, rotY, rotZ, originalDirection, out)` counter-rotates the 0..1 UV about (0.5, 0.5) per quarter turn, depending on the face's axis.

### Lighting

- `LIGHTING_CACHE` is a `static final LightingCache` on `StaticBlockModelMojang`.
- `setupCache(source.block, world, pos)` runs before each face's light is read. It provides a 3×3×3 neighbourhood, relative to the block:
  - `getLightIndex(dx, dy, dz)`: packed light byte;
  - `getBrightness(dx, dy, dz)`: float brightness;
  - `getOpacity(dx, dy, dz)`: opaque or not.
- **Full-cube face test:** `isFullCube` = the face sits within 1/16 of the block boundary (`depth <= 0.0625`) and the element has no rotation, or a multiple of 90°.
  - Full-cube faces sample the **neighbour** in the face direction.
  - Inset faces sample the **block itself** (0, 0, 0).
- **Light emission:** `minBrightness = element.light_emission / 15`. Brightness is clamped to at least that.
  - The light index's block-light nibble is raised to at least `light_emission` (`LightIndexHelper.setBlockLight`).
  - The block's own `emission` reaches us through `LightingCache` / world light, not through the model.
- **Flat** (`!useAO`): every corner gets the same light index and brightness, from the neighbour or the block as above.
- **Smooth** (`useAO`): needs all three of `ambientocclusion` in the model, `GameSettings.AMBIENT_OCCLUSION` on, and the element's `shade`.
  - Each corner's light index is `LightIndexHelper.avg(centre, side1, side2, corner)` of 4 samples.
  - Brightness is the average of the same 4 samples, the vanilla AO rule: a corner hidden by two opaque sides reuses a side sample.
  - Inset faces blend between neighbour and own samples by depth.
  - Finally the four corner values are bilinearly interpolated to the face's actual extent (top/bottom/left/right fractions), and that becomes `setShade1i`.
- **Minimal correct OBJ approach:** flat lighting. Use `getLightIndex` of the neighbour along the face normal (or the block itself when the face is inset), `getBrightness` → `setShade1i`, and `setLightmapCoord1i` per vertex. AO is optional.

## `renderStandalone` (items, GUI, ground)

- Called by `BlockModelGeneric.renderStandalone(tess, meta, light)` with `source = this`, `x = y = z = 0` and the dispatcher's `BlockColor`.
- **It calls `startDrawingQuads()` and `draw()` itself.**
- `source` and `x, y, z` are ignored. Every vertex is `pos - 0.5`, **centred on the origin**.
- Per element: `setLightmapCoord1i(lightIndex)`. Per face:
  - colour `color.getFallbackColor(meta, tintIndex)` when `tintIndex >= 0`, else -1;
  - normal = face normal if `shade`, else (0, 1, 0);
  - UV = `getSubIconU/V`.
- No culling, rotation, AO or `setShade1i`.
- Returns `false` (and draws nothing) when there are no elements.
- `color` is only touched for tinted faces.

## The small getters

| Method | Mojang returns | Non-JSON model should return |
| --- | --- | --- |
| `getParticle(Side)` | Texture `#particle_<side>`; the compiled model fills all six and `#overlay` from `#particle` | One icon for every side, or per-side icons. `null` breaks the particles. |
| `getOverlay()` | `#overlay`, which falls back to `#particle` | The same icon as the particle. `ItemModelBlock` uses it for the flat item icon. |
| `particleColorIndex(Side)` | The `particles` map value per side (default 0) | 0, or -1 for untinted |
| `renderLayer()` | `renderlayer` (default 0) | 0 opaque/cutout, 1 translucent (see [model-loading.md](model-loading.md)) |
| `getItemDisplayPos(id)` | The compiled `display` map, else `DisplayPos.DEFAULT_DISPLAY_POS` (identity) | Our own map. An empty map makes block items huge, so default to the `BlockModelStandard` block values below. |

Compiled `display` fills in missing left-hand poses from the right-hand ones:
- `firstperson_lefthand` = the right-hand pose with `ry + 180`;
- `thirdperson_lefthand` = an exact copy.

(`BlockModelMojangData` also copies them during the parent merge, without the +180.)

`BlockModelStandard` block defaults, in **blocks** (×16 for JSON pixels):
| Id | Translation | Rotation | Scale |
| --- | --- | --- | --- |
| `gui` | 0, 0, 0 | 30, 225, 0 | 0.625 |
| `ground` | 0, 0.1875, 0 | 0, 0, 0 | 0.25 |
| `fixed` | 0, 0, 0 | 0, 0, 0 | 0.5 |
| `thirdperson_righthand` / `_lefthand` | 0, 0.15625, 0 | 75, 45, 0 | 0.375 |
| `firstperson_righthand` | 0, 0, 0 | 0, 45, 0 | 0.4 |
| `firstperson_lefthand` | 0, 0, 0 | 0, 225, 0 | 0.4 |

## Tint colours

`net.minecraft.client.render.block.color.BlockColor` (abstract class):
- `int getWorldColor(WorldSource, TilePosc, int tintIndex)`: world path, biome-aware.
- `int getWorldColor(WorldSource, int x, int y, int z, int tintIndex)`: final convenience overload.
- `int getFallbackColor(int meta, int tintIndex)`: items and standalone.

Get it with `BlockColorDispatcher.getInstance().getDispatch(block)`; it's non-null for registered blocks. Colours are `0xRRGGBB`; `Color.redFromInt` etc. split them.
