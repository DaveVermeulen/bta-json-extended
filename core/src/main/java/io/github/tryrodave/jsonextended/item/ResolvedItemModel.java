package io.github.tryrodave.jsonextended.item;

import java.util.List;
import java.util.Map;

/** An item model with its parents merged: what the renderer needs. */
public final class ResolvedItemModel {
    /** How the item is drawn. */
    public enum Style {
        /** Flat layered sprite, {@code item/generated} poses. */
        GENERATED,
        /** Flat layered sprite held like a tool, {@code item/handheld} poses. */
        HANDHELD,
        /** Flat layered sprite held like a fishing rod. */
        HANDHELD_ROD,
        /** 3D model from {@code elements}. */
        MODEL
    }

    private final String id;
    private final Style style;
    private final List<String> layers;
    private final Map<String, String> textures;
    private final Map<String, DisplayTransform> display;
    private final String guiLight;
    private final String elementsFrom;
    private final List<String> warnings;

    ResolvedItemModel(String id, Style style, List<String> layers, Map<String, String> textures,
                      Map<String, DisplayTransform> display, String guiLight, String elementsFrom, List<String> warnings) {
        this.id = id;
        this.style = style;
        this.layers = List.copyOf(layers);
        this.textures = Map.copyOf(textures);
        this.display = Map.copyOf(display);
        this.guiLight = guiLight;
        this.elementsFrom = elementsFrom;
        this.warnings = List.copyOf(warnings);
    }

    public String id() {
        return id;
    }

    public Style style() {
        return style;
    }

    public boolean flat() {
        return style != Style.MODEL;
    }

    /** Texture ids of {@code layer0}, {@code layer1}, ... for flat items; empty for 3D models. */
    public List<String> layers() {
        return layers;
    }

    /** Every texture variable with references followed, as full ids. */
    public Map<String, String> textures() {
        return textures;
    }

    /** Display entries the files set, by context; contexts not here use the renderer's defaults. */
    public Map<String, DisplayTransform> display() {
        return display;
    }

    /**
     * The pose for a context: the files' own entry, else (for a left hand) the right hand's entry, else null for
     * the renderer's default.
     */
    public DisplayTransform display(String context) {
        DisplayTransform t = display.get(context);
        if (t == null && context.endsWith("_lefthand"))
            t = display.get(context.replace("_lefthand", "_righthand"));
        return t;
    }

    /** {@code front} (flat items by default) or {@code side} (3D models by default). */
    public String guiLight() {
        return guiLight;
    }

    /** For 3D models: the id of the nearest file with {@code elements}; null for flat items. */
    public String elementsFrom() {
        return elementsFrom;
    }

    /** Ignored keys and similar, one line each, for the log. */
    public List<String> warnings() {
        return warnings;
    }

    @Override
    public String toString() {
        return id + "[" + style + (flat() ? " " + layers : " from " + elementsFrom) + "]";
    }
}
