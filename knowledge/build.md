# Building and testing

Status: the build doesn't exist yet. Build and release agent: fill this in when it does.

- Gradle needs Java 21+; the mod compiles for Java 17. Planned layout (as Betafront): separate Gradle builds for `core/` and `bta/`, joined by a root composite build.
- BTA's macOS dev client needs `-XstartOnFirstThread`.
- Composite builds need a 4 GB Gradle heap in the root `gradle.properties`; run `gradlew --stop` after a failed run.
- Claude cloud sessions can't reach the BTA, Fabric, Signalum or Mojang hosts: they can only compile `core/` with plain `javac` and a JUnit console jar from Maven Central.
- GitHub Actions builds everything on every push and PR.
- Game logs from the dev client: `bta/run/logs/latest.log`. Only one dev client at a time.
