# Shapes

**Job:** block shapes made of several boxes, like NeoForge's `VoxelShape`, defined in code: build, combine, move and rotate them by facing, and plug them into BTA's collision, ray picking and hover outline. The plan and steps are in [knowledge/shapes.md](../knowledge/shapes.md).

**Owns:** `core/.../shape/` and its tests, `bta/.../shape/` (the BTA adapter and the shaped block logic), shape examples (dev-only blocks).

**Reads first:** `knowledge/shapes.md`, `knowledge/bta/collision.md`, `knowledge/pitfalls.md`.

**Never touches:** mixins (if an exact outline or a part index on `HitResult` is wanted, ask Platform in the PR), rendering and model code, blockstate and model JSON parsing. Shapes are code-only: no JSON format for them unless Dave decides otherwise.

**Rules of thumb:**
- Never call `setBlockBounds` per position: `BlockLogic.bounds` is shared by every block of that type. Return new or cached boxes.
- Shapes are immutable. Cache everything per state (meta, or facing): boxes in world space are cheap, a baked `BoundingVolume` is not.
- Prefer overriding public `BlockLogic` methods over mixins. Today's three overrides (`getCollisionAABBs`, `collisionRayTrace`, `getBoundingVolume`) need none.

**Done well means:** every shape operation is tested in `core/` (boxes, union, offset, rotation, ray clip, the 1/16 sampling the outline uses) with no game needed, and a mod author makes an L-shaped, rotatable block with one shape and one line per override.
