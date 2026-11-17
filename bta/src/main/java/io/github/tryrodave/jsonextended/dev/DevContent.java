package io.github.tryrodave.jsonextended.dev;

import io.github.tryrodave.jsonextended.JsonExtended;
import io.github.tryrodave.jsonextended.blockstate.example.BlockstateExamples;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.block.model.BlockModelDispatcher;
import net.minecraft.client.render.block.model.generic.BlockModelGeneric;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.BlockLogic;
import net.minecraft.core.block.material.Materials;
import turniplabs.halplibe.helper.BlockBuilder;
import turniplabs.halplibe.helper.ModelHelper;
import turniplabs.halplibe.helper.creativeInventory.CreativeInventoryCategory;
import turniplabs.halplibe.helper.creativeInventory.CreativeInventoryPlacement;

/**
 * Test blocks for the development client ({@code ./gradlew runClient}). Not registered in a normal game.
 * <p>
 * Phase 0 spike: a Blockbench "Java Block" export drawn by BTA's own {@link BlockModelGeneric},
 * with no model code of ours.
 */
public final class DevContent {
    // Fixed ids are fine for dev-only blocks.
    private static final int FIRST_ID = 9100;

    public static Block<ModelLogic> blockbenchTest;

    private DevContent() {}

    public static void registerBlocks() {
        BlockBuilder builder = new BlockBuilder(JsonExtended.MOD_ID)
                .setHardness(1.0f)
                // BTA's creative menu only lists blocks that are given a placement
                .setCreativeInventoryPlacement(new CreativeInventoryPlacement.Category(CreativeInventoryCategory.MISCELLANEOUS));
        blockbenchTest = builder.build("blockbench_test", FIRST_ID, ModelLogic::new);
        BlockstateExamples.registerBlocks();
    }

    @Environment(EnvType.CLIENT)
    public static void registerModels() {
        ModelHelper.setBlockModel(blockbenchTest, () -> new BlockModelGeneric<>(blockbenchTest,
                BlockModelDispatcher.loadDataModel("jsonextended:block/blockbench_test")));
        BlockstateExamples.registerModels();
    }

    /** Model blocks aren't full cubes, so they must not hide their neighbours' faces. */
    public static class ModelLogic extends BlockLogic {
        public ModelLogic(Block<?> block) {
            super(block, Materials.STONE);
        }

        @Override
        public boolean isSolidRender() {
            return false;
        }

        @Override
        public boolean isCubeShaped() {
            return false;
        }
    }
}
