# Decisions

Newest first. One line each, with the date and who decided.

| Date | Decision | By |
| --- | --- | --- |
| 2026-11-17 | GLB textures: start by referencing atlas textures by id in the model JSON (like OBJ); maybe stitch embedded images later | Dave |
| 2026-11-17 | Resource packs may restyle vanilla BTA blocks through our blockstates, using a mixin into `BlockModelDispatcher` (Platform owns it) | Dave |
| 2026-11-17 | Priority: match modern Minecraft first (blockstates, item models, model JSON), then extensions (OBJ, tile entity models, animation, GLB) | Dave |
| 2026-11-17 | Add GLB/glTF with animations as an extension after parity | Dave |
| 2026-11-17 | MIT licence; write our own OBJ parser instead of porting NeoForge's (LGPL) | Dave |
| 2026-11-17 | Agents each own one feature or lane; knowledge split into a tree of small topic files | Dave |
| 2026-11-17 | GitHub Actions for CI | Dave |
| 2026-11-17 | Work goes through PRs; Claude may merge after Dave has checked it in game | Dave |
| 2026-11-17 | Name "BTA JSON Extended", repo `bta-json-extended`, mod id `jsonextended`, package `io.github.tryrodave.jsonextended`; use `tryrodave`, not `davevermeulen`, in ids | Dave |
| 2026-11-17 | New repo; Betafront will be abandoned; BTA only (Babric has StationAPI) | Dave |
| 2026-11-17 | Build on BTA's built-in JSON models instead of writing our own loader | Plan |

