# Bedrock animation JSON

Status: core reader and player (step 1), tile entities (step 2), geo mobs (step 3).

- Files: `<name>.animation.json` as exported by Blockbench (Bedrock format), `format_version`, `animations.<id>`: `loop`, `animation_length`, `bones.<bone>.rotation|position|scale` as constants or keyframe maps (`"0.5": [x, y, z]` or `{ "post": [...], "lerp_mode": "catmull_rom" }`).
- Load through `texturePackList`, so packs can change animations.
- Output: a pose (bone name to rotation, offset, scale) sampled at time t; renderers apply it. Animation never draws anything itself.
- BTA 8.0.1 ships `pig.animation.json` and `wolf.animation.json` but no code reads them; useful as test fixtures.
- Reference: old DragonFly `AnimationHelper`, `AnimationState`, `MojangAnimationDeserializer` (branch 7.3).

## What BTA 8.0.1 actually ships (read from the jar, 2026-11-19)

The game's files are not ours to redistribute: don't copy them into the repo or our test fixtures. Write our own fixtures with the same shape.

**Paths**
| File | `format_version` |
| --- | --- |
| `assets/minecraft/models/entity/pig/pig.animation.json` | `1.8.0` |
| `assets/minecraft/models/entity/pig/pig.geo.json` | `1.8.0` |
| `assets/minecraft/models/entity/wolf/wolf.animation.json` | `1.8.0` |
| `assets/minecraft/models/entity/wolf/wolf.geo.json` | `1.10.0` |

Animations sit next to the geo file in the entity's folder (not in an `animations/` folder).

**Content: static poses only**
- Animation ids: `animation.pigsitting`, `animation.wolfidle`, `animation.wolfsitting`.
- Keys used: only `loop` (always `true`) and `bones.<name>.rotation` / `position`.
  - Every value is a constant `[x, y, z]` array: no keyframe maps, `pre`/`post`, `lerp_mode`, `animation_length`, `anim_time_update`, `blend_weight` or `scale`.
  - **No Molang strings at all.**
- Example shapes:
  - pig `body`: rotation `[-27.5, 0, 0]`, position `[0, -4, 1]`;
  - wolf `tail` (idle): rotation `[88.5, 0, 0]`.
- So they're poses for sitting/idle, not timelines. They are useful to test constant channels, not keyframes, interpolation or Molang.

