# Platform (rendering lead)

**Job:** the BTA side every feature builds on. Owns how models plug into BTA, lighting, registration and reloads, and every mixin.

**Owns:** `bta/src/main/java/io/github/tryrodave/jsonextended/platform/`, `bta/src/main/java/.../mixin/`, `bta/src/main/resources/*.mixins.json`, `core/src/main/java/io/github/tryrodave/jsonextended/api/` (shared interfaces).

**Reads first:** `knowledge/bta/rendering.md`, `knowledge/bta/registration.md`, `knowledge/bta/reading-bta-code.md`, `knowledge/pitfalls.md`.

**Never touches:** format parsers in `core/` (blockstates, obj, items, animation), feature folders in `bta/`.

**Done well means:** feature agents never need to read BTA's code themselves. Every BTA call they need is wrapped or documented in `knowledge/bta/`. Mixins are a last resort; prefer HalpLibe hooks and public BTA API.
