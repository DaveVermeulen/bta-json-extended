# How BTA 8.0.1 draws items

Read from the 8.0.1 jar with Vineflower (see [reading-bta-code.md](reading-bta-code.md)); signatures checked with `javap`. Short API list: [items.md](items.md), full signatures: [api/items.txt](api/items.txt).

## Class layout

- `ItemModel` (abstract) holds the default `renderGui`, `renderItemEntity` and `renderFirstPerson`. `ItemModelStandard` does **not** override them.
- `ItemModelStandard extends ItemModel`: one icon, a `Map<String, DisplayPos>` of poses, `render`, the flat and extruded icon drawing, and the count/durability overlay.
- `ItemModelBlock extends ItemModelStandard`: draws the block's model when `blockModel.shouldItemRender3d()`, and otherwise falls back to `ItemModelStandard`.

## `render(...)`: the one entry point

```
void render(TessellatorGeneral tessellator, Entity holder, ItemStack stack,
            String displayPosId, boolean items3d, int clusterSize,
            byte lightIndex, float partialTick, boolean leftHanded)
```

| Param | Meaning |
| --- | --- |
| `holder` | Entity holding it; `null` for ground items and the GUI default. Passed to `getIcon`. |
| `displayPosId` | Which pose to use: `"gui"`, `"ground"`, `"thirdperson_righthand"`... (constants on `DisplayPos`) |
| `items3d` | Draw the icon as a 3D extruded sprite (true) or a flat quad (false). Hands and statues always pass true; ground items pass the `ITEMS_3D` setting; GUI passes false. |
| `clusterSize` | How many copies to draw (stack count on the ground: 1 to 4). Every other caller passes 1. |
| `lightIndex` | Packed block+sky light (`LightIndexHelper.lightIndex2i(block, sky)`), written per vertex with `setLightmapCoord1i`. |
| `partialTick` | Frame interpolation; `ItemModelStandard` only passes it on to `renderSingle`. |
| `leftHanded` | Mirrors the pose: translate X, rotate Y and rotate Z are negated. Rotate X is left alone. |

`ItemModelStandard.render` steps:
1. `random.setSeed(187)`, so cluster jitter is the same every frame.
2. `DisplayPos p = getDisplayPos(displayPosId)`.
3. On `GLRenderer.modelM4f()`, in this order: `translate(±tx, ty, tz)`, `rotateX(rx)`, `rotateY(±ry)`, `rotateZ(±rz)`, `scale(sx, sy, sz)`. Angles are in degrees, converted to radians. There is no pivot: unlike modern MC's centre-of-block pivot, it rotates about the origin.
4. Cluster:
   - 3D: copy `i` is moved `0.1 * i` along Z.
   - Flat: each copy after the first gets random X/Y/Z jitter of up to ±0.2, divided by the scale.
   - Each copy calls `renderSingle(..., items3d, lightIndex, getColor(stack), partialTick, mirrorX = false)`.
   - The model matrix is not pushed. Each copy's translation is undone by translating back, and the caller owns push/pop.

`renderSingle` passes `getIcon(holder, stack)` to `renderCoordinate`. With `setFullBright()` it uses `lightIndex2i(15, 15)` instead of the light it was given.

### `renderCoordinate`: what gets drawn

- Binds `icon.parentAtlas`, so it uses whichever atlas the icon came from (item icons live in the world atlas, see below). It does not set a shader.
- The sprite fills a 1×1 square centred on the origin, X and Y from -0.5 to 0.5, with V running top to bottom.
- **Flat** (`items3d = false`): one quad at z=0, drawn twice with opposite winding so it shows from both sides. Both use normal (0, 1, 0).
- **Extruded** (`items3d = true`): 1/16 thick (z = ±0.03125).
  - Front face at z = -1/32 with normal -Z; back face at +1/32 with normal +Z.
  - For every pixel row, a top/bottom strip with normals ±Y; for every pixel column, a left/right strip with normals ±X. Each strip's UV covers that single row or column (`getSubIconU/V`).
  - Strips are drawn for transparent pixels too: there is no alpha test on the geometry, so transparent pixels are left to the shader.
- Colour comes from `getColor(stack)`, which calls `getColorFromMeta(meta)` (-1, meaning white, by default). One colour covers the whole quad.
- `mirrorX` swaps uMin and uMax. `ItemModelStandard.render` always passes false; `ItemModelBlock` passes `leftHanded`.

