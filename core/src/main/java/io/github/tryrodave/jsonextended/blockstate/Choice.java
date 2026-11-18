package io.github.tryrodave.jsonextended.blockstate;

import java.util.List;

/** A variant: one part, or a weighted list of parts of which one is drawn per block position. */
public final class Choice {
    private final List<Part> options;
    private final int totalWeight;

    Choice(List<Part> options) {
        this.options = List.copyOf(options);
        int total = 0;
        for (Part part : options)
            total += part.weight();
        this.totalWeight = total;
    }

    public List<Part> options() {
        return options;
    }

    public int totalWeight() {
        return totalWeight;
    }

    /** The first option: the one used where there is no position, such as in the inventory. */
    public Part first() {
        return options.get(0);
    }

    /**
     * The option for a block position seed (BTA {@code BlockModel.getPositionalSeed}). The same seed always
     * gives the same part; over many seeds each part comes up about weight / total weight of the time.
     */
    public Part pick(long seed) {
        if (options.size() == 1)
            return options.get(0);
        int roll = (int) Long.remainderUnsigned(mix(seed), totalWeight);
        for (Part part : options) {
            roll -= part.weight();
            if (roll < 0)
                return part;
        }
        return options.get(options.size() - 1);
    }

    /** SplitMix64 finaliser, so neighbouring seeds give unrelated picks. */
    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    @Override
    public String toString() {
        return options.toString();
    }
}
