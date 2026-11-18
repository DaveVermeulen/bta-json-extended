package io.github.tryrodave.jsonextended.blockstate;

import java.util.List;
import java.util.Map;

/**
 * What a blockstate draws for one state: the decoded properties and the variants, in order. When the state can't
 * be drawn (metadata pointing past a property's values, no matching variant) {@link #error()} says why and the
 * renderer draws the missing model.
 */
public final class Selection {
    private final Map<String, String> properties;
    private final List<Choice> choices;
    private final String error;

    private Selection(Map<String, String> properties, List<Choice> choices, String error) {
        this.properties = properties;
        this.choices = choices;
        this.error = error;
    }

    static Selection of(Map<String, String> properties, List<Choice> choices) {
        return new Selection(Map.copyOf(properties), List.copyOf(choices), null);
    }

    static Selection missing(Map<String, String> properties, String error) {
        return new Selection(Map.copyOf(properties), List.of(), error);
    }

    /** Every property's value; empty values for properties that couldn't be decoded. */
    public Map<String, String> properties() {
        return properties;
    }

    /** Variants to draw, in file order. Each draws one part, picked per position when weighted. */
    public List<Choice> choices() {
        return choices;
    }

    public boolean missing() {
        return error != null;
    }

    /** Why this state draws the missing model, or null. */
    public String error() {
        return error;
    }

    @Override
    public String toString() {
        return missing() ? "missing: " + error : properties + " -> " + choices;
    }
}
