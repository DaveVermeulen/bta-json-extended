# Installing

The library is client side: it only changes how things are drawn. A mod that uses it for tile entities or mobs also registers those on the common side, which is that mod's own code.

## For players

| You need | Version |
| --- | --- |
| Better Than Adventure | 8.0.1 |
| Fabric Loader (BTA fork) | 0.18.4-bta.11 or newer (comes with the BTA Babric instance) |
| [HalpLibe](https://github.com/Turnip-Labs/bta-halplibe) | 6.2.0+8.0.1 or newer |
| Java | 17 or newer |

Put `bta-json-extended-<version>+8.0.1.jar` and HalpLibe in the instance's `mods` folder. [ModMenu](https://github.com/Turnip-Labs/ModMenu) is optional.

## For mod developers

The library isn't published to a Maven repository yet. Download the jar from the [releases](https://github.com/DaveVermeulen/bta-json-extended/releases), or build it (`./gradlew buildAll`, see the [main README](../README.md#building)), and put it in your mod project, for example in `libs/`.

You call it from code, so it's a compile dependency:

```kotlin
// build.gradle.kts
dependencies {
    implementation(files("libs/bta-json-extended-1.0.0+8.0.1.jar"))
}
```

Add it to your `fabric.mod.json`, so players get a clear message when it's missing:

```json
"depends": {
  "jsonextended": "*"
}
```

Everything lives in the package `io.github.tryrodave.jsonextended`. The `core` classes (parsers, poses, animation sampling) have no game code and work in plain unit tests; the rest needs the game.
