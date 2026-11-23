package io.github.tryrodave.jsonextended.dev;

import io.github.tryrodave.jsonextended.JsonExtended;
import io.github.tryrodave.jsonextended.blockstate.example.BlockstateExamples;
import io.github.tryrodave.jsonextended.model.Models;
import io.github.tryrodave.jsonextended.model.example.ModelExamples;
import io.github.tryrodave.jsonextended.item.example.ItemExamples;
import io.github.tryrodave.jsonextended.tileentity.example.TestChest;
import io.github.tryrodave.jsonextended.animation.example.AnimatedChest;
import io.github.tryrodave.jsonextended.animation.example.TestMob;
import io.github.tryrodave.jsonextended.gltf.example.AnimatedLantern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.block.model.BlockModelDispatcher;
import net.minecraft.client.render.block.model.generic.BlockModelGeneric;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.BlockLogic;
import net.minecraft.core.block.material.Materials;
import turniplabs.halplibe.helper.BlockBuilder;
import turniplabs.halplibe.helper.creativeInventory.CreativeInventoryCategory;
import turniplabs.halplibe.helper.creativeInventory.CreativeInventoryPlacement;
import turniplabs.halplibe.event.defs.ClientEvents;
import turniplabs.halplibe.util.dependency.Key;

/**
 * Test blocks for the development client ({@code ./gradlew runBtaClient}). Not registered in a normal game.
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
        ModelExamples.registerBlocks();
        TestChest.registerBlocks();
        AnimatedChest.registerBlocks();
        AnimatedLantern.registerBlocks();
        TestMob.register();
        ItemExamples.registerItems();
    }

    /**
     * Called from the client entrypoint. The block model dispatcher doesn't exist yet at that point
     * (HalpLibe's deprecated {@code ModelHelper.setBlockModel} crashes there), so add the models whenever
     * HalpLibe (re)builds the dispatcher: at startup and on every texture pack change.
     */
    @Environment(EnvType.CLIENT)
    public static void registerModels() {
        ClientEvents.BLOCK_MODEL_RELOAD.listen(Key.of(JsonExtended.MOD_ID), DevContent::addModels);
        ClientEvents.ITEM_MODEL_RELOAD.listen(Key.of(JsonExtended.MOD_ID), ItemExamples::addModels);
        ClientEvents.TILE_ENTITY_RENDERER_RELOAD.listen(Key.of(JsonExtended.MOD_ID), dispatcher -> {
            TestChest.addRenderers(dispatcher);
            AnimatedChest.addRenderers(dispatcher);
            AnimatedLantern.addRenderers(dispatcher);
        });
        ClientEvents.ENTITY_RENDERER_RELOAD.listen(Key.of(JsonExtended.MOD_ID), TestMob::addRenderers);
    }

    @Environment(EnvType.CLIENT)
    private static void addModels(BlockModelDispatcher dispatcher) {
        dispatcher.addDispatch(blockbenchTest, new BlockModelGeneric<>(blockbenchTest,
                Models.load("jsonextended:block/blockbench_test")));
        BlockstateExamples.addModels(dispatcher);
        ModelExamples.addModels(dispatcher);
        TestChest.addModels(dispatcher);
        AnimatedChest.addModels(dispatcher);
        AnimatedLantern.addModels(dispatcher);
        TestMob.addModels(dispatcher);
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
