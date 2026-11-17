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
