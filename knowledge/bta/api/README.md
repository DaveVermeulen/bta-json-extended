# BTA 8.0.1 and HalpLibe API signatures

`javap` output (public, `-protected` for `org.useless.dragonfly`) from the real jars, made on the maintainer's Mac on 2026-11-17 so cloud agents can check names without the game. These are signatures only, no decompiled code.

| File | Classes |
| --- | --- |
| [dragonfly.txt](dragonfly.txt) | Every `org.useless.dragonfly` class: model data, `StaticBlockModel`, geo entity models, `DisplayPos` |
| [block-models.txt](block-models.txt) | `BlockModel`, `BlockModelStandard`, `BlockModelGeneric` (+ Rotatable, Axis, FullyRotatable, Stairs, Fence), `BlockModelDispatcher`, `RenderBlocks`, `LightingCache`, `BlockColor*` |
| [items.txt](items.txt) | `ItemModel`, `ItemModelStandard`, `ItemModelBlock`, `ItemModelDispatcher`, `ItemRenderer`, `Item`, `ItemStack` |
| [tile-entities.txt](tile-entities.txt) | `TileEntityRenderer`, `TileEntityRenderDispatcher`, `TileEntityRendererSign`, `EntityRenderer`, `MobRenderer`, `EntityRendererDispatcher`, `TileEntity` |
| [render-core.txt](render-core.txt) | `TessellatorGeneral`, `Tessellator`, `TextureRegistry`, `IconCoordinate`, `TexturePackList`, `LightIndexHelper`, `Side`, `Direction`, `Axis`, `NamespaceID`, `GameSettings` |
| [world-blocks.txt](world-blocks.txt) | `Block`, `BlockLogic`, `BlockLogicRotatable`, `WorldSource`, `TilePos`, `TilePosc` |
| [halplibe.txt](halplibe.txt) | `HalpLibe`, `ClientEvents`, `ModelHelper`, `ModelEntrypoint`, `BlockBuilder`, `ItemBuilder`, `Key`, `SortedBaseEvent` |

Parameter names are lost in `javap`; the knowledge files name them where it matters. A class you need that isn't here: a Mac session adds it (see [../reading-bta-code.md](../reading-bta-code.md)).
