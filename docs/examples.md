# Example content

The library ships test content for every feature. It's only registered in a development environment (the dev client from `./gradlew runBtaClient` or IntelliJ's Run Client), never in a normal game. Everything is in the creative inventory under **Miscellaneous**.

The code is in `bta/src/main/java/io/github/tryrodave/jsonextended/` (each feature's `example/` folder, plus `dev/DevContent.java`), and the files in `bta/src/main/resources/assets/jsonextended/`.

## Blocks

| Block | Shows | Files |
| --- | --- | --- |
| Blockbench Test | A plain Blockbench export drawn by BTA's own reader | `models/block/blockbench_test.json` |
| Test Facing | Blockstate `x` and `y` rotations, two properties | `blockstates/test_facing.json` |
| Test Stairs | 40 variants, `uvlock`, the stair shape resolver | `blockstates/test_stairs.json` |
| Test Slab | Three models from one property, an unused metadata value | `blockstates/test_slab.json` |
| Test Fence | `multipart` and the connections resolver | `blockstates/test_fence.json` |
| Test Weighted | Weighted random variants | `blockstates/test_weighted.json` |
| Test Glass | `render_type: translucent`, a 45° element, a 32×32 texture | `models/block/test_glass.json` |
| Test Broken Model | A broken file: the missing model and one error line, no crash | `models/block/test_broken_model.json` |
| Test OBJ Crystal | An OBJ from Blender, no `.mtl` | `models/block/test_obj_crystal.*` |
| Test OBJ Ramp | An OBJ with an `.mtl`, turned by blockstate | `models/block/test_obj_ramp.*` |
| Test Chest | A tile entity model whose lid opens from code | `models/block/test_chest.json`, `tileentity/example/TestChest.java` |
| Test Animated Chest | The same lid moved by an animation file | `animations/test_chest.animation.json`, `animation/example/AnimatedChest.java` |
| Test Mob Spawner | Right-click: spawns the Test Mob | `animation/example/TestMob.java` |

Right-click steps most blockstate test blocks through their metadata values, and opens and closes the chests.

## Items

| Item | Shows |
| --- | --- |
| Test Ruby | `item/generated` |
| Test Sword | `item/handheld` |
| Test Display | Custom `display` poses |
| Test Layered | Two texture layers |
| Test Gem | A 3D item model from `elements` |
| Test Gem Front | The same with `gui_light: front` |
| Test Broken | A broken item model: the missing texture and one error line |

## Mob

The **Test Mob** has its own geo model (`models/entity/test_mob/test_mob.geo.json`, listed in `models/entity/models.json`), texture (`textures/entity/test_mob/0.png`) and animations (`animations/test_mob.animation.json`). It wanders like a cow, nods and looks around when standing, and swings its legs when walking.
