# Knowledge index

Open only the files your task needs. Each stays under ~150 lines; split a file when it grows and add the new one here.

| File | Answers |
| --- | --- |
| [bta/rendering.md](bta/rendering.md) | BTA's built-in JSON block models, `StaticBlockModel`, `BlockModelGeneric`, tessellator, lighting, textures |
| [bta/model-behaviour.md](bta/model-behaviour.md) | How BTA rotates models, merges parents, resolves #textures; which atlas textures live in |
| [bta/model-loading.md](bta/model-loading.md) | Load path from `loadDataModel` to Gson, cache, mixin hook points, `renderlayer`, errors, `texture_size`, `gui_light`, mixin setup |
| [bta/registration.md](bta/registration.md) | HalpLibe hooks, what triggers a model reload (pack screen, F3+R), the missing model, blocks and creative menu |
| [bta/items.md](bta/items.md) | `ItemModel`, display transforms, `Item` constructors, ids, lang keys, `ItemBuilder` |
| [bta/item-rendering.md](bta/item-rendering.md) | How `ItemModelStandard`/`ItemModelBlock` draw items: render params, GL order, display ids, `DisplayPos` defaults, which atlas |
| [bta/static-model.md](bta/static-model.md) | Implementing `StaticBlockModel` (OBJ etc.): renderAttached rotation, culling, lighting/AO, tint; renderStandalone; particle/overlay/display getters |
| [bta/item-rendering-3d.md](bta/item-rendering-3d.md) | GLRenderer/shader/atlas signatures; how block models render standalone (centred) for items |
| [bta/tile-entities.md](bta/tile-entities.md) | TileEntity class, registration, TE renderers end to end (doRender coords, GL setup, light), drawing block quads in a TE, hiding the static model, state sync, geo models |
| [bta/collision.md](bta/collision.md) | Collision boxes, ray picking (`collisionRayTrace`), hover outline (`BoundingVolume`), support; how stairs and fences do several boxes |
| [bta/entities.md](bta/entities.md) | Custom mobs: mob class and texture path, EntityDispatcher registration, spawning, models.json and geo lookup, MobRenderer and where bones are posed |
| [bta/api/](bta/api/README.md) | Exact public signatures of BTA and HalpLibe classes (javap) |
| [bta/vanilla-parents.md](bta/vanilla-parents.md) | Vanilla parent model ids (cube_all, stairs, slab...) |
| [bta/reading-bta-code.md](bta/reading-bta-code.md) | How to decompile BTA and HalpLibe on the Mac |
| [formats/blockstates.md](formats/blockstates.md) | Blockstate JSON and model JSON as we support them |
| [formats/models.md](formats/models.md) | Model JSON: Blockbench keys, `render_type`, `gui_light`, `loader`, readable load errors |
| [formats/items.md](formats/items.md) | Item model JSON as we support it |
| [formats/obj.md](formats/obj.md) | OBJ/MTL support, carried over from Betafront |
| [formats/pose.md](formats/pose.md) | Rigs from Blockbench groups or OBJ meshes, poses from code, baking JSON elements to quads |
| [formats/gltf.md](formats/gltf.md) | GLB/glTF support plan |
| [formats/animation.md](formats/animation.md) | Bedrock animation JSON; what BTA ships (static poses), BoneTransform and how bones are posed |
| [shapes.md](shapes.md) | Plan and design for multi-box block shapes (VoxelShape-like) |
| [build.md](build.md) | Building, testing, CI, versions and Maven repositories |
| [pitfalls.md](pitfalls.md) | Symptom, cause, fix |
| [decisions.md](decisions.md) | Dated decisions |
| [prior-art.md](prior-art.md) | Betafront and DragonFly: what to reuse, what to avoid |
