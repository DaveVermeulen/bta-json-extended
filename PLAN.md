# BTA JSON Extended: plan

2026-11-17. This file is the current plan; decisions live in knowledge/decisions.md.

## Name

- Display name: BTA JSON Extended
- Repo and Modrinth slug: `bta-json-extended` (new repo, not a betafront branch)
- Mod id: `jsonextended`
- Package: `io.github.tryrodave.jsonextended`
- OBJ loader id: `jsonextended:obj`; `betafront:obj`, `neoforge:obj`, `forge:obj` still accepted

## Summary

BTA 8.0.1 already ships the core of DragonFly inside the game jar (`org.useless.dragonfly`): vanilla-style JSON block models and Bedrock `.geo.json` entity models, used by 1,400+ of its own block models. So the new mod should not write a JSON model loader. It adds what BTA left out: blockstate JSON, JSON item models, OBJ as a second model type, model-driven tile entity renderers, and animation. Roughly 6 phases, 6 to 9 weeks of evenings, with a useful release after phase 2.

## What BTA 8.0.1 already has (checked against the decompiled 8.0.1 jar and HalpLibe 6.2.0)

| Feature | Where | Notes |
| --- | --- | --- |
| JSON block models | `BlockModelMojangData`, `StaticBlockModelMojang` | `parent`, `textures`, `elements` (`from`, `to`, `rotation` any angle + `rescale`, `shade`, `light_emission`), faces (`uv`, `rotation`, `cullface`, `tintindex`), `display`, `ambientocclusion`, BTA's own `renderlayer` and `particles`. Path `assets/<ns>/models/<path>.json` |
| Texture pack overrides | `Cache.loadModelData(texturePackList, id)` | Cache cleared on each dispatcher reload |
| Smooth lighting / AO | `StaticBlockModelMojang.renderAttached` | Vanilla corner rule; emissive elements skip it |
| Whole-model rotation | `renderAttached(..., rotX, rotY, rotZ, offsets, uvlock, cullFaces, overrideTexture)` | 90 degree steps on 3 axes, passed from Java |
| Picking a model by state | `BlockModelGeneric.getModelFromData(meta)` + ~60 `BlockModelGeneric*` subclasses | All hard-coded in Java |
| Block item display | `BlockModel.getItemDisplayPos(id)` | Uses the JSON `display`; left hand mirrored from right |
| Entity models | `EntityGeometryMojangData`, `StaticEntityModelMojang` | Bedrock geo, listed in `models/entity/models.json`; also used by sign/flag tile entity renderers |
| Hooks | HalpLibe `ModelHelper`, `ClientEvents.*_RELOAD`, `ModelEntrypoint` | Block, item, entity, tile entity, block colour dispatchers |

Entry point: `BlockModelDispatcher.loadDataModel("ns:block/name")`.

## Gaps the mod fills

| Gap | Today | Mod adds |
| --- | --- | --- |
| Blockstate JSON | Java subclasses | `assets/<ns>/blockstates/<block>.json`: `variants`, `multipart`, `x`/`y`/`uvlock`, weighted variants, named properties from metadata |
| Meta to properties | none | Declarative bit map per block, Java `StateResolver` hook for neighbour-dependent blocks |
| Item models | `ItemModel` subclasses, one icon | `models/item/*.json`: `item/generated`, `item/handheld`, 3D items, `display` |
| OBJ | none | `"loader": "jsonextended:obj"` (old ids accepted), our own parser |
| GLB / glTF | none | Meshes and node animations as a model type |
| Unread JSON keys | `texture_size`, `render_type`, `loader`, `gui_light` ignored, poor errors | Handle cleanly; one readable log line per bad file |
| Model tile entity renderers | hand-written | Generic renderer for JSON/OBJ/geo models with code-driven bones |
| Animation | none at runtime (`pig.animation.json` is unused) | Bedrock `.animation.json` for entities and tile entities |

## Lessons from DragonFly (UselessSolutions/DragonFly, branch 7.3, BTA 7.3_04)

- Only the static half was upstreamed. Blockstates (`BlockstateData`, `Condition`, `MetaStateInterpreter`), `BlockModelDFObj` and animation stayed in the mod: exactly our gaps, and good reference code.
- Blockstates used a per-block Java interpreter returning a string map. Keep that as a hook, but add a JSON property map for the common case.
- Multipart conditions silently skipped unknown keys. Validate at load and log.
- It copied `RenderBlocks` and `LightingCache`, which broke across versions. Implement `StaticBlockModel` and call BTA's code instead.
- Animations loaded only from the classpath. Load everything through `texturePackList`.
- Don't reuse `org.useless.dragonfly` package or class names: they are in the game jar now.
- JitPack builds failed often. Use the planned GitHub Pages Maven.

