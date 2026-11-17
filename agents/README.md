# Agent roles

Each agent has one job. A brief says what it **owns** (may edit), what it **reads** first, and what it **never touches**. Broad knowledge of rendering lives with Platform; feature agents ask Platform instead of learning all of BTA themselves. This keeps every agent's context small.

| Role | Brief | Job |
| --- | --- | --- |
| Platform | [platform.md](platform.md) | BTA integration everyone builds on: model interface, lighting, registration, all mixins |
| Blockstates | [blockstates.md](blockstates.md) | Blockstate JSON, meta properties, neighbour hook |
| Items | [items.md](items.md) | Item model JSON, display transforms |
| OBJ | [obj.md](obj.md) | OBJ/MTL parser and the OBJ model type |
| Tile entities | [tile-entities.md](tile-entities.md) | Model-driven tile entity renderers |
| Animation | [animation.md](animation.md) | Blockbench animations and the player |
| Build and release | [build-release.md](build-release.md) | Gradle, GitHub Actions, versions, changelog, releases |
| Reviewer | [reviewer.md](reviewer.md) | Reviews PRs; never writes features |

## How roles work together

- **Shared interfaces** (anything in `core/.../api/` and the `bta/` adapter) belong to Platform. A feature agent that needs a change opens an issue or asks in its PR.
- **Parallel work** only when owned folders don't overlap. Only one agent runs the BTA dev client at a time.
- **Handover:** when a step is done, the agent adds what the next one needs to the knowledge file for its topic, not to a chat.
