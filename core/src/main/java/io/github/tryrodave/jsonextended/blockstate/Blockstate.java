package io.github.tryrodave.jsonextended.blockstate;

import io.github.tryrodave.jsonextended.json.Json;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * A parsed blockstate file ({@code assets/<ns>/blockstates/<block>.json}): our {@code properties} map plus
 * vanilla 1.21 {@code variants} or {@code multipart}. {@link #select} turns metadata (and the values a
 * {@code StateResolver} supplies) into the parts to draw, cached per state.
 * <p>
 * Everything wrong with the file is caught by {@link #parse}; the only errors left at draw time are states
 * the file can't describe (metadata past a property's values, no matching variant), which give a missing
 * selection and one warning.
 */
public final class Blockstate {
    private static final Set<String> TOP_KEYS = Set.of("properties", "variants", "multipart");
    private static final Set<String> PROPERTY_KEYS = Set.of("bits", "values");
    private static final Set<String> VARIANT_KEYS = Set.of("model", "x", "y", "uvlock", "weight");
    private static final Set<String> CASE_KEYS = Set.of("when", "apply");

    private final String source;
    private final Map<String, Property> properties;
    private final List<Property> metaProperties;
    private final List<Property> resolverProperties;
    /** Variants mode: keys (as property maps) and their choices, in file order. Null in multipart mode. */
    private final List<Map<String, String>> variantKeys;
    private final List<Choice> variantChoices;
    /** Multipart mode: conditions (null = always) and their choices. Null in variants mode. */
    private final List<Condition> caseConditions;
    private final List<Choice> caseChoices;

    private final Map<Integer, Selection> cache = new ConcurrentHashMap<>();
    private final Set<String> warned = ConcurrentHashMap.newKeySet();
    private volatile Consumer<String> warnings = message -> {};

    private Blockstate(String source, Map<String, Property> properties,
                       List<Map<String, String>> variantKeys, List<Choice> variantChoices,
                       List<Condition> caseConditions, List<Choice> caseChoices) {
        this.source = source;
        this.properties = Collections.unmodifiableMap(properties);
        List<Property> meta = new ArrayList<>(), resolver = new ArrayList<>();
        for (Property p : properties.values())
            (p.fromMeta() ? meta : resolver).add(p);
        this.metaProperties = List.copyOf(meta);
        this.resolverProperties = List.copyOf(resolver);
        this.variantKeys = variantKeys;
        this.variantChoices = variantChoices;
        this.caseConditions = caseConditions;
        this.caseChoices = caseChoices;
    }

    /**
     * Reads a blockstate file.
     *
     * @param source names the file in error messages, e.g. {@code jsonextended:test_stairs}
     * @throws BlockstateException naming the file and the key, for anything the file gets wrong
     */
    public static Blockstate parse(String source, String json) {
        Object root;
        try {
            root = Json.parse(json);
        } catch (IllegalArgumentException e) {
            throw new BlockstateException(source + ": " + e.getMessage());
        }
        return new Parser(source).file(root);
    }

    /** Where this file came from, as given to {@link #parse}. */
    public String source() {
        return source;
    }

    /** Declared properties, in file order. */
    public Map<String, Property> properties() {
        return properties;
    }

    /** Properties a {@code StateResolver} must supply; empty for blocks fully described by metadata. */
    public List<Property> resolverProperties() {
        return resolverProperties;
    }

    public boolean needsResolver() {
        return !resolverProperties.isEmpty();
    }

    /** Every model id the file names, in file order, without repeats. */
    public List<String> models() {
        List<String> models = new ArrayList<>();
        for (Choice choice : variantChoices != null ? variantChoices : caseChoices)
            for (Part part : choice.options())
                if (!models.contains(part.model()))
                    models.add(part.model());
        return models;
    }

    /**
     * The value a metadata property has in {@code meta}, or null when the property isn't read from metadata or
     * the bits point past its values. For resolvers that look at neighbours of the same block.
     */
    public String metaValue(String property, int meta) {
        Property p = properties.get(property);
        if (p == null || !p.fromMeta())
            return null;
        int index = p.indexIn(meta);
        return index < p.values().size() ? p.values().get(index) : null;
    }

    /** Receives one line per state that draws the missing model (each distinct problem once). */
    public void onWarning(Consumer<String> sink) {
        this.warnings = sink == null ? message -> {} : sink;
    }

    /** {@link #select(int, Map)} for blocks with no resolver properties. */
    public Selection select(int meta) {
        return select(meta, Map.of());
    }

