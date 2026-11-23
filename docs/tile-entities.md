# Tile entity models

A tile entity renderer draws its block every frame instead of once per chunk build, so parts can move: a chest lid, a door, a fan. `ModelTileEntityRenderer` draws a Blockbench model for a tile entity and moves its parts from your code.

## 1. Make the model in Blockbench

Use the "Java Block/Item" format and put each moving part in a **group** (folder) in the outliner. Set the group's **pivot** where it turns: for a chest lid, on the hinge at the back top edge. Groups can be nested; a child follows its parent. Elements outside any group never move.

Export it as usual to `assets/<modid>/models/block/<name>.json`. Blockbench writes the groups into the file (`"groups"`), which is where we read the parts and pivots from. Give it a `display` block too, for the item (see [model-json.md](model-json.md#block-items)).

## 2. The tile entity and block (common side)

```java
public class ChestTileEntity extends TileEntity {
    public float lid, prevLid;          // 0 closed, 1 open

    public ChestTileEntity() {}         // needs a public no-argument constructor

    @Override
    public void tick() {                // every game tick
        prevLid = lid;
        boolean open = (getBlockMeta() & 4) != 0;
        lid = open ? Math.min(1f, lid + 0.1f) : Math.max(0f, lid - 0.1f);
    }

    @Override public void readAdditionalData(CompoundTag tag) {}
    @Override public void writeAdditionalData(CompoundTag tag) {}
}
```

Register the class and give the block a tile entity:

```java
TileEntityDispatcher.addMapping(ChestTileEntity.class, NamespaceID.fromPool(MOD_ID, "chest"));
chest = new BlockBuilder(MOD_ID)
        .setTileEntity(ChestTileEntity::new)
        .build("chest", 1234, ChestLogic::new);
```

The `NamespaceID` is saved in the world, so don't change it later. Keep lasting state (open or closed) in metadata or in `writeAdditionalData`, and only the animation in fields.

## 3. The renderer (client)

```java
ClientEvents.TILE_ENTITY_RENDERER_RELOAD.listen(Key.of(MOD_ID), dispatcher ->
        dispatcher.assignRenderer(ChestTileEntity.class, new ModelTileEntityRenderer<ChestTileEntity>(
                "mymod:block/chest",
                (chest, partialTick) -> {
                    float t = chest.prevLid + (chest.lid - chest.prevLid) * partialTick;
                    return Pose.REST.with("lid", PartTransform.rotation(t * 90f, 0f, 0f));
                },
                chest -> -(chest.getBlockMeta() & 3))));   // optional: quarter turns about Y
```

- The **poser** returns a `Pose` for one tile entity at one moment: a `PartTransform` per group name. Groups it doesn't name stay as modelled.
- `PartTransform` holds a rotation in degrees (X, then Y, then Z, about the group's pivot, as Blockbench shows it), a translation in pixels and a scale: `PartTransform.rotation(x, y, z)`, `PartTransform.translation(x, y, z)`, `.withScale(x, y, z)`, `PartTransform.lerp(a, b, t)`.
- `partialTick` is how far the frame is between two ticks. Lerp between last tick's value and this tick's for smooth motion.
- The **facing** function (optional) turns the whole model by quarter turns about the vertical axis. One turn takes east to north.
- The renderer is made new on every texture pack reload, so the model follows the packs.

## 4. Hide the chunk model

The block's normal model would draw a second, still copy. Keep the model for the item, but draw nothing in the world:

```java
ClientEvents.BLOCK_MODEL_RELOAD.listen(Key.of(MOD_ID), dispatcher ->
        dispatcher.addDispatch(chest, new BlockModelGeneric<ChestLogic>(chest, Models.load("mymod:block/chest")) {
            @Override
            public boolean render(TessellatorGeneral tess, WorldSource world, TilePosc pos) {
                return false;
            }

            @Override
            public boolean renderAttached(TessellatorGeneral tess, WorldSource world, TilePosc pos,
                                          boolean noCulling, IconCoordinate overrideTexture) {
                return false;
            }
        }));
```

The block's `BlockLogic` should return `false` from `isSolidRender()` and `isCubeShaped()`.

## Lighting

Every face takes the light of the tile entity's own block and is shaded by its direction. There's no smooth lighting or culling against neighbours for tile entity models.

The full working version is the Test Chest: `bta/src/main/java/io/github/tryrodave/jsonextended/tileentity/example/TestChest.java`. To move parts from an animation file instead of code, see [animations.md](animations.md).
