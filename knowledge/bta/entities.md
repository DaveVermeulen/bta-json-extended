# Custom mobs with geo models (BTA 8.0.1)

Read from the 8.0.1 jar (Vineflower) and HalpLibe 6.2.0; exact signatures are in [api/entities.txt](api/entities.txt). Bone posing and the bone matrix maths are in [../formats/animation.md](../formats/animation.md).

## 1. The mob class

- Copy **`MobCow`**, the simplest: `extends MobAnimal` (→ `MobPathfinder` → `Mob` → `Entity`). Its constructor:

```java
public TestMob(World world) {
    super(world);
    setTextureIdentifier("jsonextended", "test_mob");   // protected final on Mob
    setSize(0.9F, 1.3F);                                // hitbox width, height in blocks
}
```

- It needs a public `(World)` constructor; the factory calls it.
- Optional overrides (all of them on MobCow): `getLivingSound()`, `getHurtSound()`, `getDeathSound()`, `getSoundVolume()`, `isFavouriteItem(ItemStack)`. Plus `addAdditionalSaveData` / `readAdditionalSaveData(CompoundTag)` for saved fields; always call `super`.
- **Texture:** `setTextureIdentifier(ns, id)` sets `basePath = /assets/<ns>/textures/entity/<id>/`.
  - `getDefaultEntityTexture()` = `basePath + "0.png"`.
  - With the `MOB_VARIANTS` option on, `getEntityTexture()` picks a variant from `basePath + "variants.json"`, default `"0"`.
  - So ship `assets/jsonextended/textures/entity/test_mob/0.png`. It's a loose texture, not an atlas: geo UVs are in its pixels (`texturewidth`/`textureheight`).

## 2. Registering the mob (common side)

- `EntityDispatcher.getInstance().addMapping(Class<T>, NamespaceID, EntityFactory<T>)`. There's also an overload with a trailing guidebook translation key, which vanilla passes: `"guidebook.section.mob.pig.name"`.
  - Vanilla: `addMapping(MobPig.class, NamespaceID.fromPool("minecraft", "pig"), MobPig::new, "guidebook.section.mob.pig.name")`.
