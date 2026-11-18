# Blockstate fixtures

For the phase 1 blockstate parser. See `knowledge/formats/blockstates.md` for the format.

| Folder | What |
| --- | --- |
| `../assets/jsonextended/` | Byte-identical copies of the dev test blocks' blockstates and models from `bta/src/main/resources/assets/jsonextended/`. Edit the `bta/` files, then copy them here; `BlockstateFixturesTest` fails if they differ |
| `cases/<block>.json` | Expected results per blockstate: `meta` (and `resolved`, the StateResolver's values) in; decoded `properties` and the `parts` drawn, in order, out. `"parts": "missing"` means the missing model plus one log line |
| `conditions/` | A multipart file covering `a\|b` values, several keys, `OR` and `AND`; its table is `cases/multipart_conditions.json` |
| `invalid/` | Files that must fail to load; `cases/invalid.json` says why each one fails |

Each part lists the vanilla `x`/`y` in degrees and `bta`: the same rotation as `renderAttached` quarter turns `[rotX, rotY, rotZ] = [-x/90, -y/90, 0] mod 4`, X applied first. That mapping is derived from BTA's code and was confirmed in game with the test blocks (2026-11-18).

Weighted variants (`cases/test_weighted.json`) list `candidates` with weights instead of one result: the pick must be stable per block position.
