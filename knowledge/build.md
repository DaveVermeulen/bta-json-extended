# Building and testing

Status: the build doesn't exist yet. Build and release agent: fill this in when it does.

- Gradle needs Java 21+; the mod compiles for Java 17. Planned layout (as Betafront): separate Gradle builds for `core/` and `bta/`, joined by a root composite build.
- BTA's macOS dev client needs `-XstartOnFirstThread`.
- Composite builds need a 4 GB Gradle heap in the root `gradle.properties`; run `gradlew --stop` after a failed run.
- Claude cloud sessions can't reach the BTA, Fabric, Signalum or Mojang hosts: they can only compile `core/` with plain `javac` and a JUnit console jar from Maven Central.
- GitHub Actions builds everything on every push and PR.
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
