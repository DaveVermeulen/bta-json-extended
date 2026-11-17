package io.github.tryrodave.jsonextended.blockstate;

import java.util.List;

/**
 * Vanilla's stair {@code shape} rule (straight, inner_left, inner_right, outer_left, outer_right), worked out from
 * the stairs behind and in front. Plain logic so it can be tested without the game; a {@code StateResolver} in
 * the mod feeds it the neighbouring blocks.
 * <p>
 * Directions are the vanilla names {@code north}, {@code east}, {@code south}, {@code west}; {@code facing} is the
 * side the tall half is on (the way the player looked when placing); {@code half} is {@code bottom} or {@code top}.
 */
public final class StairShape {
    public static final List<String> SHAPES = List.of("straight", "inner_left", "inner_right", "outer_left", "outer_right");

    /** A stair block's facing and half. */
    public record Stair(String facing, String half) {}

    /** The stair block one step in a horizontal direction from this one, or null when it isn't a stair. */
    @FunctionalInterface
    public interface Neighbours {
        Stair at(String direction);
    }

    private StairShape() {}

    public static String of(String facing, String half, Neighbours neighbours) {
        Stair behind = neighbours.at(facing);
        if (behind != null && behind.half().equals(half)) {
            String turned = behind.facing();
            if (!sameAxis(turned, facing) && canTakeShape(facing, half, neighbours, opposite(turned)))
                return turned.equals(counterClockwise(facing)) ? "outer_left" : "outer_right";
        }
        Stair front = neighbours.at(opposite(facing));
        if (front != null && front.half().equals(half)) {
            String turned = front.facing();
            if (!sameAxis(turned, facing) && canTakeShape(facing, half, neighbours, turned))
                return turned.equals(counterClockwise(facing)) ? "inner_left" : "inner_right";
        }
        return "straight";
    }

    /** False when the stair on that side already lines up with this one, so the corner would break the run. */
    private static boolean canTakeShape(String facing, String half, Neighbours neighbours, String side) {
        Stair other = neighbours.at(side);
        return other == null || !other.facing().equals(facing) || !other.half().equals(half);
    }

    public static String opposite(String direction) {
        return switch (direction) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            case "west" -> "east";
            default -> throw new IllegalArgumentException("not a horizontal direction: " + direction);
        };
    }

    /** Seen from above: north to west, west to south, south to east, east to north. */
    public static String counterClockwise(String direction) {
        return switch (direction) {
            case "north" -> "west";
            case "west" -> "south";
            case "south" -> "east";
            case "east" -> "north";
            default -> throw new IllegalArgumentException("not a horizontal direction: " + direction);
        };
    }

    private static boolean sameAxis(String a, String b) {
        return a.equals(b) || a.equals(opposite(b));
    }
}