## Architecture

BTA-only mod on HalpLibe, plus a pure-Java `core/` for parsing and tests.

1. Model types implement BTA's `StaticBlockModel`. OBJ becomes `ObjStaticModel`; a model JSON with `loader` picks it, so anything taking a `StaticBlockModel` can draw OBJ.
2. `Models.load(id)` reads JSON via `texturePackList`, routes `loader` models to our loaders and everything else to `BlockModelDispatcher.loadDataModel`. Bad files give the missing cube plus one log line.
3. `BlockStateModel extends BlockModelGeneric`: meta → properties → variant/multipart parts → each part's `renderAttached` with rotation and uvlock. Cached per meta value.
4. Properties: `"facing": {"bits": "0-1", "values": ["south","west","north","east"]}` in the blockstate JSON; `StateResolver` for world-dependent blocks.
5. Registration: `Models.blockstate(block)`, `Models.item(item)` from the client entrypoint; reload on HalpLibe `BLOCK_MODEL_RELOAD` / `ITEM_MODEL_RELOAD`.
6. `JsonItemModel extends ItemModel`: layered sprites or a full model with per-context `display`.
7. Later: `ModelTileEntityRenderer` with per-frame bone poses; an animation player produces those poses.

Mixins only where HalpLibe has no hook (likely the item display transform).

## Phases

Priority: work like modern Minecraft first (phases 0 to 3), then extend on top (phases 4 to 7).

| Phase | Scope | Done when | Size |
| --- | --- | --- | --- |
| **Parity** | | | |
| 0. Build + spike | Gradle build (`core/` + `bta/`), GitHub Actions. Block using `BlockModelGeneric` with a Blockbench JSON | A Blockbench "Java Block" export renders with no new code; CI green | 2-3 days |
| 1. Blockstates | variants, multipart, x/y/uvlock, weighted, meta property map, `StateResolver`, cache, errors | Rotatable, slab-like, fence-like examples driven only by JSON | 1.5-2 weeks |
| 2. Item models | generated/handheld layers, 3D items, display, gui_light | Example items right in hand, GUI, ground. **First public release** | 1.5-2 weeks |
| 3. Model JSON polish | Blockbench keys BTA ignores (`texture_size`, `render_type`, `gui_light`), readable load errors, `loader` dispatch for custom model types | A set of real Blockbench exports load with no warnings | 3-5 days |
| **Extensions** | | | |
| 4. OBJ model type | Our own OBJ/MTL parser (MIT, from the spec), as a `StaticBlockModel` behind `loader` | Betafront's crystal and ramp work through blockstates | 1.5 weeks |
| 5. Model TE renderers | Generic renderer for any model type, bone transforms from code | Chest-style lid opens from code with a Blockbench model | 1-1.5 weeks |
| 6. Animation | Bedrock keyframes (rot/pos/scale, linear + catmull-rom), loop/one-shot, controller API | Animated entity and tile entity from Blockbench | 2 weeks |
| 7. GLB / glTF | glTF 2.0 meshes and materials as a model type; node animations through the phase 6 player | A static GLB block and an animated GLB tile entity | 2-3 weeks (skinning later, separate) |

## Reuse, risks, open questions

Licence: MIT. Reused from Betafront: OBJ test models and tests (not the NeoForge-derived LGPL parser), texture-pack loading, smooth lighting (or BTA's own), KNOWLEDGE.md API table, Gradle build and release steps. Dropped: StationAPI build, `ObjModels.block(..., yRotation)` API.

Risks:
- BTA's model code can change in an update; keep ours behind one small adapter.
- Item display may need a mixin into the item renderer; check on phase 3 day 1.
- Meta is small, so complex states (fences) need `StateResolver`.
- Visual checks need Dave in game every phase.

Open questions:
- [x] Name, repo, licence, order: see knowledge/decisions.md
- [x] GLB textures: start with atlas ids in the model JSON, like OBJ; stitching embedded images may come later (see knowledge/decisions.md)
- [x] Let resource packs restyle vanilla BTA blocks via our blockstates: yes, through a mixin into `BlockModelDispatcher` (see knowledge/decisions.md)
