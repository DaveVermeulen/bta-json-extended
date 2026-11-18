# How BTA 8.0.1 loads JSON block models

Read from the 8.0.1 jar with Vineflower (see [reading-bta-code.md](reading-bta-code.md)). The packages are `org.useless.dragonfly.data.block.mojang` and `net.minecraft.client.render.block.model`.

## Load path, start to finish

1. **`BlockModelDispatcher.loadDataModel(String id)`** (public static).
   - Calls `BlockModelMojangData.Cache.loadModelData(mc.texturePackList, id)`.
   - On `null`, logs `Could not find model for id '<id>'!` and returns `Cache.getMissingModel()`: a full cube textured `minecraft:block/missing`.
2. **`Cache.loadModelData(TexturePackList, String id)`**. A `(TexturePackList, NamespaceID)` overload does the same.
   - A missing namespace means `minecraft`; more than one `:` throws `IllegalArgumentException`.
   - Returns the cached entry if there is one.
   - Otherwise reads `texturePackList.getResourceAsStream("/assets/<ns>/models/<path>.json")`, so texture packs override mod jars.
   - No file: logs `Could not locate block model data for id '<id>'!` and returns `null`.
3. **`Cache.loadFromStream(InputStream)`** (protected static, returns `BlockModelMojangData.Builder`).
   - This is where the JSON text is read: `gson.fromJson(new JsonReader(new InputStreamReader(stream)), Builder.class)`.
   - The stream is never closed.
4. **`Builder.Serializer.deserialize`** reads only these keys:
   - top level: `parent`, `ambientocclusion`, `renderlayer` (an int), `display`, `textures`, `elements`, `particles` (direction → int);
   - each element: `from`, `to`, `rotation`, `shade`, `light_emission`;
   - each face: `texture`, `uv`, `cullface`, `rotation`, `tintindex`.
5. **`Builder.build(texturePackList, id)`** turns that into `new BlockModelMojangData(...)`. The constructor loads the parent recursively through the same `Cache.loadModelData` and merges it:
   - the child's `elements` replace the parent's (only used when the child has none);
   - `textures`, `display` and `particles` are merged key by key, with the child winning;
   - `ambientocclusion` and `renderlayer` are inherited when the child leaves them out;
   - a child right-hand `display` with no left-hand one is copied to the left hand.
6. The result is cached in `Cache.modelDataCache`, a `Map<String, BlockModelMojangData>` keyed by `"<ns>:<path>"`.
7. **`BlockModelData.asModel()`** → `new StaticBlockModelMojang(data)` → `new CompiledBlockModelMojangData(data)`. This resolves textures and bakes vertices.
   - `BlockModelGeneric(block, BlockModelData)` calls `asModel()` once in its constructor.

## Caching

- Parsed data is cached per model id until `BlockModelDispatcher.reload()`. That calls `Cache.resetCache()` and rebuilds every dispatch, on texture pack changes and the HalpLibe reload.
- Parents share the cache, so a parent used by 50 models is parsed once.
- Quirk: the `String` overload checks the cache with the id exactly as given, but stores it under `minecraft:<path>`. An id written without a namespace is therefore parsed again on every call.
- `asModel()` is **not** cached: each `BlockModelGeneric` compiles its own `StaticBlockModelMojang`.

## Where to hook

| Goal | Best spot | Why |
| --- | --- | --- |
| Pre-process JSON (add `render_type`, `texture_size`, `gui_light`...) | `@WrapOperation` / `@Redirect` on the `gson.fromJson` call in `Cache.loadFromStream` | It's the only place the text is read, and it covers parents too. It doesn't know the model id. |
| Same, but needs the id | `@Inject(at = HEAD, cancellable = true)` on both `Cache.loadModelData` overloads | Has the id; we can open the stream ourselves and return our own data. Two overloads to cover. |
| Swap in our own `StaticBlockModel` | `@Inject(at = RETURN)` on `BlockModelMojangData.asModel()`, or our own `BlockModelGeneric(block, ourStaticModel)` for our blocks (no mixin) | `asModel()` is the one factory. Vanilla blocks get there from `BlockModelDispatcher.reload()`. |

Our own blocks need no mixin: build `BlockModelGeneric(block, StaticBlockModel)` with our model. Mixins are only needed to change how vanilla or other mods' JSON is read.

## `renderlayer`: what the int means

