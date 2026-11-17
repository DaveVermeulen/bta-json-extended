# Bedrock animation JSON

Status: design (phase 5).

- Files: `<name>.animation.json` as exported by Blockbench (Bedrock format), `format_version`, `animations.<id>`: `loop`, `animation_length`, `bones.<bone>.rotation|position|scale` as constants or keyframe maps (`"0.5": [x, y, z]` or `{ "post": [...], "lerp_mode": "catmull_rom" }`).
- Load through `texturePackList`, so packs can change animations.
- Output: a pose (bone name to rotation, offset, scale) sampled at time t; renderers apply it. Animation never draws anything itself.
- BTA 8.0.1 ships `pig.animation.json` and `wolf.animation.json` but no code reads them; useful as test fixtures.
- Reference: old DragonFly `AnimationHelper`, `AnimationState`, `MojangAnimationDeserializer` (branch 7.3).
