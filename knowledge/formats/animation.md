# Bedrock animation JSON

Status: design (phase 5).

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
