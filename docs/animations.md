# Animations

Blockbench animations (`.animation.json`, the Bedrock format Blockbench exports) can move the parts of [tile entity models](tile-entities.md) and the bones of geo mobs. Animations are read through the texture packs like every other file.

## Making an animation

1. Blockbench's "Java Block/Item" format has no Animate tab. For a block model, save a copy of the project and convert it (**File > Convert Project**) to "Bedrock Entity" or "Generic Model". Keep the group names and pivots: they are how the animation finds its parts. Mobs are made in a Bedrock entity project from the start.
2. Animate in the **Animate** tab. Name animations as you like, e.g. `animation.chest.open`.
3. **Animation > Export Animations** writes one file with all of them. Put it at `assets/<modid>/animations/<name>.animation.json`; its id is `<modid>:<name>`.

### What's read

| Key | Supported |
| --- | --- |
| `loop` | `true` (loops), `false` (plays once, then has no effect), `"hold_on_last_frame"` (stays on the last frame) |
| `animation_length` | Yes. Without it, the time of the last keyframe |
| `bones.<name>.rotation`, `position`, `scale` | A constant (`[x, y, z]`), one number for all three axes, or keyframes (`"0.5": [x, y, z]`) |
| Keyframe `pre` / `post` | Yes: a different value arriving at and leaving a keyframe makes a jump |
| `lerp_mode` | `linear`, `catmullrom` (smooth) and `step`, sampled the way Blockbench previews them |
| Molang (`"math.sin(q.anim_time * 90)"`) | **Not yet.** Numbers and number strings work. Any other string counts as 0 (1 for scale) and logs a warning |

Rotations are in degrees, positions in pixels. For tile entity models, values are converted from Bedrock's axes to the Java model's for you, so a lid that opens upwards in Blockbench opens upwards in game.

## On a tile entity

Load the file in the renderer and keep one `AnimationPlayer` per tile entity on the client:

```java
AnimationFile[] file = new AnimationFile[1];   // read on first draw, again after each pack reload
dispatcher.assignRenderer(ChestTileEntity.class, new ModelTileEntityRenderer<ChestTileEntity>("mymod:block/chest",
        (chest, partialTick) -> {
            if (file[0] == null)
                file[0] = Animations.load("mymod:chest");
            float now = (chest.ticks + partialTick) / 20f;            // seconds, from a tick counter on the TE
            boolean open = (chest.getBlockMeta() & 4) != 0;
            Animation wanted = file[0].get(open ? "animation.chest.open" : "animation.chest.close");
            if (wanted == null)
                return Pose.REST;
            AnimationPlayer player = chest.player();                  // a client-only field on the TE
            if (player.current() == null)
                player.skipToEnd(wanted, now);                        // just loaded: show the saved state, no swing
            else
                player.ensure(wanted, now, 0.1f);                     // switch, with a 0.1 s crossfade
            return player.pose(now, Pose.REST);
        }));
```

| `AnimationPlayer` | Does |
| --- | --- |
| `play(animation, now[, fadeSeconds])` | Starts an animation, optionally blending from the current one |
| `ensure(animation, now, fadeSeconds)` | Plays it unless it's already the current one, so it's safe to call every frame |
| `skipToEnd(animation, now)` | Shows it as already finished: use it for state loaded with the world |
| `pose(now, base)` | The pose now, added on top of `base` (a pose from your own code, or `Pose.REST`) |
| `playing(now)`, `elapsed(now)`, `current()`, `stop()` | State |

`Animation.sample(seconds, base)` samples one animation directly, without a player. The working example is the Test Animated Chest: `bta/src/main/java/io/github/tryrodave/jsonextended/animation/example/AnimatedChest.java`.

## On a mob

Mobs use BTA's own geo models (Bedrock `.geo.json`), which BTA reads from a manifest in each mod's assets:

`assets/<modid>/models/entity/models.json`:

```json
{
  "model_paths": ["/assets/mymod/models/entity/deer/deer.geo.json"],
  "renderers": {
    "mymod:deer": {"main": "geometry.mymod.deer"}
  }
}
```

- `model_paths` are full resource paths. `renderers` maps your entity id to the geometry's `identifier`. Geometry ids are global, so prefix them with your mod id.
- The texture is `assets/<modid>/textures/entity/<id>/0.png`, set in the mob's constructor with `setTextureIdentifier(MOD_ID, "deer")`.
- A wrong id gives a plain 16-pixel cube.

Register the mob (common side) and an `AnimatedMobRenderer` (client):

```java
EntityDispatcher.getInstance().addMapping(Deer.class, NamespaceID.fromPool(MOD_ID, "deer"), Deer::new);

ClientEvents.ENTITY_RENDERER_RELOAD.listen(Key.of(MOD_ID), dispatcher ->
        dispatcher.assignRenderer(Deer.class, new AnimatedMobRenderer<Deer>(0.7f, "mymod:deer",
                "animation.deer.idle", "animation.deer.walk", AnimatedMobRenderer.VANILLA_WALK_CYCLE)));
```

- `idle` plays on the mob's age; it blends into `walk` as the mob speeds up.
- The walk animation's time follows the distance walked: one loop per `walkCycle` of limb swing. `VANILLA_WALK_CYCLE` matches vanilla's leg swing, so steps keep pace with the ground. Either name can be null.
- Override `pose(mob, partialTick)` to add more, such as an attack animation. It returns bone values in the file's units; `Animation.rawPose(seconds)` and `Animation.mix(a, b, t)` help.

The working example is the Test Mob: `bta/src/main/java/io/github/tryrodave/jsonextended/animation/example/TestMob.java`, with its files in `assets/jsonextended/models/entity/` and `animations/test_mob.animation.json`.
