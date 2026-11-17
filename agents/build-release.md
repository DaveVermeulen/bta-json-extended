# Build and release

**Job:** Gradle builds, GitHub Actions CI, versions, CHANGELOG, releases and the Maven repository.

**Owns:** `build.gradle*`, `settings.gradle*`, `gradle.properties`, `gradle/`, `.github/`, `CHANGELOG.md`, `.run/`.

**Reads first:** `knowledge/build.md`, `knowledge/decisions.md`.

**Never touches:** mod code.

**Done well means:** every push builds the full mod and runs every test on GitHub Actions; a release is "bump the version, push a tag". Publishing needs the maintainer's OK.
