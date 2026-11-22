# Block shapes: plan

2026-11-18. A side track next to the phases in [PLAN.md](../PLAN.md), owned by the [Shapes agent](../agents/shapes.md). BTA facts behind it: [bta/collision.md](bta/collision.md).

## Goal

A block made of several boxes (a table, an L-shaped ledge, a pipe) collides, is picked and is outlined by its real shape, like NeoForge's `VoxelShape`. Shapes are defined in Java, not JSON.

## What BTA 8.0.1 already gives us

More than expected. Checked against the decompiled jar:

| Need | BTA 8.0.1 | Our work |
| --- | --- | --- |
| Entities collide with each box | `getCollisionAABBs(world, pos, query, out)` is already a list (stairs and fences use it) | Override it, add each part |
| Aim at each box | `World.checkBlockCollisionBetweenPoints` calls `collisionRayTrace` per block; stairs loop over parts | Override it, keep the nearest part hit |
| Outline of the real shape | `RenderGlobal` draws `getBoundingVolume(...)` if non-null: a 16x16x16 voxel grid baked to merged edges | Override it, return a cached volume |
| Which part was hit | No part index in `HitResult.Tile` (final class) | Recompute from the hit point when needed (no mixin) |
| Outline off the 1/16 grid | `BoundingVolume` snaps to 1/16 | Draw our own exact edges, like modern Minecraft (see "Exact outline"), in step 2 |

So the core feature needs **no mixins**. That is the main finding.

## Design

### core: `io.github.tryrodave.jsonextended.shape` (pure Java)

