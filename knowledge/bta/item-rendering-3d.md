# Drawing a 3D item model in BTA 8.0.1

Follow-up to [item-rendering.md](item-rendering.md). Read from the 8.0.1 jar with Vineflower; signatures checked with `javap`.

## GL helpers (exact signatures)

`net.minecraft.client.render.renderer.GLRenderer`, all static:

```
void pushFrame()                     // saves the model matrix and GL state
void popFrame()
org.joml.Matrix4f modelM4f()         // the live model matrix: mutate it directly
void setShader(net.minecraft.client.render.shader.Shader)
void globalSetLightEnabled(boolean)
boolean globalGetLightEnabled()
void enableState(net.minecraft.client.render.renderer.State)
void disableState(net.minecraft.client.render.renderer.State)
void setBlendFunc(BlendFactor src, BlendFactor dst)   // renderer.BlendFactor
byte getLightIndex()
```

- There is no separate push/pop for the matrix: `pushFrame`/`popFrame` is the only stack.
- Blend and cull are switched on with `enableState(State.BLEND)` and `enableState(State.CULL_FACE)`. `State` also has `DEPTH_TEST`, `POLYGON_OFFSET_FILL` and others.
- Shaders: `net.minecraft.client.render.renderer.Shaders` has `public static final Shader ITEM`, set with `GLRenderer.setShader(Shaders.ITEM)`.
  - `ItemModel`'s wrappers set it before calling `render`.
  - `ItemModelStandard.render` doesn't set it; `ItemModelBlock.render` does.

## Atlases and icons

- `TextureRegistry.worldAtlas` is a `public static AtlasStitcher` (`net.minecraft.client.render.texture.stitcher`).
- `AtlasStitcher` has `void bind()` and `void bind(String)`, plus `static Matrix4f textureM4f()`.
- `IconCoordinate.parentAtlas` is a `public final AtlasStitcher`, so `icon.parentAtlas.bind()` binds whichever atlas the icon came from.
- UVs inside an icon: `getSubIconU(double)` / `getSubIconV(double)` take 0..1 within the icon. `getIconUMin/UMax/VMin/VMax` give the icon's corners.

## How `ItemModelBlock` reaches the block model

`ItemModelBlock.renderSingle(...)` when the block renders 3D:
1. `TextureRegistry.worldAtlas.bind()`.
2. `blockModel.renderStandalone(tessellator, stack.getMetadata(), lightIndex)`.

There is no translate before it. The only transforms are the `DisplayPos` and cluster offsets applied in `render`, and whatever the caller set up. `ItemModelBlock.getColor(stack)` is computed, but the 3D branch never uses it: tint comes from the block model itself.

`BlockModel.renderStandalone(TessellatorGeneral, int meta, byte light)` (abstract) has two implementations:

- **`BlockModelGeneric`** (JSON models), which calls:
  `getModelFromData(meta).renderStandalone(this, tess, 0.0, 0.0, 0.0, meta, light, BlockColorDispatcher.getInstance().getDispatch(block))`
  - `getModelFromData` returns `staticModel` by default.
  - The `Generic*` subclasses pick the model from meta.
  - `BlockColor` comes from the dispatcher, so it is non-null for registered blocks.
- **`BlockModelStandard`** (the old cube blocks):
  - `tess.offsetTranslation(-0.5, -0.5, -0.5)`, then draws the 6 faces of `getBlockBoundsForItemRender()`, then `offsetTranslation(+0.5, +0.5, +0.5)`.
  - Tint is `getStandaloneTintColor(meta)`: the `BlockColor` fallback when `renderBlocks.useInventoryTint` is set, otherwise white.

So in both paths **the block ends up centred on the origin** (-0.5..0.5), not 0..1. `DisplayPos` translations are relative to the block's centre.

## `StaticBlockModelMojang.renderStandalone`

```
boolean renderStandalone(BlockModelGeneric<? extends BlockLogic> sourceModel,
    TessellatorGeneral tess, double x, double y, double z,
    int meta, byte lightIndex, BlockColor color)        // from StaticBlockModel
```

- Returns `false` and draws nothing when the model has no elements.
- **`sourceModel` and `x, y, z` are not used** in this implementation. It always draws at the origin, so `sourceModel` could be any value without breaking this class. The parameter is still `@NotNull` on the interface, though, and other `StaticBlockModel`s may use it.
- One `startDrawingQuads()` ... `draw()` for the whole model. Before each element it sets the lightmap to `lightIndex`.
- Per face:
  - **Colour:** if the face's `tintindex >= 0`, it uses `color.getFallbackColor(meta, tintindex)`; otherwise -1 (white).
  - **So `color` may be null only when no face has a tintindex**; a tinted face would throw a NullPointerException.
  - **Normal:** the face normal if the element has `shade`, otherwise (0, 1, 0).
  - **Vertex:** the precompiled vertex position minus 0.5 on each axis, which centres it like `BlockModelStandard`. UV is `coordinate.getSubIconU/V(uv)` of the face's resolved texture.
- No culling, no rotation by blockstate and no AO, unlike `renderAttached`.
- Element rotations are already baked into the compiled vertices.

## Recipe for our JSON item model (element-based)

- Inside `render(...)`:
  - Optionally `pushFrame()`.
  - `setShader(Shaders.ITEM)`.
  - Apply our `DisplayPos` on `modelM4f()` as `ItemModelBlock` does.
  - `TextureRegistry.worldAtlas.bind()`.
  - Draw the elements centred (pos - 0.5), with the tint from our own `BlockColor` or -1.
- For GUI, copy `ItemModelBlock.renderGui`: `translate(x + 8, y + 8, 8)`, `scale(16, -16, 16)`, lighting left on, cull face on.
- If we reuse `StaticBlockModelMojang` directly, pass any `BlockModelGeneric` (it's ignored), 0, 0, 0 for the position, and a non-null `BlockColor` whenever the model has tintindex faces.
