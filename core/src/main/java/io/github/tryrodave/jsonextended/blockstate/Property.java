package io.github.tryrodave.jsonextended.blockstate;

import java.util.List;

/**
 * A named block state property. With {@code bits} it is read from block metadata: the value index is those bits
 * read as a number. Without, a {@code StateResolver} supplies it from the world (stair shape, fence sides).
 */
public final class Property {
    /** BTA block metadata has 4 bits. */
    public static final int META_BITS = 4;

    private final String name;
    private final int firstBit;
    private final int bitCount;
    private final List<String> values;

    Property(String name, int firstBit, int bitCount, List<String> values) {
        this.name = name;
        this.firstBit = firstBit;
        this.bitCount = bitCount;
        this.values = List.copyOf(values);
    }

    public String name() {
        return name;
    }

    public List<String> values() {
        return values;
    }

    /** True when the value comes from metadata bits, false when a resolver supplies it. */
    public boolean fromMeta() {
        return bitCount > 0;
    }

    public int firstBit() {
        return firstBit;
    }

    public int bitCount() {
        return bitCount;
    }

    /** The value index stored in {@code meta}; may be past the end of {@link #values()}. */
    public int indexIn(int meta) {
        return (meta >>> firstBit) & ((1 << bitCount) - 1);
    }

    /** Bit mask of this property within metadata. */
    int mask() {
        return ((1 << bitCount) - 1) << firstBit;
    }

    @Override
    public String toString() {
        return fromMeta() ? name + "(bits " + firstBit + "-" + (firstBit + bitCount - 1) + ")" + values : name + values;
    }
}
