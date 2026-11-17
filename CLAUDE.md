# BTA JSON Extended: rules for agents

Read this whole file, then **only** your brief in [agents/](agents/README.md) and the knowledge files it lists. Don't read the rest of `knowledge/` unless your task needs it: [knowledge/index.md](knowledge/index.md) says which file answers what.

## The project in one paragraph

A BTA 8.0.1 mod (HalpLibe) that extends BTA's built-in JSON model system. BTA already loads vanilla-style block models (`org.useless.dragonfly`, part of the game jar). We add blockstates, item models, OBJ, model tile entity renderers and animation. The plan and phases are in [PLAN.md](PLAN.md).

## Layout (planned)

```
core/       Pure Java: parsing and logic for every format, plus JUnit tests. No game code, no HalpLibe.
bta/        The BTA mod: rendering, registration, mixins. Depends on core/.
knowledge/  Facts, split by topic. Start at knowledge/index.md.
agents/     One brief per agent role: what it owns, reads and must not touch.
```

## Rules for every agent

1. **Stay in your lane.** Edit only the folders your brief owns. Need something outside it (a mixin, a shared interface, a BTA hook)? Ask the owner in the PR or thread; don't add it yourself.
2. **One PR per step.** Small, focused, on its own branch `<role>/<short-name>`. Never push to `main`.
3. **Merging.** A PR is merged only after CI is green, the reviewer has passed it, and the maintainer has checked it in game. Claude may merge after the maintainer says it's checked.
4. **Never guess BTA or HalpLibe names.** Use the signatures in `knowledge/bta/`. Missing one? A Mac session checks it against the jar (see [knowledge/bta/reading-bta-code.md](knowledge/bta/reading-bta-code.md)) and adds it there.
5. **Tests.** Everything in `core/` has JUnit tests, using real Blockbench exports as fixtures where possible. Rendering changes add or update a dev-only example and an in-game checklist in the PR.
6. **Say what you verified:** built, tested, or seen in game. Agents can't see the game; never claim something looks right.
7. **Write down what you learn, in the right file.** A fact goes in the knowledge file for its topic (keep each under ~150 lines; split when it grows). A decision goes in [knowledge/decisions.md](knowledge/decisions.md), dated. A pitfall goes in [knowledge/pitfalls.md](knowledge/pitfalls.md). Update `knowledge/index.md` when you add a file.
8. **Don't reuse `org.useless.dragonfly` package or class names**: they are in the game jar.
9. **Keep `core/` free of game code.**
10. **Pause means stop.** If the maintainer says pause or stop: stop editing, leave files as they are, report `git status`.

## Where agents can run

| Work | Cloud session | Mac (Remote Control) |
| --- | --- | --- |
| `core/` code and tests | Yes | Yes |
| Docs, knowledge, briefs | Yes | Yes |
| Building `bta/`, dev client, reading BTA's code | No: the network blocks BTA, Fabric and Signalum hosts | Yes |
| Full build and tests | GitHub Actions on every push | Yes |
