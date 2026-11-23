# Drawing a tile entity through the terrain shader

Why: the ITEM shader has no shade input, so TEs drawn with it get no ambient occlusion ([tile-entities.md](tile-entities.md), "Lighting under `Shaders.ITEM`"). The terrain shader reads it. Names below were read from the BTA 8.0.1 jar and LWJGL 3.3.3 on Dave's Mac (2026-11-23). Used by `tileentity/TileEntityMesh`; **not yet seen in game** when written.

## TessellatorTerrainImpl (`net.minecraft.client.render.tessellator`)
- `TessellatorTerrainImpl(int bufferSize)`: bytes; the buffer grows itself.
- `startDrawing(DrawMode)` resets `data.vertexCount` and clears the buffer, but throws "Already drawing!" while the public field `drawing` is true. Sets light 15/15, white, uv 0, normal up; shade starts at 255.
- Per vertex: `setColor1i(int)`, `setNormal(float, float, float)` (stored as bytes × 127), `setShade1i(int)`, `setLightmapCoord1i(int)`, `setTextureUV(double, double)`, `addVertex(double, double, double)`. `setTranslation` / `offsetTranslation` exist.
- Quads are split into triangles inside `addVertex` (6 vertices per quad, 28 bytes each, so 168 bytes per quad).
- **`draw()` and `record(int, int)` throw `UnsupportedOperationException`.** BTA's chunk code uploads `data` into its shared `VertexBuffer` arena itself, then sets `drawing = false`. We do the same.
- `public final TerrainVertexData data`: `vertexCount`, `getBuffer()` (position at the end after filling: `flip()` before uploading), `config`.
- `TessellatorTerrainImpl.TerrainVertexConfig`: public static, no-arg constructor. `enable(int vao, int vbo)` binds both and sets the five attribute pointers (stride 28). Also `disable()`, `getVertexSize()`, `drawMode()` (TRIANGLES).
- Layout: loc0 position 3×float; loc1 colour 3×ubyte normalised (written B, G, R); loc2 normal 3×byte normalised; loc3 uv 2×float; loc4 `aLightShade` 2×ubyte (shade, light index).

## Buffers and drawing
- LWJGL 3.3.3; BTA calls GL through `org.lwjgl.opengl.GL41`: `glGenVertexArrays()`, `glGenBuffers()`, `glBindVertexArray(int)`, `glBindBuffer(int, int)`, `glBufferData(int, ByteBuffer, int)`, `glBufferSubData(int, long, ByteBuffer)`, `glDeleteBuffers(int)`, `glDeleteVertexArrays(int)`. Constants as BTA writes them: 34962 `GL_ARRAY_BUFFER`, 35044 `GL_STATIC_DRAW`, 35040 `GL_STREAM_DRAW`.
- `net.minecraft.client.render.renderer.DrawMode` enum (TRIANGLES, QUADS, ...; `public final int cap`).
- `RenderBuffer(int vao, int vbo, int offset, int count, DrawMode)`: public record, `delete()` frees both.
- `GLRenderer.render(int vao, int vbo, int offset, int count, DrawMode)` (or `render(RenderBuffer)`): binds, `currentShader.bind()`, `globalSetUniforms(currentShader)`, `glDrawArrays`, unbinds. `setShader(Shaders.TERRAIN)` first.
- `globalSetUniforms` sets `model`, `texture`, `uLightMapStrength`, `uAlphaTest`, `uColor`, fog, `normalTransform` and the lights. TEs draw inside the same `Lighting.enableLight()` window as terrain, so uniforms match.
- Terrain itself: `TerrainRendererMultiDraw.renderSolidTerrain` enables its config on the arena's VAO, then per region `pushFrame`, `modelM4f().translate(regionX - camX, -camY, regionZ - camZ)`, `globalSetUniforms(Shaders.TERRAIN)`, multi-draw, `popFrame`.
- `net.minecraft.client.render.terrain.VertexBuffer(long)` is the shared chunk arena with no public delete: not for per-TE use.

