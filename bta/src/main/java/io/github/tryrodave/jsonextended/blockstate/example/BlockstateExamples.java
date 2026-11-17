package io.github.tryrodave.jsonextended.blockstate.example;

import io.github.tryrodave.jsonextended.JsonExtended;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.block.model.BlockModelDispatcher;
import net.minecraft.client.render.block.model.generic.BlockModelGeneric;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.BlockLogic;
import net.minecraft.core.block.material.Materials;
import net.minecraft.core.entity.Mob;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.util.helper.MathHelper;
import net.minecraft.core.util.helper.Side;
import net.minecraft.core.world.World;
import net.minecraft.core.world.pos.TilePosc;
import turniplabs.halplibe.helper.BlockBuilder;
import turniplabs.halplibe.helper.creativeInventory.CreativeInventoryCategory;
import turniplabs.halplibe.helper.creativeInventory.CreativeInventoryPlacement;

/**
 * Dev-only test blocks for blockstate JSON (phase 1). Each one has a blockstate file in
 * {@code assets/jsonextended/blockstates/} and an expected-results table in
 * {@code core/src/test/resources/blockstate/cases/}.
 * <p>
 * Placement sets the metadata the way the vanilla block would, and right-clicking steps through every valid
 * metadata value, so each variant can be checked in game. Until blockstate rendering lands they are drawn with
 * BTA's {@link BlockModelGeneric} and one fixed model, so they don't turn yet.
 */
public final class BlockstateExamples {
    // Fixed ids are fine for dev-only blocks. 9100 is the Blockbench test block.
    private static final int FIRST_ID = 9110;

    /** Six-way facing in bits 0-2 (south, west, north, east, up, down) and lit in bit 3. */
    public static Block<FacingLogic> testFacing;
    /** Facing in bits 0-1 (south, west, north, east), half in bit 2; shape will come from a StateResolver. */
    public static Block<StairsLogic> testStairs;
    /** Type in bits 0-1: bottom, top, double. */
    public static Block<SlabLogic> testSlab;
    /** No metadata: north/east/south/west connections will come from a StateResolver. */
    public static Block<ExampleLogic> testFence;
    /** No metadata: one of four weighted variants per position. */
    public static Block<ExampleLogic> testWeighted;

    private BlockstateExamples() {}

    public static void registerBlocks() {
        BlockBuilder builder = new BlockBuilder(JsonExtended.MOD_ID)
                .setHardness(1.0f)
                .setCreativeInventoryPlacement(new CreativeInventoryPlacement.Category(CreativeInventoryCategory.MISCELLANEOUS));
        testFacing = builder.build("test_facing", FIRST_ID, FacingLogic::new);
        testStairs = builder.build("test_stairs", FIRST_ID + 1, StairsLogic::new);
        testSlab = builder.build("test_slab", FIRST_ID + 2, SlabLogic::new);
        testFence = builder.build("test_fence", FIRST_ID + 3, ExampleLogic::new);
        testWeighted = builder.build("test_weighted", FIRST_ID + 4, ExampleLogic::new);
    }

    /** Placeholder models until blockstate rendering lands: the blockstate code will replace these. */
    @Environment(EnvType.CLIENT)
    public static void addModels(BlockModelDispatcher dispatcher) {
        fixedModel(dispatcher, testFacing, "jsonextended:block/test_facing");
        fixedModel(dispatcher, testStairs, "jsonextended:block/test_stairs");
        fixedModel(dispatcher, testSlab, "jsonextended:block/test_slab");
        fixedModel(dispatcher, testFence, "jsonextended:block/test_fence_inventory");
        fixedModel(dispatcher, testWeighted, "jsonextended:block/test_weighted_a");
    }

    @Environment(EnvType.CLIENT)
    private static <T extends BlockLogic> void fixedModel(BlockModelDispatcher dispatcher, Block<T> block, String model) {
        dispatcher.addDispatch(block, new BlockModelGeneric<>(block, BlockModelDispatcher.loadDataModel(model)));
    }

    /** Horizontal index of the way the mob looks: 0 south, 1 west, 2 north, 3 east (as in Betafront's ramp). */
    static int lookIndex(Mob mob) {
        return MathHelper.floor(mob.yRot * 4.0f / 360.0f + 0.5) & 3;
    }

    /**
     * A non-cube test block. Right-click steps through {@code states}, the valid metadata values in order;
     * with no states, right-click does nothing.
     */
    public static class ExampleLogic extends BlockLogic {
        private final int[] states;

        public ExampleLogic(Block<?> block) {
            this(block, new int[0]);
        }

        protected ExampleLogic(Block<?> block, int[] states) {
            super(block, Materials.STONE);
            this.states = states;
        }

        @Override
        public boolean isSolidRender() {
            return false;
        }

        @Override
        public boolean isCubeShaped() {
            return false;
        }

        @Override
        public boolean onInteracted(World world, TilePosc tilePos, Player player, Side side, double xHit, double yHit) {
            if (states.length == 0)
                return false;
            int meta = world.getBlockData(tilePos);
            int next = states[0];
            for (int i = 0; i < states.length; i++) {
                if (states[i] == meta) {
                    next = states[(i + 1) % states.length];
                    break;
                }
            }
            world.setBlockDataNotify(tilePos, next);
            return true;
        }
    }

    /** Front faces the player when placed. */
    public static class FacingLogic extends ExampleLogic {
        public FacingLogic(Block<?> block) {
            super(block, new int[] {0, 1, 2, 3, 4, 5, 8, 9, 10, 11, 12, 13});
        }

        @Override
        public void onPlacedByMob(World world, TilePosc tilePos, Side side, Mob mob, double xHit, double yHit) {
            world.setBlockDataNotify(tilePos, (lookIndex(mob) + 2) & 3);
        }
    }

    /** Faces the way the player looks, like vanilla stairs; upside down when placed against a block's underside. */
    public static class StairsLogic extends ExampleLogic {
        public StairsLogic(Block<?> block) {
            super(block, new int[] {0, 1, 2, 3, 4, 5, 6, 7});
        }

        @Override
        public void onPlacedByMob(World world, TilePosc tilePos, Side side, Mob mob, double xHit, double yHit) {
            int half = side == Side.BOTTOM ? 1 : 0;
            world.setBlockDataNotify(tilePos, lookIndex(mob) | half << 2);
        }
    }

    /** Top slab when placed against a block's underside; right-click also reaches double. */
    public static class SlabLogic extends ExampleLogic {
        public SlabLogic(Block<?> block) {
            super(block, new int[] {0, 1, 2});
        }

        @Override
        public void onPlacedByMob(World world, TilePosc tilePos, Side side, Mob mob, double xHit, double yHit) {
            world.setBlockDataNotify(tilePos, side == Side.BOTTOM ? 1 : 0);
        }
    }
}
