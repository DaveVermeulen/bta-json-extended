# How BTA's built-in JSON models behave

Plain-language notes from reading the decompiled 8.0.1 code (2026-11-17). Signatures: [api/dragonfly.txt](api/dragonfly.txt).

## Whole-model rotation (`renderAttached(rotX, rotY, rotZ, ...)`)

- The values are quarter turns. Each vertex is moved to the block centre, rotated with JOML `rotateX(rotX·90°)`, then `rotateY(rotY·90°)`, then `rotateZ(rotZ·90°)`, and moved back. **X is applied first, then Y, then Z.**
- JOML rotations are right-handed (counter-clockwise looking down the positive axis). So `rotY = +1` turns **east to north** (counter-clockwise seen from above), and `rotX = +1` turns **north to up**.
- Vanilla blockstates turn the other way: `"y": 90` turns north to east (clockwise from above), and `"x": 90` turns north to down. **So vanilla `x`/`y` in degrees map to BTA quarter turns as `-x/90` and `-y/90`** (mod 4), applied X then Y, as vanilla does. Derived from the maths; confirm with a test block in the phase 0 spike.
- Cull faces and face directions are rotated the same way (`Direction.rotateX/Y/Z`), so culling follows the rotation.
- `uvlock = true` re-maps each face's UVs (`rotateUV`) so textures stay aligned to the world.
- `xOff, yOff, zOff` shift the whole model in blocks after rotation.
- Example in the game: `BlockModelGenericRotatable` uses SOUTH = no rotation, EAST = rotY 1, NORTH = rotY 2, WEST = rotY 3, UP = rotX 1, DOWN = rotX -1.

## Parent merging (`BlockModelMojangData` constructor)

- The parent is loaded first (through the same cache, so texture packs apply) and merged into the child:
  - `textures`, `display`, `particles`: parent's map, then the child's entries on top.
  - `elements`: the child's list if it has one, else a copy of the parent's (never merged).
  - `ambientocclusion`: the child's if set, else the parent's. With no parent the default is `true`.
  - `renderlayer`: the child's if set, else the parent's.
- A parent that can't be found is ignored silently (the child keeps only its own values). We should log this.
- Ids without a namespace get `minecraft:`.
- Missing left-hand `display` poses are copied from the right-hand ones.

## Texture references (`CompiledBlockModelMojangData`)

- A face texture `#name` looks up `textures.name`; if that value starts with `#` it is followed again (chains work). A plain value becomes a texture id, with `minecraft:` added when there is no namespace, then looked up in the atlas.
- `#particle` fills in `#particle_up/down/north/south/west/east` and `#overlay` when those aren't set. A missing particle side logs a warning per side.
- Unresolved references log `Texture for symbol '...' is not assigned` and fall back to a default.

## Texture atlases

`TextureRegistry` has separate atlases for `textures/block`, `textures/item`, `textures/particle`, `textures/art` and `textures/gui/...`. Block models are drawn during chunk building with the block atlas, so **block model textures must be `ns:block/...`**. Item textures (`ns:item/...`) live in the item atlas: a JSON item model drawn with item textures must bind/use that atlas. `HalpLibe.registerMod(id, true)` stitches a mod's textures (via `TextureHelper.initializeAllFiles(modId, AtlasStitcher, boolean)`); check which folders it covers before phase 2.

## Display transforms

See [items.md](items.md): `ItemModel#getDisplayPos(id)` is overridable; applied as translate, rotate X, Y, Z (degrees), scale, mirrored for the left hand.
