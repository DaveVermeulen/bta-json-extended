# Known limitations

These don't work yet:

- **Molang.** Animation values must be numbers; expressions count as 0 and log a warning.
- **Mob head look.** `AnimatedMobRenderer` plays idle and walk, but doesn't turn the head towards what the mob looks at.
- **Item `overrides`** (a different model by damage or a predicate) log a warning and are ignored.
- **`gui_light` for flat items.** It's read, but flat items always get BTA's normal inventory lighting.
- **Tinted 3D item models.** Faces with a `tintindex` are drawn white in item models.
- **Tile entity models**: OBJ files can't be posed yet (model JSON and glTF can). Faces aren't culled against neighbouring blocks.
- **OBJ transparency from materials.** MTL `d` and `Tr` are read but ignored; use a texture with alpha and `render_type`.
- **Rotations** in blockstates are in steps of 90 degrees, as in vanilla.
- **New texture names from packs.** A texture pack can replace any file, but a texture that no mod jar ships isn't in BTA's atlas, so a pack's model can only point at textures that exist.
- **Multiplayer** hasn't been tested. The test chests keep their open state in metadata, which the server sends; animation state is client-only.
- **glTF**: images inside the file aren't used (textures come from the model JSON), skins and morph targets aren't read, and animations only play on tile entities (block models show the rest pose). Blockbench's glTF export hasn't been tried in game yet, so its size and position may need `offset`.

Found something else? Please open an issue at https://github.com/DaveVermeulen/bta-json-extended/issues.
