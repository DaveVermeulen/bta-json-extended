package io.github.tryrodave.jsonextended.blockstate;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.block.Block;
import net.minecraft.core.world.WorldSource;
import net.minecraft.core.world.pos.TilePosc;

/** Ready-made {@link StateResolver}s for the common neighbour-dependent blocks. */
@Environment(EnvType.CLIENT)
public final class NeighbourResolvers {
    private static final String[] HORIZONTAL = {"north", "east", "south", "west"};

    private NeighbourResolvers() {}

    /**
     * Vanilla stair {@code shape}, from metadata properties {@code facing} (north/east/south/west) and {@code half}.
     * Neighbours count as stairs when {@code isStairs} accepts their block; their facing and half are decoded
     * with this block's blockstate, so they must use the same metadata layout.
     */
    public static StateResolver stairs(Predicate<Block<?>> isStairs) {
        Map<String, Map<String, String>> shapes = new HashMap<>();
        for (String shape : StairShape.SHAPES)
            shapes.put(shape, Map.of("shape", shape));
        return (world, pos, meta, blockstate) -> {
            String facing = blockstate.metaValue("facing", meta), half = blockstate.metaValue("half", meta);
            if (facing == null || half == null)
                return Map.of();
            String shape = StairShape.of(facing, half, direction -> {
                int x = pos.x() + dx(direction), y = pos.y(), z = pos.z() + dz(direction);
                Block<?> block = world.getBlock(x, y, z);
                if (block == null || !isStairs.test(block))
                    return null;
                int otherMeta = world.getBlockMetadata(x, y, z);
                String otherFacing = blockstate.metaValue("facing", otherMeta), otherHalf = blockstate.metaValue("half", otherMeta);
                return otherFacing == null || otherHalf == null ? null : new StairShape.Stair(otherFacing, otherHalf);
            });
            return shapes.get(shape);
        };
    }

    /**
     * Fence-style {@code north}/{@code east}/{@code south}/{@code west} = {@code "true"} or {@code "false"}: a side
     * connects when {@code connectsTo} accepts the neighbouring block or the neighbour is an opaque full cube.
     */
    public static StateResolver connections(Predicate<Block<?>> connectsTo) {
        @SuppressWarnings("unchecked")
        Map<String, String>[] states = new Map[16];
        for (int bits = 0; bits < 16; bits++) {
            Map<String, String> state = new HashMap<>();
            for (int i = 0; i < 4; i++)
                state.put(HORIZONTAL[i], Boolean.toString((bits & 1 << i) != 0));
            states[bits] = Map.copyOf(state);
        }
        return (world, pos, meta, blockstate) -> {
            int bits = 0;
            for (int i = 0; i < 4; i++) {
                int x = pos.x() + dx(HORIZONTAL[i]), y = pos.y(), z = pos.z() + dz(HORIZONTAL[i]);
                Block<?> block = world.getBlock(x, y, z);
                if (block != null && (connectsTo.test(block) || world.isBlockOpaqueCube(x, y, z)))
                    bits |= 1 << i;
            }
            return states[bits];
        };
    }

    /** Vanilla axes: east is +x, south is +z. */
    static int dx(String direction) {
        return switch (direction) {
            case "east" -> 1;
            case "west" -> -1;
            default -> 0;
        };
    }

    static int dz(String direction) {
        return switch (direction) {
            case "south" -> 1;
            case "north" -> -1;
            default -> 0;
        };
    }
}