- `Box`: immutable, block units (0..1, may reach outside the block like fences). `Box.px(x0, y0, z0, x1, y1, z1)` in pixels (0..16) like NeoForge's `Block.box`.
- `Shape`: immutable list of boxes.
  - Build: `Shape.empty()`, `Shape.full()`, `Shape.box(...)`, `Shape.px(...)`, `Shape.of(Box...)`.
  - Combine: `a.or(b)` (union, keeps boxes, drops exact duplicates and boxes inside another), `Shape.or(a, b, c...)`.
  - Move: `offset(dx, dy, dz)`.
  - Rotate: `rotateY(degrees)` and `rotateX(degrees)` in 90 degree steps about the block centre, **using the blockstate convention** (`"y": 90` turns north to east), so a shape rotates exactly like its model. `forFacing(Facing)` with our own `Facing` enum (`DOWN, UP, NORTH, SOUTH, WEST, EAST`), base shape facing north. `Shape.byFacing(base)` precomputes all six (or four horizontal).
  - Query: `boxes()`, `bounds()` (the union box, for BTA's single-box callers), `isEmpty()`.
  - Ray: `clip(start, end)` gives the nearest `ShapeHit(boxIndex, point, face)` or null.
  - Outline: `edges()` gives the exact outer edges of the union, like modern Minecraft's `VoxelShape.forAllEdges`: build a grid from every box's own coordinates (like modern `ArrayVoxelShape`, so any size is exact), mark the filled cells, and keep each grid edge where the filled pattern of its four neighbouring cells is not flat (a corner or a crease). Merges touching boxes, no lines inside.
  - Voxels: `voxels16()` gives the same 16x16x16 sampling as BTA's `BoundingVolume.bakeFromBlockSpaceAabbs` (voxel centre `(i + 0.5) / 16` inside a box), so tests can check what the outline will look like.
- Later, step 6: `a.minus(b)` (subtract). Easiest exact version splits each box of `a` around `b` into at most 6 boxes; on the 1/16 grid it can also go through voxels.

### bta: `io.github.tryrodave.jsonextended.shape`

- `BlockShapes`: static helpers, so blocks that already extend another logic (rotatable, chest...) can use shapes too.
  - `collide(Shape, TilePosc, AABBdc query, List<AABBdc> out)`: each box moved to world space, added if it intersects (JOML `testAABB`).
  - `rayTrace(Shape, TilePosc, start, end)`: `Shape.clip` in block space, mapped to `new HitResult.Tile(pos, side, location)`.
  - `volume(Shape)`: a `BoundingVolume` whose lines are `Shape.edges()` (see "Exact outline"), cached per `Shape` instance (identity map), so a shape from `byFacing` is built once. Fallback: `BoundingVolume.bakeFromBlockSpaceAabbs(...)`, snapped to 1/16.
  - `partAt(Shape, HitResult.Tile)`: which box the hit is on (nearest box containing the hit point), for blocks that react per part.
  - `Facing` to and from BTA `Direction`/`Side`.
- `ShapedBlockLogic extends BlockLogic`: one method to implement, `Shape getShape(WorldSource world, TilePosc pos)` (default: from meta via `getShape(int meta)`), and optionally a separate `getCollisionShape` (default: the same shape; return `Shape.empty()` for walk-through). It overrides:
  - `getCollisionAABBs` → `BlockShapes.collide`
  - `collisionRayTrace` → `BlockShapes.rayTrace` (selection shape)
  - `getBoundingVolume` → `BlockShapes.volume`
  - `getBoundsFromState` → `shape.bounds()` (so placement checks, particles and pistons get a sensible single box)
  - `getCollisionAABB` → collision shape bounds in world space, or null when empty
  - `isCubeShaped()` → false, `isSolidRender()` → false by default (a shaped block isn't a full cube; otherwise neighbour faces get culled)

Example for a mod author:

```java
static final Shape LEDGE = Shape.or(Shape.px(0, 0, 0, 16, 8, 16), Shape.px(0, 8, 8, 16, 16, 16));
static final Shape[] BY_FACING = Shape.byFacing(LEDGE);

class LedgeLogic extends ShapedBlockLogic {
    @Override protected Shape getShape(int meta) { return BY_FACING[meta & 3]; }
}
```

## Exact outline (like modern Minecraft)

Modern Minecraft (1.14+) keeps a shape as a voxel grid too, but the grid lines are the boxes' own coordinates, not a fixed 1/16 grid, and the outline is drawn from that shape's edges. We do the same in `core/` (`Shape.edges()`), then hand the lines to BTA one of two ways, picked in step 0:

1. **No mixin:** `RenderGlobal.drawOutlinedVolume` only reads `volume.getBakedLineCoords()`. If `BoundingVolume` isn't final and that method can be overridden, `ShapeVolume extends BoundingVolume` returns our exact lines.
2. **Small mixin (Platform):** otherwise, inject at the head of `RenderGlobal.drawTileSelectionBoxRaw` and draw our edges for blocks with a `ShapedBlockLogic`.

Differences from modern that stay: BTA collides with a list of boxes instead of one shape per block (same result for movement), and the hit has no part index (we recompute it).

## Steps (one PR each)

| Step | Scope | Done when | Size |
| --- | --- | --- | --- |
| 0. Verify | Mac session: `javap -p` for `BoundingVolume` (is the class final? is `getBakedLineCoords` overridable? constructors?), `HitResult` (+ `$Tile` constructor visibility), `ISupport`, `FullSupport`, `PartialSupport`, `RenderGlobal`; add them to `knowledge/bta/api/` | Every name the adapter uses is in the api files | 1 hour |
| 1. Core shapes | `Box`, `Shape`, union, offset, rotation, `byFacing`, `bounds`, `clip`, `voxels16`, tests | Tests cover rotation (matches `QuarterTurns`/blockstate `y`), clip nearest part and face, union clean-up, voxel sampling of off-grid edges | 2-3 days |
| 2. BTA adapter | `BlockShapes`, `ShapedBlockLogic`, dev-only examples: Test Ledge (L-shape, 4 facings), Test Table (top and four legs) | Dave checks in game: walk under the table, stand on the ledge step, outline follows the shape exactly (also on a test block with off-grid sizes, e.g. 0.3), clicking each part targets the block, F-key collision boxes match | 2-3 days |
| 3. Support | `BlockShapes.support(Shape, Side)`: full face covered → `FullSupport`, else `PartialSupport` from which centre/edge cells are covered; used by `ShapedBlockLogic.getSupport` | Torch on the ledge's top step works, not on its empty half; support view (F3 option) shows green/red right | 1 day |
| 4. Docs | `knowledge/formats/shapes.md` (author guide), README section, CHANGELOG | A mod author can follow it without reading BTA code | half a day |
| 5. Optional mixin (Platform) | Part index carried with the hit, only if `partAt` isn't enough | Only if Dave wants it after step 2 | 1 day |
| 6. Optional subtract | `Shape.minus` | Tests; one example with a hole | 1-2 days |

Steps 1 and 2 are the feature. Step 1 runs fully in a cloud session; step 2 needs the Mac and Dave in game.

## Risks and open questions

- `getBoundsFromState` is also read by rendering and AO code. Returning the union box may change how a JSON-model block is lit or culled. Check in step 2; if it matters, return the full cube there and use the union only in `getCollisionAABB`/`getSelectionAABB`.
- Exact outline: if `BoundingVolume` can't be subclassed, it needs the small `RenderGlobal` mixin (Platform). Either way the edges come from `core/` and are tested there.
- `HitResult.Tile` has no part index; `partAt` recomputes it from the hit point. Ties on a shared face go to the first box.
- Shapes that depend on neighbours (like fences) work through `getShape(world, pos)`, but the outline volume must then be cached per neighbour mask, not per meta.
- Open: should a blockstate JSON be able to name a shape later? Out of scope now (Dave: code only).
