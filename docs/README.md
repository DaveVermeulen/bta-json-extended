# BTA JSON Extended documentation

BTA JSON Extended is a library for Better Than Adventure 8.0.1 mods. It adds the parts of modern Minecraft's model system that BTA left out: blockstate files, item model files, OBJ and glTF models, model-driven tile entity renderers and Blockbench animations. It builds on the JSON block models BTA already reads, so everything Blockbench exports for a vanilla block works as it is.

On its own it adds nothing to the game. Your mod points it at JSON files and it draws them.

## Guides

1. [Installing](installing.md): what players and mod developers need.
2. [Getting started](getting-started.md): where files go, and registering a block and an item from your client entrypoint.
3. [Blockstates](blockstates.md): one block, many models, chosen by metadata or by its neighbours.
4. [Item models](item-models.md): flat, layered, handheld and 3D items, with display poses.
5. [Model JSON and Blockbench](model-json.md): what we add on top of BTA's model reader, and the error messages.
6. [OBJ models](obj-models.md): Wavefront `.obj` files as block models.
7. [glTF and GLB models](gltf-models.md): `.glb` and `.gltf` files as block models, and their animations on tile entities.
8. [Tile entity models](tile-entities.md): a Blockbench model drawn every frame, with parts moved from code.
9. [Animations](animations.md): Blockbench animations on tile entities and mobs.
10. [Example content](examples.md): the dev-only test blocks, items and mob, and where their files are.
11. [Known limitations](limitations.md): what doesn't work yet.

For building the library itself, see the [main README](../README.md#building).