**Bone names**
- **pig.geo:** `body` (base rotation 90, 0, 0), `head` (locator `lead`), `leg0`–`leg3` (`leg1`/`leg3` have `mirror: true`).
- **wolf.geo:** `head` (locator `lead`), `mouthItem` (parent `head`, no cubes; it's the held-item anchor, base rotation -90, 40, 2.5), `body`, `upperBody` (base rotation -90, 180, -180), `leg0`–`leg3`, `tail`.
- Every bone named in the animations exists in the matching geo file.
- Bone base rotations (90 on `body`) are part of the geo; animation rotations add to them.

## How BTA poses bones without an animation runtime

- Nothing in the 8.0.1 jar reads `*.animation.json`: no class references the files. There are no Animation, Keyframe or Molang classes for entities.
  - The jar's "Animation" classes are texture-frame animation (`texture.meta.AnimationProperties`) and the pickup particle.
- Instead the mob renderers set bone transforms in code every frame: `MobRendererPig` and `MobRendererWolf` write `rotX/rotY/rotZ` and `posX/posY/posZ` on `head`, `body`, `leg0..3`, `tail`, `upperBody`. The sitting poses are hard-coded there, matching the JSON.

`org.useless.dragonfly.models.entity` (javap -public):
```
public final class BoneTransform {
  public double posX, posY, posZ, rotX, rotY, rotZ, scaleX, scaleY, scaleZ;  // scale defaults 1
  public boolean visible;                                                    // default true
  public BoneTransform(); public void reset();                               // back to identity, visible
}
public interface StaticEntityModel {
  void resetBones(); BoneTransform getTransform(String bone); AABBdc visibleBounds();
  void translateToBone(String bone); void render(); void delete();
}
public class mojang.StaticEntityModelMojang implements StaticEntityModel {
  public static final int MAX_BONES;   // 128, the size of the bone uniform block
  public final Map<String, BoneTransform> transformMap; public final BoneTransform[] transforms;
  public final StaticEntityModelMojang$BoneEntry[] bones; public final EntityGeometryMojangData data;
  public final double inflation;
  public StaticEntityModelMojang(EntityGeometryMojangData, double inflation);
}
```

- **Use:** each frame, `model.resetBones()`, then `model.getTransform("head").rotX = ...` and so on, then `model.render()`.
  - `getTransform` returns a shared dummy for unknown bone names, so a typo is silently ignored.
- **`protected boolean boneTransform(Bone bone, Matrix4f dest)`** builds one bone's matrix. It recurses into the parent first, so parent transforms apply. Then:
  - `translate(pivot)`, then `translate(transform.pos)`;
  - `rotateZ(-(rotZ + boneRotZ))`, `rotateY(rotY + boneRotY)`, `rotateX(-(rotX + boneRotX))`;
  - `translate(-pivot)`, then `scale(transform.scale)`.
  - It returns `true` if this bone or any ancestor has `visible == false`.
- **Units and signs:**
  - `BoneTransform.rot*` are **radians**; the geo `rotation` is degrees.
  - **X and Z are negated, Y is not**: Bedrock's convention. Animation JSON degrees must become `rotX = toRadians(x)` etc. and go through the same path.
  - `pos*` are in model pixels (geo units, 16 per block). Scale is applied last in the chain, so it scales about the model origin, not the pivot.
- **`render()`:**
  - `pushFrame`, `Shaders.ENTITY`; sets the `uLightmap` uniform from `GLRenderer.getLightIndex()`.
  - Uploads one matrix per bone into `UniformBlockManager.BLOCK_ENTITY_BONES`, or a zero matrix for hidden bones.
  - Draws the prebuilt VAO in one call, then `popFrame`.
  - The geometry is baked once in the constructor, so animation only changes the bone matrices. That's cheap per frame.
- **`translateToBone(name)`:** multiplies the current model matrix by that bone's matrix and moves to its pivot. Renderers use it to attach held items (`rightItem`, `leftItem`, `mouthItem`).

**Implication for phase 6:**
- Our runtime samples `animations.<id>.bones` at time t and writes the result into `BoneTransform`s, converting degrees to radians.
- Base geo rotations stay in the geo.
- Molang and keyframes have no BTA precedent: follow the Bedrock and Blockbench spec, and test with our own fixtures.

## Our reader and player (`core/.../animation/`, phase 6 step 1)

- `AnimationFile.parse(id, text)`: `animations()`, `get(name)`, `warnings()`. Reads `loop` (`true`, `false`, `"hold_on_last_frame"`), `animation_length` (else the last keyframe), and per bone `rotation`/`position`/`scale` as a constant, a scalar (all axes), or keyframes (`[x, y, z]`, `{pre, post, lerp_mode}`, old `{vector}`). Malformed files throw `AnimationException` with file, animation, bone, channel and keyframe.
- **Molang:** numbers and number strings only. Any other string reads as 0 (1 for scale) and adds a warning. Evaluating Molang is later work.
- `Channel.sample(t)` follows Blockbench's preview: before the first keyframe its `pre`, after the last its `post`; a segment is step if its first keyframe is step, Catmull-Rom if either end is, else linear. Catmull-Rom uses the neighbouring keyframes (or repeats the ends).
- `Animation.sample(seconds, base)` returns a `Pose`: animation values are added to `base` (rotation and translation add, scale multiplies, `PartTransform.plus`). Loop wraps, hold clamps, play-once has no effect after its length (`finished`).
- **Axes, Java block models (Blockbench groups, `Rig`):** rotation `(-x, -y, z)`, position `(-x, y, z)` from the file, the flip GeckoLib undoes. So Bedrock `-90` X opens our chest lid like `PartTransform.rotation(90, 0, 0)`. **Not yet checked in game.**
- **Axes, geo entity models:** use `Animation.raw(bone, seconds)` (file values) and write `toRadians` of them into `BoneTransform`: BTA's `boneTransform` applies the Bedrock flip itself (see above).
- `AnimationPlayer`: one per object. `play(anim, now[, fadeSeconds])`, `ensure(...)` (no restart if already current), `stop()`, `playing(now)`, `pose(now, base)`. Crossfade lerps each part from the old animation's pose to the new one's. Time is the caller's clock in seconds, e.g. `(worldTime + partialTick) / 20`.
- Fixture: `core/src/test/resources/animation/test_chest.animation.json` (open: hold, catmull-rom; bob: loop, scalar scale; snap: step, pre/post, Molang).

## In game (`bta/.../animation/`, phase 6 step 2)

- `Animations.load("ns:path")` reads `/assets/ns/animations/path.animation.json` through the texture packs. Missing or broken files and Molang warnings log once per session; it then returns an empty file, so `get` gives null.
- Load in the tile entity renderer (lazily, on first draw): renderers are made new on every pack reload, so pack changes reach animations.
- Example `animation/example/AnimatedChest` (dev block 9125, Test Animated Chest), the test chest's model with `animations/test_chest.animation.json`:
  - The renderer's `Poser` keeps one `AnimationPlayer` per tile entity (client state, not saved). It calls `ensure(open ? open : close, now, 0.1)` with `now = (te.ticks + partialTick) / 20`, then adds `idle` once `open` has finished.
  - A chest that has just loaded uses `skipToEnd`, so it shows its saved state (open or closed) without swinging into it.
- `AnimationPlayer.skipToEnd(anim, now)`: as if already played to its end, for state loaded with the world (hold: last frame; loop: goes on; once: no effect).
- `AnimationPlayer.elapsed(now)`: seconds since the current animation started (hold animations never report `finished`).

## Geo mobs (`AnimatedMobRenderer`, phase 6 step 3)

- `bta/.../animation/AnimatedMobRenderer<T extends Mob>(shadow, animationFile, idle, walk, walkCycle)`: a `MobRenderer` for a geo model listed in `models/entity/models.json` (see [../bta/entities.md](../bta/entities.md)). Each frame: `getModel("main")`, `resetBones()`, then each bone from `pose(mob, pt)` into its `BoneTransform` (degrees to radians, raw file axes).
- `pose`: `idle` on the mob's age (`getLimbPitch / 20` s), blended into `walk` by walk amount (`getLimbYaw`, 0..1) with `Animation.mix`. Walk time follows `getLimbSwing`: one loop per `walkCycle` of limb swing (`VANILLA_WALK_CYCLE` = 2π/0.6662, the vanilla leg period), so steps match ground speed. Override `pose` for more.
- Core: `Animation.rawPose(seconds)` (all bones, file values) and `Animation.mix(a, b, t)` (missing bones blend with neutral).
- Example `animation/example/TestMob` (dev): our own `models/entity/test_mob/test_mob.geo.json` (body, head child of body, leg0-3), `textures/entity/test_mob/0.png`, `animations/test_mob.animation.json` (walk, idle). Spawned by right-clicking the Test Mob Spawner block (9126). No head look yet.