## Freeing
GL objects must be deleted on the render thread, so a `WeakHashMap` can't free them. Hooks:
- `TileEntityRenderer.tick()`: once per client tick, every renderer.
- `TileEntityRenderer.onWorldChanged(World)`, from `TileEntityRenderDispatcher.onWorldChanged`.
- `TileEntityRenderDispatcher.reload()` replaces every renderer and drops the old ones; HalpLibe's `ClientEvents.TILE_ENTITY_RENDERER_RELOAD` fires after it.
- `TileEntity.isInvalid()`. There is no chunk-unload callback for TEs.

What we do: a static set of meshes holding buffers; `sweep()` from `tick()` (at most once a second) frees meshes not drawn for 2 s, which covers removed and unloaded TEs and replaced renderers; `onWorldChanged` frees all. In the dev client a log line gives the count whenever it changes.

## Chunk AO, for reference
`StaticBlockModelMojang.renderAttached` with a static `LightingCache`: `setupCache(Block<?>, WorldSource, int x, int y, int z)` (or `TilePosc`), then `getOpacity`, `getLightIndex` and `getBrightness(dx, dy, dz)` with offsets −1..1. Corner light is `LightIndexHelper.avg` of 4 samples, brightness the average of the same 4, interpolated over the face and written as `setShade1i((int) (shade * 255))`. `obj/ObjSmoothLight` reproduces this.

## Batching (2026-11-23)
Measured on Dave's Mac (Intel Iris 640, macOS GL) with 57 TEs: ~85–130 µs per `GLRenderer.render` call, 300–425 ms per second in total. That call does `glBindVertexArray`, `glBindBuffer`, `currentShader.bind()`, `globalSetUniforms` (binds again; Matrices/Fog UBOs only when dirty; ~20 uniforms by name every call, incl. `world.getCelestialAngle`), `glDrawArrays`, then unbinds. `pushFrame` copies state and pushes 4 matrices (no GL); `popFrame` restores state and may mark the fog UBO dirty.
- Order in `WorldRenderer.renderWorld(float, long)`: `Lighting.enableLight()`, `renderSolidTerrain`, `RenderGlobal.renderEntities(ICamera, float)` (entities, then the TE loop over `renderableTileEntities` → `TileEntityRenderDispatcher.renderTileEntity`), `Lighting.disable()`, block breaking, selection box, translucent terrain.
- HalpLibe 6.2.0 has no render events, so we flush from a mixin at RETURN of `public void net.minecraft.client.render.RenderGlobal.renderEntities(ICamera, float)` (`mixin/RenderGlobalMixin`). HalpLibe mixes into `TileEntityRenderDispatcher` (recovery), not RenderGlobal.
- `tileentity/TileEntityBatch.flush()`: `worldAtlas.bind()`, `setShader(Shaders.TERRAIN)`, blend, `globalSetUniforms(Shaders.TERRAIN)` once; per mesh `Shaders.TERRAIN.uniformMat4f("model", m)` (`m` = `modelM4f()` at doRender time, translated to x, y, z), `GL41.glBindVertexArray(vao)`, `glDrawArrays(GL_TRIANGLES, 0, count)`; then `glBindVertexArray(0)`, `Shaders.TERRAIN.unbind()`.
- `Shader` (`net.minecraft.client.render.shader`): `bind()`, `unbind()`, `getUniform(String)` (cached location), `uniformMat4f(String, Matrix4fc)`, `uniformFloat`, `uniformInt`, `uniformVec*`. `GLRenderer.modelM4f()` returns `org.joml.Matrix4f`; `globalSetUniforms(Shader)` is public static.
- Mixin config: no refmap needed (the game runs with named classes; HalpLibe's own config has none). Format copied from `halplibe.mixins.json`.
- `TileEntityRendererSign$BufferedTextMeshRenderer` caches one VAO per sign text and draws each immediately (no cross-sign batch); it frees entries unused for 120 s from `tick()`.
