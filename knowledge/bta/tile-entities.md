# Tile entities and geo models (BTA 8.0.1)

`net.minecraft.client.render.tileentity.TileEntityRenderer<T>`:

- `abstract void doRender(TessellatorGeneral, T tileEntity, double x, double y, double z, float partialTick)`
- `void tick()`, `void init(Class<? extends T>, TileEntityRenderDispatcher)`, `void onWorldChanged(World)`
- `final TileEntityRenderer<T> setModel(String localId, String modelId, double inflation)`: attaches a geo model; get it back with `getModel(localId)` (used by the sign renderer: "post", "wall")
- `boolean isVisible(T, ICamera, float partialTick)`

Geo models: `org.useless.dragonfly.data.entity.mojang.EntityGeometryMojangData` (Bedrock `.geo.json`), drawn by `org.useless.dragonfly.models.entity.mojang.StaticEntityModelMojang`; `models.entity.BoneTransform` exists. Vanilla geo files are listed in `assets/minecraft/models/entity/models.json`.

No animation runtime in 8.0.1: `pig.animation.json` and `wolf.animation.json` ship but nothing reads them.

Registration: `ModelHelper.setTileEntityModel(Class, renderer)` or `TILE_ENTITY_RENDERER_RELOAD`.
