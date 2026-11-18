# Pitfalls

| Symptom | Cause and fix |
| --- | --- |
| Model lighting frozen at one time of day | World brightness baked into colour or shade. Pass the light index with `setLightmapCoord1i`; `setShade1i` is only the AO factor |
| `TextureRegistry.getTexture` throws | Id has no known atlas folder (e.g. no `block/`). Catch it, fall back to the particle texture, cache the result |
| Cached textures wrong after a pack change | Invalidate on `TextureRegistry.reloadGeneration` changes |
| BlockBuilder setting has no effect | `set...` returns a copy; use the return value |
| Block missing from creative menu | Needs `setCreativeInventoryPlacement(...)` |
| NPE `ModelHelper.blockModelDispatcher is null` at startup | `ModelHelper.setBlockModel` called from the `client` entrypoint, before HalpLibe builds the dispatcher. Add models in `ClientEvents.BLOCK_MODEL_RELOAD.listen(Key.of(MOD_ID), dispatcher -> dispatcher.addDispatch(block, model))` |
| "GLFW may only be used on the main thread" on macOS | `-XstartOnFirstThread` |
| "Java heap space" configuring the BTA build | 4 GB heap in root `gradle.properties`, then `gradlew --stop` |
| `Can't connect to Mojang API ... Player123` | Harmless |
| `babric-loom-extension` 1.17.4 `UnsupportedOperationException` | Use 1.17.5 (only if a Babric build ever returns) |
| Class clash with the game | Don't reuse `org.useless.dragonfly` names |

- **Log: 8 lines of `Model 'minecraft:block/missing' does not have an assigned '#particle_up' symbol!` per block.** Cause: every `BlockModelMojangData.Cache.getMissingModel().asModel()` compiles BTA's missing model again, and it has no textures map. Fix: call `Models.missing()` only when a model is actually missing and keep it in that model (`BlockstateModel` builds it lazily), so it is built at most once per broken block per reload (seen in Dave's log 2026-11-18, one block per blockstate block).
- **IntelliJ Run Client: "Resolution of the configuration ':bta:runtimeClasspath' was attempted without an exclusive lock".** Cause: IntelliJ's Kotlin coroutine debugger init script reads the jvm args of every task with the requested name in every build, so a root task named `runClient` pulled in `:bta:runClient` and Loom resolved its classpath unlocked (Gradle 9). Fix: the root task is `runBtaClient` (2026-11-18). Never name a root task the same as a Loom run task.
- **Block item huge or odd in hand and GUI.** Cause: the model JSON has no `display` and no parent with one, so BTA uses identity poses. Fix: give it vanilla `block/block`'s display poses (see `test_chest.json`).
