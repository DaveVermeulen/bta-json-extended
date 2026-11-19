# Block collision, ray picking and the hover outline

Read from the decompiled BTA 8.0.1 jar (merged-deobf, Vineflower 1.11.1) on the maintainer's Mac, 2026-11-18. Signatures for `Block`/`BlockLogic` are in [api/world-blocks.txt](api/world-blocks.txt). Items marked *unverified* still need a `javap` check before code depends on them.

## Boxes

- Block code uses JOML `org.joml.primitives.AABBd` / `AABBdc`. `net.minecraft.core.util.phys.AABB` is legacy and pooled; don't use it.
- `Block<T>` is final and forwards every method below to its `BlockLogic`. Custom behaviour = a `BlockLogic` subclass.
- `BlockLogic` has **one** `protected final AABBd bounds` (0..1), shared by every position and thread of that block type. `setBlockBounds(...)` mutates it and `getBounds()`/`getBoundsRaw()` hand back that same object. Never set it per position. Modern logic (stairs, fence, slab) returns a `new AABBd(...)` from `getBoundsFromState` instead.

## Default `BlockLogic` methods

| Method | Default | Who calls it |
| --- | --- | --- |
| `AABBdc getBoundsFromState(WorldSource, TilePosc)` | `getBounds()` (block-local) | Selection and collision defaults, plus rendering and AO code |
| `AABBdc getSelectionAABB(WorldSource, TilePosc)` | bounds from state moved to world space | Outline (when no volume), World raycast null check |
| `@Nullable AABBdc getCollisionAABB(WorldSource, TilePosc)` | same as selection | `getCollisionAABBs` default, World raycast null check, `World.canBlockIdBePlacedAt` (entities in the way), `Particle.getCollisionBox`, moving piston, `WorldType`, rubyglass growth |
| `void getCollisionAABBs(World, TilePosc, AABBdc query, List<AABBdc> out)` | `addIntersectingBoundingBox(query, getCollisionAABB(..), out)` | Entity movement (see below) |
| `@Nullable BoundingVolume getBoundingVolume(World, TilePosc)` | `null` | Hover outline only |
| `HitResult collisionRayTrace(World, TilePosc, Vector3dc start, Vector3dc end, boolean useSelectionAABB)` | clips one box: the selection box (made local again) if the flag is set, else `getBoundsFromState` | World raycast |
| `protected void addIntersectingBoundingBox(AABBdc query, AABBdc box, List<AABBdc> out)` | adds `box` if non-null and it intersects `query` | helper |
| `protected HitResult traceRayAgainstLocalAABB(TilePosc, Vector3dc start, Vector3dc end, AABBdc localBox)` | clips one block-local box, returns `HitResult.Tile` or null | helper, reusable per part |
| `ISupport getSupport(World, TilePosc, Side)` | `FullSupport.INSTANCE` | Placement of torches, buttons etc.; support view (F3 option) |

Side from the clipped plane: minX `WEST`, maxX `EAST`, minY `BOTTOM`, maxY `TOP`, minZ `NORTH`, maxZ `SOUTH`.

## Entity collision

`World.getCubes(Entity, AABBdc)` scans blocks from floor(min)-1 to floor(max+1); for each block where `block.collidesWithEntity(entity, world, pos) && entity.collidesWithBlock(block, meta)` it calls `getCollisionAABBs(world, pos, entityBox, world.collidingBoundingBoxes)`, then adds nearby entity boxes. `getCollidingSolidBlockBoundingBoxes(Entity, AABBdc)` does the same for solid-render blocks. The list is a shared `World` field cleared on each call: add to it, never keep it. So **several collision boxes per block already work** with no mixin.

## Ray picking

`World.checkBlockCollisionBetweenPoints(Vector3dc start, Vector3dc end, boolean fluids, boolean ignoreNonColliderBlocks, boolean useSelectorBoxes)` (also 2- and 3-arg overloads) walks the ray voxel by voxel. For each block it takes `getSelectionAABB` (or `getCollisionAABB`) only as a **null check**, then calls `collisionRayTrace`; the first non-null hit wins. So **per-part picking already works** by overriding `collisionRayTrace` and returning the nearest part hit.

`HitResult` (`net.minecraft.core.util.phys`): `public final Vector3dc location`, `distanceTo(Entity)`. Subclasses (check with `instanceof`, there is no type enum):
- `HitResult.Tile(TilePosc tilePos, Side side, Vector3dc location)`, fields `tilePos`, `side`. Final class. **No part index.** Constructor visibility *unverified*.
- `HitResult.Clip(Side, Vector3dc)` (from `AABB.clip`), `HitResult.Entity(Entity)`.

## Hover outline

`RenderGlobal.drawTileSelectionBoxRaw(ICamera, TilePosc, double offX, double offY, double offZ)` (private), expand 0.002:
- `getBoundingVolume(world, pos)` non-null: `drawOutlinedVolume(volume, x, y, z, expand)` draws the volume's baked edges as `LINES`.
- else: one `getSelectionAABB` grown by 0.002, `drawOutlinedBoundingBox`.
- With `GameSettings.VIEW_SUPPORT_MODE` on, it also shades each side green or red from `getSupport(...)`.

`BoundingVolume` (`net.minecraft.core.util.phys`) is **a 16x16x16 voxel grid**, not a list of boxes: `boolean[] volume`, `SIZE = 16`.
- `static newFilledVolume()`, `newEmptyVolume()`, `static bakeFromBlockSpaceAabbs(AABBdc... localBoxes)` (samples each voxel centre `(i + 0.5) / 16`, then bakes)
- `fillVolume` / `carveVolume(int x0, y0, z0, x1, y1, z1)`, `addVolume` / `subtractVolume(BoundingVolume)`, `bake()`, `Float[] getBakedLineCoords()`, `copy()`
- Baked lines are the merged outer edges of the union: no lines between touching parts.
- **Outlines snap to 1/16.** A box edge at 0.3 is drawn at the nearest voxel edge. An exact off-grid outline needs a `RenderGlobal` mixin.
- Baking is not free: cache one volume per state, like `BlockLogicFire.SHAPE_VOLUMES` (lazy, per neighbour mask) and `BlockLogicStairs.STAIR_SELECTION_VOLUME` (8 orientations, baked once).

## How vanilla multi-box blocks do it

- **Stairs** (`BlockLogicStairs`): `getCollisionAABBs` adds 2 world-space parts (`collectStairPartAABBs`) through `addIntersectingBoundingBox`; `collisionRayTrace` loops `traceRayAgainstLocalAABB` over the local parts and keeps the nearest (ignores `useSelectionAABB`); `getBoundingVolume` returns a pre-baked volume per orientation. Selection and single collision box stay a full cube. Support from static `ISupport[]` tables.
- **Fence** (`BlockLogicFence`): `getCollisionAABBs` adds a 1.5-tall post plus one arm per connection; `getCollisionAABB` is the merged box stretched up 0.5; no volume or ray override, so outline and picking use the merged box.
- **Fire**: lazily baked `BoundingVolume` per mask, from `getShapeParts(mask)`.

## Support

`ISupport.canSupport(ISupport, Side)`; `ISupportable.getSupportConstraint(World, TilePosc, Side)`. Implementations `FullSupport.INSTANCE` and `PartialSupport(byte supportField)` (up/down/left/right/center bits, 2^5 values). Each logic sets support per side in `getSupport`; it is **not** derived from bounds. Exact bit layout and constructor visibility *unverified*.