## Default wrappers on `ItemModel`

- **`renderGui(tess, holder, stack, x, y, light, pt)`**:
  - Push, shader `Shaders.ITEM`, **turns lighting off** (`globalSetLightEnabled(false)`), alpha blend, cull face on.
  - `translate(x + 8, y + 8, 0)`, then `scale(16, -16, 1)`: the 1×1 sprite becomes a 16-pixel slot, with Y flipped for screen space.
  - Calls `render(..., "gui", items3d = false, 1, light, pt, false)`. Turns lighting back on and pops.
- **`renderItemEntity(tess, stack, items3d, clusterSize, ticks, yaw, light, pt)`**:
  - Push, `Shaders.ITEM`, blend.
  - 3D: `rotateY(yaw)`, so the item spins. Flat: `rotateY(180 - viewLerpYaw)`, a billboard facing the camera.
  - Calls `render(..., "ground", items3d, clusterSize, light, pt, false)`, then pops.
  - Its caller, `EntityRendererItem`, has already translated to the entity plus a bobbing offset. It sets the cluster to 1/2/3/4 copies for stack sizes 1, >1, >5 and >20, and uses yaw = `toDegrees((age + pt) / 20 + bobOffset)`.
- **`renderFirstPerson(tess, itemRenderer, player, stack, light, pt)`**:
  - Push, `Shaders.ITEM`, blend, cull face on.
  - Applies the swing and equip animation: translate by the swing offsets, then `translate(±0.5625, -0.515625 - (1 - equip) * 0.6, -0.725)`, then rotate Y 45 / swing / Y -45.
  - Left-handed players: the whole animation is wrapped in `scale(-1, 1, 1)` before and after.
  - Calls `render(..., "firstperson_righthand" | "firstperson_lefthand", true, 1, light, pt, leftHanded)`.

## `ItemModelBlock`

- Constructor: `super(itemBlock, false)`, which skips the item texture lookup. Takes `BlockModelDispatcher.getDispatch(block)`; the icon is `blockModel.getOverlayTexture(0)`.
- `getIcon` returns `getOverlayTexture(meta)`, or `BLOCK_TEXTURE_UNASSIGNED` when that is null.
- `getDisplayPos(id)`: if the block renders 3D, returns `blockModel.getItemDisplayPos(id)` (the block model JSON `display`); otherwise uses the item defaults.
- If `!shouldItemRender3d()`, every method falls back to `ItemModelStandard`, which draws the block's overlay texture as a sprite.
- **render (3D)**: same steps as `ItemModelStandard`, but:
  - Sets `Shaders.ITEM` itself.
  - Always uses the flat-style random jitter for clusters, even with `items3d`.
  - `renderSingle` binds `TextureRegistry.worldAtlas` and calls `blockModel.renderStandalone(tess, meta, lightIndex)`, so the block model draws itself. I didn't check whether `renderStandalone` centres the cube on the origin; read it before tuning `display` translations.
  - Colour comes from `BlockColorDispatcher...getFallbackColor(meta, 0)` (grass and leaves tint).
- **renderGui (3D)**:
  - Push, `Shaders.ITEM`, blend, cull face on, **lighting left on**.
  - `translate(x + 8, y + 8, 8)`, then `scale(16, -16, 16)`: depth is kept, unlike the flat GUI.
  - `render(..., "gui", false, 1, ...)`, then pop.
- **renderItemEntity (3D)**:
  - **No push/pop**, blend on.
  - `rotateY(yaw)` whether or not items are 3D: a block never billboards.
  - `render(..., "ground", items3d, clusterSize, ...)`, then blend off.
- `renderFirstPerson` is inherited from `ItemModel`.

## Display ids actually asked for (8.0.1)

| Id | Caller |
| --- | --- |
| `gui` | `ItemModel.renderGui` / `ItemModelBlock.renderGui` |
| `ground` | `renderItemEntity`; also `MobRendererWolf`, for the item in a wolf's mouth |
| `firstperson_righthand` / `_lefthand` | `ItemModel.renderFirstPerson` |
| `thirdperson_righthand` / `_lefthand` | `MobRendererBiped`, after `translateToBone("rightItem"/"leftItem")`, `scale(∓1, 1, ±1)` and `scale(15.99)`; `TileEntityRendererStatue` (right hand only) |
| `head` | `MobRendererBipedArmored`, for an item in the helmet slot |
| `fixed`, `jar`, `none` | Defined on `DisplayPos`, but no item caller found (`jar` belongs to the jar block model) |

