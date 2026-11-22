# Getting started

## Where files go

Everything goes under your mod's `assets/<modid>/` folder, the same layout as vanilla resource packs:

```
assets/mymod/blockstates/lamp.json              blockstates
assets/mymod/models/block/lamp.json             block models (Blockbench "Java Block" exports)
assets/mymod/models/block/lamp.obj              OBJ files, next to their model JSON
assets/mymod/models/item/ruby.json              item models
assets/mymod/textures/block/lamp.png            block textures
assets/mymod/textures/item/ruby.png             item textures
assets/mymod/animations/chest.animation.json    Blockbench animations
assets/mymod/models/entity/models.json          geo mob models (BTA's own system, see animations.md)
```

Ids are written `namespace:path`, without the folder or extension, like vanilla: the block model above is `mymod:block/lamp`, the blockstate `mymod:lamp`, the texture `mymod:block/lamp`.

Texture packs can replace any of these files by shipping the same path. Everything is read again when the player changes packs or presses F3+R.

## Registering models

BTA rebuilds its model dispatchers on every texture pack change, so register in HalpLibe's reload events, from your client entrypoint. They fire at startup and on every reload.

```java
import io.github.tryrodave.jsonextended.blockstate.Blockstates;
import io.github.tryrodave.jsonextended.item.JsonItems;
import turniplabs.halplibe.event.defs.ClientEvents;
import turniplabs.halplibe.util.dependency.Key;

public final class MyModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientEvents.BLOCK_MODEL_RELOAD.listen(Key.of(MyMod.MOD_ID), dispatcher -> {
            dispatcher.addDispatch(MyBlocks.LAMP, Blockstates.model(MyBlocks.LAMP, "mymod:lamp"));
        });
        ClientEvents.ITEM_MODEL_RELOAD.listen(Key.of(MyMod.MOD_ID), dispatcher -> {
            dispatcher.addDispatch(JsonItems.model(MyItems.RUBY, "mymod:item/ruby"));
        });
    }
}
```

- `Blockstates.model(block, "mymod:lamp")` reads `blockstates/lamp.json` and every model it names. See [blockstates.md](blockstates.md).
- `JsonItems.model(item, "mymod:item/ruby")` reads `models/item/ruby.json`. See [item-models.md](item-models.md).
- A block with a single model and no blockstate file: `new BlockModelGeneric<>(block, Models.load("mymod:block/lamp"))`. `Models.load` also handles OBJ models and gives readable errors.

## Block logic

Model blocks are usually not full cubes. Return `false` from your `BlockLogic`'s `isSolidRender()` and `isCubeShaped()`, or the faces of neighbouring blocks get hidden behind it.

## When something is wrong

Every file is checked when it loads. A problem logs one line naming the file and the key, like `mymod:block/lamp: elements[0].faces.up.texture: every face needs a texture`, once per game session. A file that can't be read draws BTA's missing model or texture instead of crashing the reload.
