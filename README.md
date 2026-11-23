# BTA JSON Extended

<p align="center">
  <img src=".github/icon-transparent.png" alt="BTA JSON Extended icon" width="128">
</p>

Modern-Minecraft-style models for **Better Than Adventure 8.0.1** mods, on [HalpLibe](https://github.com/Turnip-Labs/bta-halplibe). BTA already reads vanilla-style JSON block models; this library adds the rest of the model system:

- **Blockstate JSON**: `variants` and `multipart`, `x`/`y`/`uvlock`, weighted models, named properties from metadata bits, and neighbour-dependent state (stairs, fences).
- **Item model JSON**: generated, handheld and rod items with layered textures, `display` poses, and 3D item models.
- **Model JSON polish**: Blockbench exports load quietly, `render_type: translucent`, `gui_light`, and one readable log line per problem instead of a crash.
- **OBJ models**: Wavefront `.obj` files as block models through `"loader": "jsonextended:obj"`, with NeoForge's keys, tinting and smooth lighting.
- **glTF and GLB models**: `.glb` and `.gltf` files as block models through `"loader": "jsonextended:gltf"`, with node transforms and materials, drawn like OBJ; their own animations play on tile entities.
- **Tile entity models**: a Blockbench model drawn every frame, with its groups moved from code (a chest lid, a door).
- **Animations**: Blockbench's `.animation.json` on tile entities and on geo mobs, with crossfades and walk/idle blending.

It's a library for mod authors: on its own it adds nothing to the game.

**New here?** The [documentation](docs/README.md) covers installing, every file format, registering from code, the example content and known limitations.

> **AI disclaimer:** most of this mod's code and documentation was written with the help of Claude, an AI assistant by Anthropic, and reviewed by the author. Every change is built by CI and checked in game by the maintainer before it is merged. Please report anything that looks wrong as an issue.

## Status

Version 0.1.0 is released (blockstates, item models, model JSON polish). Main also has OBJ models, tile entity models and animations, which will be in the next release. GLB/glTF models are in progress: static block models are merged, node animations on tile entities are in review. See [CHANGELOG.md](CHANGELOG.md) and [PLAN.md](PLAN.md).

## A quick look

`assets/mymod/blockstates/lamp.json`:

```json
{
  "properties": {"facing": {"bits": "0-1", "values": ["south", "west", "north", "east"]}},
  "variants": {
    "facing=north": {"model": "mymod:block/lamp"},
    "facing=east":  {"model": "mymod:block/lamp", "y": 90},
    "facing=south": {"model": "mymod:block/lamp", "y": 180},
    "facing=west":  {"model": "mymod:block/lamp", "y": 270}
  }
}
```

In your client entrypoint:

```java
ClientEvents.BLOCK_MODEL_RELOAD.listen(Key.of(MOD_ID), dispatcher ->
        dispatcher.addDispatch(MyBlocks.LAMP, Blockstates.model(MyBlocks.LAMP, "mymod:lamp")));
ClientEvents.ITEM_MODEL_RELOAD.listen(Key.of(MOD_ID), dispatcher ->
        dispatcher.addDispatch(JsonItems.model(MyItems.RUBY, "mymod:item/ruby")));
```

More in [Getting started](docs/getting-started.md).

## Dependencies

### To play

| Dependency | Version | Notes |
| --- | --- | --- |
| Better Than Adventure | 8.0.1 | |
| Fabric Loader (BTA fork) | 0.18.4-bta.11 or newer | Comes with the BTA Babric instance |
| [HalpLibe](https://github.com/Turnip-Labs/bta-halplibe) | 6.2.0+8.0.1 or newer | Required |
| [ModMenu](https://github.com/Turnip-Labs/ModMenu) | 5.0.2 | Optional |
| Java | 17 or newer | |

### To build

| Dependency | Version |
| --- | --- |
| JDK to run Gradle | 21 or newer |
| JDK to compile | 17 (Gradle finds or downloads it) |
| Gradle | 9.5.1 (wrapper included) |
| Fabric Loom | 1.15-SNAPSHOT |
| JUnit Jupiter (core tests) | 5.11.4 |

## Repository layout

| Folder | What it is |
| --- | --- |
| `core/` | The parsers and logic for every format (blockstates, item models, model checks, OBJ, poses, animation), with JUnit tests. Plain Java, no game code. Compiled into the mod jar |
| `bta/` | The BTA mod (`bta-json-extended-<version>+8.0.1.jar`): rendering, registration, the dev-only examples |
| `docs/` | The documentation for mod developers |
| `knowledge/`, `agents/` | Notes for contributors and AI agents: verified BTA API names, decisions, pitfalls, and one brief per role |

## Building

### In IntelliJ IDEA

Open the repository folder (not a subfolder) and let IntelliJ import the Gradle project. Set the Gradle JVM to Java 21 or newer (Settings > Build, Execution, Deployment > Build Tools > Gradle). Then pick a run configuration:

- **Run Client**: the BTA dev client with all the [example content](docs/examples.md)
- **Core Tests**: the `core/` unit tests
- **Build All**: tests plus the mod jar

Use Run, not Debug, for Run Client: the debugger's Kotlin coroutine agent breaks the Gradle run (see [knowledge/pitfalls.md](knowledge/pitfalls.md)).

### From a terminal

```
./gradlew buildAll          # core tests plus the mod jar in bta/build/libs
./gradlew runBtaClient      # dev client (same as ./gradlew -p bta runClient)
./gradlew testCore          # core tests (same as ./gradlew -p core test)
./gradlew cleanAll          # deletes both builds' output
```

GitHub Actions runs the core tests and builds the jar on every pull request. Tagging `v<version>` builds a draft release; see [knowledge/build.md](knowledge/build.md).

## Contributing

Contributors and AI agents: start with [CLAUDE.md](CLAUDE.md), then your role's brief in [agents/](agents/README.md) and [knowledge/index.md](knowledge/index.md).

## Licence

MIT, see [LICENSE](LICENSE). The OBJ parser is our own, written from the format specs; its JSON keys follow NeoForge's OBJ loader so models carry over.
