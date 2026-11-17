# glTF / GLB

Status: plan (extension phase, after modern-Minecraft parity).

- glTF 2.0: `.gltf` (JSON + `.bin` + images) and `.glb` (one binary file: JSON chunk + BIN chunk). Parse with Gson plus our own buffer/accessor reader.
- Meshes: triangles from `POSITION`, `NORMAL`, `TEXCOORD_0`, `indices`; node transforms (TRS or matrix) baked for static models.
- Materials: `baseColorTexture` and `baseColorFactor`. **Risk:** textures embedded in a GLB are not in BTA's block atlas (atlas entries come from jar files). Options: require textures referenced by id in the model JSON (`"textures": {...}` like OBJ), or stitch embedded images at load (needs Platform's help). Decide in the spike.
- Animations: glTF `animations` channels (translation, rotation, scale per node; LINEAR, STEP, CUBICSPLINE) converted to the same clip format the Animation agent uses, so one player drives both Blockbench and glTF animations.
- Later, maybe: skinning (joints and weights) for smooth deformation. Much bigger; node animation first.
- Static blocks use the mesh like OBJ; animated GLB is for tile entities and entities, drawn every frame.
