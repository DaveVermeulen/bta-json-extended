# Items

**Job:** item model JSON: `item/generated` and `item/handheld` layers, 3D item models, `display` per context, `gui_light`.

**Owns:** `core/.../item/` and its tests, `bta/.../item/`, item examples.

**Reads first:** `knowledge/bta/items.md`, `knowledge/formats/blockstates.md` (model JSON section only).

**Never touches:** mixins (if display transforms need one, Platform writes it), other features.
