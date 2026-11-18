package io.github.tryrodave.jsonextended.blockstate;

import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.world.WorldSource;
import net.minecraft.core.world.pos.TilePosc;

/**
 * Supplies the blockstate properties that aren't stored in metadata (those declared without {@code bits}), from
 * the world: stair shape, fence connections. Called for every draw of the block, so keep it cheap and reuse
 * maps where you can. Values must be among the property's {@code values}.
 */
@Environment(EnvType.CLIENT)
@FunctionalInterface
public interface StateResolver {
    /**
     * @param meta       the block's metadata
     * @param blockstate the block's blockstate file, e.g. to decode a neighbour's metadata with
     *                   {@link Blockstate#metaValue}
     */
    Map<String, String> resolve(WorldSource world, TilePosc pos, int meta, Blockstate blockstate);
}