    /**
     * The parts to draw for a state. Cached: the same meta and resolved values return the same object.
     *
     * @param meta     block metadata (only the low 4 bits are read)
     * @param resolved values for {@link #resolverProperties()}; other entries are ignored
     */
    public Selection select(int meta, Map<String, String> resolved) {
        meta &= (1 << Property.META_BITS) - 1;
        int key = meta;
        int radix = 1 << Property.META_BITS;
        for (Property p : resolverProperties) {
            String value = resolved.get(p.name());
            int index = value == null ? -1 : p.values().indexOf(value);
            if (index < 0) {
                String error = value == null
                        ? "property '" + p.name() + "' has no bits and no StateResolver value"
                        : "'" + value + "' is not one of " + p.name() + "'s values";
                return warn(Selection.missing(Map.of(), error));
            }
            key += index * radix;
            radix *= p.values().size();
        }
        Selection cached = cache.get(key);
        if (cached != null)
            return cached;
        Selection computed = compute(meta, resolved);
        Selection raced = cache.putIfAbsent(key, computed);
        if (raced != null)
            return raced;
        return warn(computed);
    }

    private Selection warn(Selection selection) {
        if (selection.missing()) {
            String line = source + ": " + selection.error();
            if (warned.add(line))
                warnings.accept(line);
        }
        return selection;
    }

    private Selection compute(int meta, Map<String, String> resolved) {
        Map<String, String> state = new LinkedHashMap<>();
        for (Property p : properties.values()) {
            if (p.fromMeta()) {
                int index = p.indexIn(meta);
                if (index >= p.values().size())
                    return Selection.missing(Map.of(), p.name() + " index " + index + " has no value");
                state.put(p.name(), p.values().get(index));
            } else {
                state.put(p.name(), resolved.get(p.name()));
            }
        }
        List<Choice> drawn = new ArrayList<>();
        if (variantKeys != null) {
            for (int i = 0; i < variantKeys.size(); i++) {
                if (matches(variantKeys.get(i), state)) {
                    drawn.add(variantChoices.get(i));
                    return Selection.of(state, drawn);
                }
            }
            return Selection.missing(state, "no variant matches " + describe(state));
        }
        for (int i = 0; i < caseConditions.size(); i++) {
            Condition when = caseConditions.get(i);
            if (when == null || when.test(state))
                drawn.add(caseChoices.get(i));
        }
        return Selection.of(state, drawn);
    }

    private static boolean matches(Map<String, String> key, Map<String, String> state) {
        for (Map.Entry<String, String> e : key.entrySet())
            if (!e.getValue().equals(state.get(e.getKey())))
                return false;
        return true;
    }