- `EntityFactory<T extends Entity>` is a functional interface: `T createEntity(World)`, so `TestMob::new` works.
- HalpLibe `EntityHelper.createEntity(Class, NamespaceID, EntityFactory)` calls the same `addMapping`, **but it's an instance method**, not static. That means `new EntityHelper().createEntity(...)`, so calling `EntityDispatcher` directly is simpler.
  - (`EntityHelper.addMapping` is static, but it's for *tile entities*.)
- **The id matters twice:** it's saved in the world, and it's the key the renderer uses to find its geo models (see 4).

## 3. Spawning without a spawn egg

BTA 8.0.1 has no spawn egg item. A dev item that spawns on right-click:

```java
@Override public boolean onUseOnBlock(ItemStack stack, World world, Player player, TilePosc pos,
                                      Side side, double xHit, double yHit) {
    if (!world.isClientSide) {
        TestMob mob = new TestMob(world);   // or EntityDispatcher.getInstance().createEntityInWorld(TestMob.class, world)
        mob.moveTo(pos.x() + 0.5, pos.y() + 1, pos.z() + 0.5, player.yRot, 0);   // x, y, z, yRot, xRot
        world.entityJoinedWorld(mob);
    }
    return true;
}
```

- `Item.onUseOnBlock(...)` is the overridable one. `onUseItemOnBlock` is `final` and calls it.
- `Entity.moveTo(double, double, double, float yRot, float xRot)`, `setPos(double, double, double)` and `absMoveTo(...)` all exist.
- `World.entityJoinedWorld(Entity)` returns `boolean`.
- A dev block works the same way from `BlockLogic.onInteracted(World, TilePosc, Player, Side, double, double)`.
- `TilePosc` has `int x()`, `y()`, `z()`.

## 4. Where geo models come from

- `EntityGeometryMojangData.Cache.reload(texturePackList)` runs on every `TexturePackList.refresh()`. For **every namespace in `Registries.NAMESPACES`** (mod ids included), in the default pack and each selected pack, it reads **`/assets/<ns>/models/entity/models.json`**:

```json
{
  "model_paths": ["/assets/jsonextended/models/entity/test_mob/test_mob.geo.json"],
  "renderers": {
    "jsonextended:test_mob": {
      "main": "geometry.jsonextended.test_mob",
      "outer": { "model": "geometry.jsonextended.test_mob", "inflation": 0.5 }
    }
  }
}
```

- `model_paths` are full resource paths. Each file can use the old format (`"geometry.x": {...}`) or the new one (`"minecraft:geometry": [{"description": {"identifier": ...}}]`).
- Models are cached by **geometry identifier**, a global key with no namespace. Prefix ours, e.g. `geometry.jsonextended.test_mob`, to avoid clashing with other mods.
- `renderers` maps an **entity id** to local model ids. The renderer's `getModel("main")` looks up `EntityDispatcher.entryForClass(appliedClass).namespaceID`, then this map.
  - An unknown id or local id gives the **fallback model**, a 16-pixel cube, with no log line for unmapped local ids.
  - An unknown geometry id logs `No model data for model '<id>'!`.
- Alternative without the manifest's `renderers`: `renderer.setModel(localId, "geometry.jsonextended.test_mob", inflation)` (public, final, returns the renderer). The geometry still has to be listed in `model_paths`.
- Vanilla pig: `"minecraft:pig": {"main": "geometry.pig", "saddle": {"model": "geometry.pig", "inflation": 0.5}}`.

## 5. The renderer (client)

- **Register:**
  - `ClientEvents.ENTITY_RENDERER_RELOAD.listen(Key.of(MOD_ID), d -> d.assignRenderer(TestMob.class, new TestMobRenderer(0.7F)))`.
  - `EntityRendererDispatcher.assignRenderer(Class<? extends T>, EntityRenderer<T>)`.
  - The event fires from HalpLibe's mixin at the tail of `EntityRendererDispatcher.reload()`.
  - The constructor argument is the shadow size: pig and cow use 0.7F.
- **Simplest base:** `MobRendererQuadruped<T>`, which cow uses as is. Pig extends it. For our own poses, extend `MobRenderer<T>` and implement the one abstract method:

```java
@Override protected StaticEntityModel getAndSetupModelForLayer(T entity, float brightness, float partialTick, int layer)
```

- Called once per layer, 0..`maxRenderLayer(entity)` (default 0), every frame. Return the model to draw, or null. The pig's layer 1 = saddle.
- Pattern (from `MobRendererQuadruped` / `MobRendererPig`):
  - `model = getModel("main")`, `model.resetBones()`;
  - then write `model.getTransform("head").rotX = ...` etc. (radians; see animation.md);
  - return `model`.
- **What `MobRenderer.render(...)` does around it:**
  - It **binds the mob texture** first (`loadEntityTexture` → `bindTexture(getDefaultEntityTexture() / getEntityTexture())`).
  - Blend on, cull off.
  - `preRenderTransform`: translate to the entity, `rotateY(-bodyYaw)`, `scale(1/15.99, 1/15.99, -1/15.99)` (pixels → blocks, Z flipped), the death tilt, and the baby scale for `AgedMob`.
  - Then per layer: `pushFrame`, your method, `model.render()`, `popFrame`.
  - Then the red hurt flash, `renderAdditional` (held items) and `renderSpecials` (name tag).
  - To use another texture on a layer, call `bindTexture(path)` inside your method (the pig does this for the saddle).
- **Inputs for poses**, protected helpers on `MobRenderer`, all taking `(T entity, float partialTick)`:

| Helper | Returns |
| --- | --- |
| `getLimbSwing` | `walkAnimPos - walkAnimSpeed * (1 - pt)`: walk cycle phase |
| `getLimbYaw` | lerp(`walkAnimSpeedO`, `walkAnimSpeed`), capped at 1: walk amount |
| `getLimbPitch` | `tickCount + pt`: an age timer, good as animation time / 20 for seconds |
| `getBodyYaw` | lerp(`yBodyRotO`, `yBodyRot`), radians |
| `getHeadYaw` | lerp(`yRotO`, `yRot`), radians. Subtract `getBodyYaw` for the head relative to the body. |
| `getHeadPitch` | lerp(`xRotO`, `xRot`), radians |

  - Vanilla legs: `leg0.rotX = cos(limbSwing * 0.6662) * 1.4 * limbYaw`; leg1 and leg2 add `PI`.
- **Phase 6 hook:**
  - Sample our animation at `getLimbPitch(entity, pt) / 20` seconds into a pose, write it into the `BoneTransform`s after `resetBones()`, then add head look and walk on top.
  - Keep per-entity animation state on the mob or in a map keyed by `entity.id`. The renderer is shared by every mob of that class.