## `DisplayPos` (`org.useless.dragonfly`)

- `public final float tx, ty, tz, rx, ry, rz, sx, sy, sz`; constructor `DisplayPos(tx, ty, tz, rx, ry, rz, sx, sy, sz)`, in that order.
- Units: translation in blocks, rotation in degrees.
- `DEFAULT_DISPLAY_POS` = identity (0 translation, 0 rotation, scale 1). It is used when an id isn't in the map.
- `DisplayPos.Serializer` reads modern JSON `display` entries:
  - `rotation`, `translation` and `scale` are arrays of 3; missing ones default to 0, 0 and 1.
  - **translation is multiplied by 1/16**, because JSON uses pixels.
  - It doesn't serialize (it throws).
  - It doesn't clamp, unlike vanilla's ±80 translation and ≤4 scale.

## `ItemModelStandard` defaults

Written as (translation)(rotation)(scale):

| Id | Value |
| --- | --- |
| `ground` | (0, 0.125, 0)(0, 0, 0)(0.5) |
| `head` | (0, 0.875, 0)(0, 0, 0)(1) |
| `thirdperson_righthand` | (0, 0.1875, 0.0625)(0, 0, 0)(0.55) |
| `thirdperson_lefthand` | (0, 0.1875, 0.0625)(0, 0, 0)(0.55) |
| `firstperson_righthand` | (0.070625, 0.2, 0.070625)(0, -90, 25)(0.68) |
| `firstperson_lefthand` | (0.070625, 0.2, 0.070625)(0, 90, -25)(0.68) |
| `fixed` | (0, 0, 0)(0, 180, 0)(1) |

- `gui` isn't in the map, so it gets identity.
- The constructor `ItemModelStandard(item)` (= `(item, true)`) sets the icon to `TextureRegistry.getTexture(item.namespaceID)`.
- `setDisplayPos(id, pos)` and `setIcon(...)` both return `this`.

`ItemModelDispatcher` adds handheld (tool) poses:
- `HANDHELD_FIRST_PERSON_*`: same as the defaults.
- `HANDHELD_THIRD_PERSON_RIGHT_HAND`: (0, 0.25, 0.03125)(0, -90, 55)(0.85); the left hand mirrors Y and Z.
- `HANDHELD_ROD_*`: fishing rods and similar.

## Which atlas item textures live in

- `TextureRegistry.worldAtlas` stitches three folders: `textures/block`, `textures/item` and `textures/particle`. It also has `cmask` (colour mask) and `emiss` (emissive) layers.
- Other atlases: `art` (`textures/art`), `gui` (`textures/gui/sprites`), `sign` (`textures/gui/sign`).
- `TextureRegistry.init()` stitches every registered namespace into every atlas.
- HalpLibe `TextureHelper.initializeAllFiles(modId, atlas, searchSubDirs)` only checks that the mod exists, then calls `TextureRegistry.initializeAllFiles` → `atlas.initializeAll(modId, subDirs)`. It stitches every folder of the atlas you pass. For `worldAtlas` that means **block and item (and particle)**.
- So `modid:item/foo` and `modid:block/foo` both resolve in the same atlas, and an item model can use block textures directly.

## Takeaways for JSON item models

- One `ItemModelStandard` subclass that overrides `getDisplayPos` (or calls `setDisplayPos` from JSON `display` via `DisplayPos.Serializer`) covers `item/generated`.
  - Its 3D extrusion already looks like vanilla's generated model, but its pivot and units differ: our JSON defaults must be tuned against BTA's built-in defaults, not copied from vanilla.
- An element-based item model (`parent: block/...` or custom cuboids) can follow `ItemModelBlock`'s path:
  - Bind `worldAtlas`, set `Shaders.ITEM`, apply the `DisplayPos`, and draw the cuboids the same way `renderStandalone` places a block.
  - Copy its GUI matrix (z scale 16, lighting on) for a 3D inventory icon.
- Any `display` id we read but BTA never asks for (`fixed`, `jar`) only matters if we add callers.