    private static String describe(Map<String, String> state) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : state.entrySet()) {
            if (sb.length() > 0)
                sb.append(',');
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.length() == 0 ? "\"\"" : sb.toString();
    }

    @Override
    public String toString() {
        return "Blockstate[" + source + "]";
    }

    /** A multipart {@code when}. */
    interface Condition {
        boolean test(Map<String, String> state);
    }

    /** Turns the JSON tree into a {@link Blockstate}, checking everything on the way. */
    private static final class Parser {
        private final String source;
        private final Map<String, Property> properties = new LinkedHashMap<>();

        Parser(String source) {
            this.source = source;
        }

        Blockstate file(Object root) {
            Map<String, Object> file = object(root, "the file");
            unknownKeys(file, TOP_KEYS, "");
            boolean hasVariants = file.containsKey("variants"), hasMultipart = file.containsKey("multipart");
            if (hasVariants && hasMultipart)
                throw error("", "a file has either variants or multipart, not both");
            if (!hasVariants && !hasMultipart)
                throw error("", "a file needs variants or multipart");
            if (file.containsKey("properties"))
                properties(object(file.get("properties"), "properties"));

            if (hasVariants) {
                Map<String, Object> variants = object(file.get("variants"), "variants");
                List<Map<String, String>> keys = new ArrayList<>();
                List<Choice> choices = new ArrayList<>();
                for (Map.Entry<String, Object> e : variants.entrySet()) {
                    String path = "variants.\"" + e.getKey() + "\"";
                    keys.add(variantKey(e.getKey(), path));
                    choices.add(choice(e.getValue(), path));
                }
                if (keys.isEmpty())
                    throw error("variants", "variants needs at least one entry");
                return new Blockstate(source, properties, List.copyOf(keys), List.copyOf(choices), null, null);
            }

            List<Object> cases = list(file.get("multipart"), "multipart");
            List<Condition> conditions = new ArrayList<>();
            List<Choice> choices = new ArrayList<>();
            for (int i = 0; i < cases.size(); i++) {
                String path = "multipart[" + i + "]";
                Map<String, Object> c = object(cases.get(i), path);
                unknownKeys(c, CASE_KEYS, path);
                if (!c.containsKey("apply"))
                    throw error(path, "every multipart entry needs apply");
                conditions.add(c.containsKey("when") ? condition(c.get("when"), path + ".when") : null);
                choices.add(choice(c.get("apply"), path + ".apply"));
            }
            return new Blockstate(source, properties, null, null, conditions, choices);
        }

        private void properties(Map<String, Object> json) {
            Map<Integer, String> bitOwner = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : json.entrySet()) {
                String name = e.getKey(), path = "properties." + name;
                if (name.isEmpty() || name.matches(".*[,=|].*") || name.equals("OR") || name.equals("AND"))
                    throw error(path, "'" + name + "' can't be a property name");
                Map<String, Object> p = object(e.getValue(), path);
                unknownKeys(p, PROPERTY_KEYS, path);
                List<String> values = new ArrayList<>();
                for (Object v : list(p.get("values"), path + ".values")) {
                    String value = scalar(v, path + ".values");
                    if (value.isEmpty() || value.matches(".*[,=|].*"))
                        throw error(path + ".values", "'" + value + "' can't be a value");
                    if (values.contains(value))
                        throw error(path + ".values", "'" + value + "' is listed twice");
                    values.add(value);
                }
                if (values.isEmpty())
                    throw error(path + ".values", "a property needs at least one value");

                int first = 0, count = 0;
                if (p.containsKey("bits")) {
                    String bits = scalar(p.get("bits"), path + ".bits");
                    int[] range = bitRange(bits, path + ".bits");
                    first = range[0];
                    count = range[1] - range[0] + 1;
                    for (int bit = range[0]; bit <= range[1]; bit++) {
                        String owner = bitOwner.putIfAbsent(bit, name);
                        if (owner != null)
                            throw error(path + ".bits", owner + " and " + name + " both use bit " + bit);
                    }
                    if (values.size() > 1 << count)
                        throw error(path + ".values", values.size() + " values don't fit in " + count
                                + (count == 1 ? " bit" : " bits"));
                }
                properties.put(name, new Property(name, first, count, values));
            }
        }

        private int[] bitRange(String bits, String path) {
            String[] ends = bits.split("-", -1);
            if (ends.length > 2)
                throw error(path, "bits is one bit (\"2\") or a range (\"0-1\")");
            int[] range = new int[2];
            try {
                range[0] = Integer.parseInt(ends[0].trim());
                range[1] = Integer.parseInt(ends[ends.length - 1].trim());
            } catch (NumberFormatException e) {
                throw error(path, "bits is one bit (\"2\") or a range (\"0-1\")");
            }
            if (range[0] < 0 || range[1] < 0 || range[0] >= Property.META_BITS || range[1] >= Property.META_BITS)
                throw error(path, "BTA block metadata has 4 bits (0-3)");
            if (range[1] < range[0])
                throw error(path, "a bit range goes from low to high");
            return range;
        }

        private Map<String, String> variantKey(String key, String path) {
            Map<String, String> pairs = new LinkedHashMap<>();
            if (key.isEmpty())
                return pairs;
            for (String pair : key.split(",", -1)) {
                int eq = pair.indexOf('=');
                if (eq < 0)
                    throw error(path, "variant keys look like \"facing=north,half=top\" (or \"\" for every state)");
                String name = pair.substring(0, eq).trim(), value = pair.substring(eq + 1).trim();
                Property p = properties.get(name);
                if (p == null)
                    throw error(path, "variant key names property '" + name + "', which the file doesn't declare");
                if (!p.values().contains(value))
                    throw error(path, "'" + value + "' is not one of " + name + "'s values");
                if (pairs.put(name, value) != null)
                    throw error(path, "variant key names " + name + " twice");
            }
            return pairs;
        }

        private Condition condition(Object json, String path) {
            Map<String, Object> when = object(json, path);
            if (when.size() == 1 && (when.containsKey("OR") || when.containsKey("AND"))) {
                boolean or = when.containsKey("OR");
                String listPath = path + (or ? ".OR" : ".AND");
                List<Object> items = list(when.get(or ? "OR" : "AND"), listPath);
                if (items.isEmpty())
                    throw error(listPath, (or ? "OR" : "AND") + " needs at least one condition");
                List<Condition> parts = new ArrayList<>();
                for (int i = 0; i < items.size(); i++)
                    parts.add(condition(items.get(i), listPath + "[" + i + "]"));
                return or
                        ? state -> parts.stream().anyMatch(c -> c.test(state))
                        : state -> parts.stream().allMatch(c -> c.test(state));
            }
            if (when.containsKey("OR") || when.containsKey("AND"))
                throw error(path, "OR and AND must be the only key in their object");
            Map<String, Set<String>> required = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : when.entrySet()) {
                String name = e.getKey(), keyPath = path + "." + name;
                Property p = properties.get(name);
                if (p == null)
                    throw error(keyPath, "condition names property '" + name + "', which the file doesn't declare");
                Set<String> allowed = new java.util.HashSet<>();
                for (String value : scalar(e.getValue(), keyPath).split("\\|", -1)) {
                    if (!p.values().contains(value))
                        throw error(keyPath, "'" + value + "' is not one of " + name + "'s values");
                    allowed.add(value);
                }
                required.put(name, Set.copyOf(allowed));
            }
            return state -> {
                for (Map.Entry<String, Set<String>> e : required.entrySet())
                    if (!e.getValue().contains(state.get(e.getKey())))
                        return false;
                return true;
            };
        }

        private Choice choice(Object json, String path) {
            if (json instanceof List<?> list) {
                if (list.isEmpty())
                    throw error(path, "a weighted list needs at least one variant");
                List<Part> parts = new ArrayList<>();
                for (int i = 0; i < list.size(); i++)
                    parts.add(part(list.get(i), path + "[" + i + "]"));
                return new Choice(parts);
            }
            return new Choice(List.of(part(json, path)));
        }

        private Part part(Object json, String path) {
            Map<String, Object> v = object(json, path);
            unknownKeys(v, VARIANT_KEYS, path);
            if (!(v.get("model") instanceof String model) || model.isEmpty())
                throw error(path, "every variant needs a model");
            int x = rotation(v, "x", path), y = rotation(v, "y", path);
            boolean uvlock = false;
            if (v.containsKey("uvlock")) {
                if (!(v.get("uvlock") instanceof Boolean b))
                    throw error(path + ".uvlock", "uvlock is true or false");
                uvlock = b;
            }
            int weight = 1;
            if (v.containsKey("weight")) {
                Object w = v.get("weight");
                if (!(w instanceof Double d) || d != Math.rint(d) || d < 1 || d > 1_000_000)
                    throw error(path + ".weight", "weight must be 1 or more");
                weight = d.intValue();
            }
            return new Part(model, x, y, uvlock, weight);
        }

        private int rotation(Map<String, Object> v, String axis, String path) {
            if (!v.containsKey(axis))
                return 0;
            Object r = v.get(axis);
            if (r instanceof Double d && d == Math.rint(d)) {
                int degrees = d.intValue();
                if (degrees == 0 || degrees == 90 || degrees == 180 || degrees == 270)
                    return degrees;
            }
            throw error(path + "." + axis, axis + " must be 0, 90, 180 or 270");
        }

        private void unknownKeys(Map<String, Object> json, Set<String> known, String path) {
            for (String key : json.keySet())
                if (!known.contains(key))
                    throw error(path.isEmpty() ? key : path + "." + key, "unknown key '" + key + "'; expected one of "
                            + new java.util.TreeSet<>(known));
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> object(Object json, String path) {
            if (json instanceof Map<?, ?> map)
                return (Map<String, Object>) map;
            throw error(path, "expected an object");
        }

        @SuppressWarnings("unchecked")
        private List<Object> list(Object json, String path) {
            if (json instanceof List<?> list)
                return (List<Object>) list;
            throw error(path, "expected a list");
        }

        /** Strings, plus booleans and whole numbers written without quotes ({@code "lit": true}). */
        private String scalar(Object json, String path) {
            if (json instanceof String s)
                return s;
            if (json instanceof Boolean b)
                return b.toString();
            if (json instanceof Double d && d == Math.rint(d))
                return Long.toString(d.longValue());
            throw error(path, "expected a string");
        }

        private BlockstateException error(String path, String reason) {
            return new BlockstateException(source + (path.isEmpty() ? "" : ": " + path) + ": " + reason);
        }
    }
}
