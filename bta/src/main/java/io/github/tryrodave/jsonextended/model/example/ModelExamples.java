package io.github.tryrodave.jsonextended.model.example;

import io.github.tryrodave.jsonextended.JsonExtended;
import io.github.tryrodave.jsonextended.blockstate.Blockstates;
import io.github.tryrodave.jsonextended.blockstate.example.BlockstateExamples.ExampleLogic;
import io.github.tryrodave.jsonextended.blockstate.example.BlockstateExamples.StairsLogic;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.block.model.BlockModelDispatcher;
import net.minecraft.core.block.Block;
import turniplabs.halplibe.helper.BlockBuilder;
import turniplabs.halplibe.helper.creativeInventory.CreativeInventoryCategory;
import turniplabs.halplibe.helper.creativeInventory.CreativeInventoryPlacement;

/**
 * Dev-only test blocks for model JSON: Blockbench keys BTA ignores, {@code render_type}, a broken file that must
 * give the missing model and an error line instead of crashing the reload (phase 3), and OBJ models (phase 4).
 */
public final class ModelExamples {
    // Fixed ids are fine for dev-only blocks. 9100 is the Blockbench test block, 9110-9114 the blockstate tests.
    private static final int FIRST_ID = 9120;

    /** A Blockbench export with a 32x32 texture, {@code render_type: translucent} and a 45-degree element. */
    public static Block<ExampleLogic> testGlass;
    /** Broken JSON on purpose (a trailing comma): the missing model and one error line. */
    public static Block<ExampleLogic> testBrokenModel;

    /** Betafront's OBJ crystal (a Blender export), through {@code "loader": "betafront:obj"}. */
    public static Block<ExampleLogic> testObjCrystal;
    /** Betafront's OBJ ramp through {@code "loader": "jsonextended:obj"}: faces the player, upside down from below. */
    public static Block<StairsLogic> testObjRamp;

    private ModelExamples() {}

    public static void registerBlocks() {
        BlockBuilder builder = new BlockBuilder(JsonExtended.MOD_ID)
                .setHardness(1.0f)
                .setCreativeInventoryPlacement(new CreativeInventoryPlacement.Category(CreativeInventoryCategory.MISCELLANEOUS));
        testGlass = builder.build("test_glass", FIRST_ID, ExampleLogic::new);
        testBrokenModel = builder.build("test_broken_model", FIRST_ID + 1, ExampleLogic::new);
        testObjCrystal = builder.build("test_obj_crystal", FIRST_ID + 2, ExampleLogic::new);
        testObjRamp = builder.build("test_obj_ramp", FIRST_ID + 3, StairsLogic::new);
    }

    @Environment(EnvType.CLIENT)
    public static void addModels(BlockModelDispatcher dispatcher) {
        dispatcher.addDispatch(testGlass, Blockstates.model(testGlass, "jsonextended:test_glass"));
        dispatcher.addDispatch(testBrokenModel, Blockstates.model(testBrokenModel, "jsonextended:test_broken_model"));
        dispatcher.addDispatch(testObjCrystal, Blockstates.model(testObjCrystal, "jsonextended:test_obj_crystal"));
        dispatcher.addDispatch(testObjRamp, Blockstates.model(testObjRamp, "jsonextended:test_obj_ramp"));
    }
}
