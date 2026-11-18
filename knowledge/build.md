# Building and testing

## Layout

| Path | What |
| --- | --- |
| `settings.gradle`, `build.gradle` | Root composite build for IntelliJ: `includeBuild 'core'`, `includeBuild 'bta'`, plus shortcut tasks |
| `core/` | Plain Java 17 build, JUnit 5. Only compiles and tests `core/src`; no game code |
| `bta/` | Loom build of the mod (Kotlin DSL, from Turnip-Labs/bta-example-mod via Betafront). Compiles `../core/src/main/java` into the mod jar |
| `bta/gradle/libs.versions.toml` | All versions (table below) |
| `.github/workflows/build.yml`, `release.yml` | CI and tagged releases |
| `.run/` | IntelliJ run configurations: Build All, Core Tests, Run Client |

## Commands

| Command | Does |
| --- | --- |
| `./gradlew buildAll` | Core tests + mod jar (`bta/build/libs/`) |
| `./gradlew testCore` or `./gradlew -p core test` | Core tests only (works in cloud sessions) |
| `./gradlew runBtaClient` or `./gradlew -p bta runClient` | Dev client with the dev-only test blocks (`FabricLoader.isDevelopmentEnvironment()`) |
| `./gradlew cleanAll` | Deletes both builds' output |

## CI (GitHub Actions)

`build.yml` runs on every pull request, every push to `main`, and by hand (`workflow_dispatch`). It installs Temurin 17 and 21 (21 runs Gradle, 17 is the toolchain via `-Porg.gradle.java.installations.fromEnv=JAVA_HOME_17_X64`), runs `-p core test`, then `-p bta build`, and uploads the mod jar as the `bta-json-extended` artifact (test reports on failure). A newer push to the same PR cancels the older run.

## Releases

1. Set `mod_version` in `bta/gradle.properties` (the jar is `bta-json-extended-<version>+8.0.1.jar`).
2. Rename `## Unreleased` in `CHANGELOG.md` to `## <version>` and start a new empty `## Unreleased`.
3. Merge that through a PR, then tag main: `git tag v<version> && git push origin v<version>`.

`release.yml` then checks the tag matches `mod_version`, takes that version's CHANGELOG section as the notes, runs the tests, builds, and opens a **draft** GitHub release with the jar and sources jar. Dave reviews the draft and presses Publish; nothing is published automatically.

Dependabot (`.github/dependabot.yml`) opens a monthly PR for GitHub Actions updates only. Mod and Gradle versions stay pinned by hand.

## Notes

- Gradle 9.5.1 (wrapper) needs Java 21+; the mod compiles for Java 17.
- BTA's macOS dev client needs `-XstartOnFirstThread` (set in `bta/build.gradle.kts`).
- Composite builds need a 4 GB Gradle heap: the root `gradle.properties` sets it. Run `./gradlew --stop` after a failed run.
- Claude cloud sessions can't reach the BTA, Fabric, Signalum or Mojang hosts: they can run `./gradlew -p core test` (Gradle and Maven Central work) but not build `bta/`. CI is the build check.
- Groovy DSL in `core/`: use `prop = value`; the space syntax is deprecated in Gradle 9.
- Game logs from the dev client: `bta/run/logs/latest.log`. Only one dev client at a time.

## Versions and repositories (from Betafront's working BTA build, 2026-11-17)

| Thing | Value |
| --- | --- |
| BTA | `8.0.1`, channel `release`, manifest `https://downloads.betterthanadventure.net/bta-client/release/v8.0.1/manifest.json` (Loom `customMinecraftMetadata`, `minecraft("::8.0.1")`) |
| Fabric loader (BTA fork) | `net.fabricmc:fabric-loader:0.18.4-bta.11` |
| HalpLibe | `turniplabs:halplibe:6.2.0+8.0.1` |
| ModMenu | `turniplabs:modmenu-bta:5.0.2` |
| legacy-lwjgl3 | `com.github.Better-than-Adventure:legacy-lwjgl3:1.0.6` |
| LWJGL | `3.3.3` (lwjgl, glfw, openal, opengl, stb), compileOnly |
| JOML | `1.10.8`, joml-primitives `1.10.0` |
| slf4j-api | `2.0.17` |
| Loom | `net.fabricmc.fabric-loom` `1.15-SNAPSHOT` |
| Java | compile 17, Gradle on 21 |

Maven repositories: `mavenCentral()`, `https://maven.fabricmc.net/`, `https://maven.danygames2014.net/signalum`, `https://maven.thesignalumproject.net/infrastructure`, `.../releases`, `.../nightly`. Plugin management also needs Fabric and Signalum infrastructure. Betafront's `bta/build.gradle.kts`, `settings.gradle.kts` and `gradle/libs.versions.toml` (from Turnip-Labs/bta-example-mod) are a working template; exclude `net.minecraft:launchwrapper`.
