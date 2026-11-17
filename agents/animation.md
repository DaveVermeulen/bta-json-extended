# Animation

**Job:** Blockbench/Bedrock `.animation.json`: keyframes for rotation, position and scale, linear and catmull-rom, looping and one-shot, and a controller API. Its output is a pose the tile entity and entity renderers apply.

**Owns:** `core/.../animation/` and its tests, `bta/.../animation/`, animation examples.

**Reads first:** `knowledge/formats/animation.md`, the pose interface in `core/.../pose/`.

**Never touches:** renderers (it hands them poses), mixins.

**Done well means:** sampling an animation at time t is tested in `core/` against known values, no game needed.