- `ChunkRendererMultiDraw` builds each chunk in **2 passes**, `renderPass` 0 then 1. A block is drawn only in the pass equal to `BlockModelDispatcher.getDispatch(block).renderLayer()`.
- That method has no metadata parameter, so it is one layer per block, not per variant.
- **0** is the default (`BlockModel.renderLayer()` and `DEFAULT_RENDER_LAYER`). It draws solid geometry *and* cut-out geometry: glass, leaves and flowers are all layer 0, and transparent pixels are cut away rather than blended.
- **1** is translucent (blended, drawn after pass 0). Vanilla sets it only for tinted glass, water and acid, via `BlockModelStandard.onRenderLayer(1)`.
- `StaticBlockModelMojang.renderLayer()` returns the compiled `renderlayer` (0 if absent); `BlockModelGeneric` passes it on.
- Mapping vanilla `render_type` onto it:
  - `minecraft:solid`, `minecraft:cutout` and `minecraft:cutout_mipped` → 0. BTA has no separate mipped pass.
  - `minecraft:translucent` → 1.
  - Anything else (e.g. `tripwire`) → 0, plus a warning.
- Our own `renderlayer` key keeps working as-is.

## Error handling (what BTA does today)

| Case | Result |
| --- | --- |
| Invalid JSON | `JsonSyntaxException` from `loadFromStream`. Nothing in `loadModelData` or `loadDataModel` catches it, so it escapes into `reload()`: expect a crash, not a log line. |
| Wrong type (e.g. `"uv": "x"`), or a face without `texture` | Gson or NullPointerException, same as invalid JSON |
| Missing model file | Error `Could not locate block model data for id '...'`; `loadDataModel` then uses the missing-texture cube |
| Missing **parent** | The same error for the parent id, then the child is built from its own keys alone. No inherited elements, so the model may be empty. |
| `#var` not defined | Warning `Texture for symbol '#x' is not assigned to any value!`; the face gets `BlockModelStandard.BLOCK_TEXTURE_UNASSIGNED` |
| `#a` → `#b` → `#a` loop | Caught `StackOverflowError`, error `Recursive texture lookup ...` |
| Texture id with no PNG | `TextureRegistry.getTexture` makes an icon on demand, and the atlas most likely fills it with its missing image (I didn't trace this into `AtlasStitcher.Box`). A path whose first folder isn't an atlas (not `block/`, `item/`, `particle/`, `art/`...) throws `IllegalArgumentException` ("Failed to find atlas"). |
| No `particle` texture | Warnings for each missing `#particle_<side>` and `#overlay` |
| Unknown keys (`render_type`, `gui_light`, `texture_size`, `credit`, `groups`, `name`...) | Silently ignored |

## `texture_size` and UVs

- BTA never reads `texture_size`. Face `uv` values are always divided by 16 and used as a 0..1 position inside the icon (`getSubIconU/V`).
- So the PNG's real resolution doesn't matter: a 32×32 texture with UVs 0..16 maps correctly.
- But Blockbench writes UVs in `texture_size` units: with `[32, 32]`, the UVs run 0..32. **We must scale `uv * 16 / texture_size` while pre-processing**, or those faces sample outside the icon.
- Faces without `uv` get default UVs from the element bounds (`16 - to`... in `Face`), the same as vanilla.

## `gui_light`

- Not read anywhere in 8.0.1.
- What BTA does instead:
  - Flat item icons: `ItemModel.renderGui` turns lighting **off**.
  - 3D block items: `ItemModelBlock.renderGui` leaves it **on**.
- So BTA behaves like vanilla `side` for blocks and `front` for flat items, decided by item kind, not by the JSON.
- Supporting `gui_light` means our own item model choosing which of those two to call. Details: [item-rendering.md](item-rendering.md).

## Mixin setup in `bta/`

- Not set up yet: `fabric.mod.json` has no `"mixins"` entry, there is no `*.mixins.json`, and `build.gradle.kts` has no mixin configuration.
- Fabric Loader already bundles Sponge Mixin, and Loom generates the refmap when it compiles mixin classes, so no extra dependency is needed.
- What's needed:
  1. `bta/src/main/resources/jsonextended.mixins.json` with `"package": "io.github.tryrodave.jsonextended.mixin"`, `"compatibilityLevel": "JAVA_17"` (`bta/` compiles with Java 17), and a `"client": [...]` list.
  2. `"mixins": ["jsonextended.mixins.json"]` in `fabric.mod.json`.
- BTA's game jar uses named mappings (`minecraft-merged-deobf`), so targets are the names shown above. Not verified here: whether BTA's Loom fork needs `loom { mixin { ... } }` settings or a refmap name. Check the first mixin with `./gradlew -p bta build` and a dev run.
