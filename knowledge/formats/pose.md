# Poses and rigs (phase 5)

Core code in `core/.../pose/` and `core/.../model/{ResolvedModel,ElementQuads}`. No game code; the bta tile entity renderer draws the result.

## Model quads

- `ResolvedModel.load(id, source)` merges the parent chain: textures merge with the child winning, `elements` and `groups` come from the nearest file that has them. A missing model throws `IllegalArgumentException`.
- `ElementQuads.bake(model)` turns elements into `ModelQuad`s (positions in blocks, 0..1; uvs 0..1 of the sprite; unit normal; resolved texture id or null; tint index; shade; cullface). It follows vanilla: corner order per side, uv corners in vertex order (u0,v0),(u0,v1),(u1,v1),(u1,v0), face `rotation` shifts the uv corner by r/90, default uvs from the element's bounds, element rotation about `origin` with `rescale` = 1/|cos|, cullface `bottom`/`top` read as `down`/`up`.
- Quads keep their element index, which is how a rig finds them.

## Rigs

- `Rig.fromGroups(groups, elementCount)` reads Blockbench's `groups` array (Blockbench writes it in the model JSON, BTA ignores it). A group is `{name, origin [px], rotation?, children: [element index | group]}`. Elements not in any group belong to the root part, named `""`.
- `Rig.fromMeshes(meshNames, pivots)` makes one part per OBJ object/group name; pivots in blocks, default (0,0,0).
- Part origins are stored in blocks (pixels / 16).

## Poses

- `PartTransform(rx, ry, rz, tx, ty, tz, sx, sy, sz)`: degrees, pixels, factors. `rotation(...)`, `translation(...)`, `with*`, `lerp(a, b, t)` (per component, for partialTick).
- `Pose` is immutable: `Pose.REST.with("lid", t)`. Parts not named keep their rest transform.
- `rig.pose(quads, pose)` returns a new list; quads of unmoved parts are the same objects.
- Matrix per part: parent · T(origin + t/16) · R(pose) · R(rest) · S · T(-origin). Rotation is Rz·Ry·Rx: X first, then Y, then Z, right-hand, the same as Blockbench's group rotation. Quarter turns are exact.
- Direction: for a lid hinged at the back (+Z) top, opening is **+X** (the front swings up and back). Tested in `RigTest`.

## Fixture

`core/src/test/resources/pose/test_chest.json`: Blockbench-style chest, base element without a group, `lid` group (origin [8, 9, 15]) with a nested `knob` group.
