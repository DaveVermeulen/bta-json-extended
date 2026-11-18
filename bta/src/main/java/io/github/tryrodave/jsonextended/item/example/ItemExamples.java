package io.github.tryrodave.jsonextended.item.example;

import io.github.tryrodave.jsonextended.JsonExtended;
import io.github.tryrodave.jsonextended.item.JsonItems;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.item.model.ItemModelDispatcher;
import net.minecraft.core.item.Item;
import turniplabs.halplibe.helper.ItemBuilder;
import turniplabs.halplibe.helper.creativeInventory.CreativeInventoryCategory;
import turniplabs.halplibe.helper.creativeInventory.CreativeInventoryPlacement;

/**
 * Dev-only test items for item model JSON (phase 2), each with a model in {@code assets/jsonextended/models/item/}.
 */
public final class ItemExamples {
    // Plain items use ids 16384..32767; vanilla goes up to 16568.
    private static final int FIRST_ID = 17100;

    /** {@code item/generated}, one layer. */
    public static Item testRuby;
    /** {@code item/handheld}: held like a tool. */
    public static Item testSword;
    /** Own {@code display} poses (gui turned 45°, big on the ground, sideways in the hand, big on the head). */
    public static Item testDisplay;
    /** Two layers: the ruby with a gold ring drawn over it. */
    public static Item testLayered;
    /** {@code elements}: a small 3D gem drawn by BTA's JSON model code. */
    public static Item testGem;
    /** Broken on purpose (no layer0): the missing texture and one error line in the log. */
    public static Item testBroken;

    private ItemExamples() {}

    public static void registerItems() {
        ItemBuilder builder = new ItemBuilder(JsonExtended.MOD_ID)
                .setCreativeInventoryPlacement(new CreativeInventoryPlacement.Category(CreativeInventoryCategory.MISCELLANEOUS));
        testRuby = builder.build(new Item("test.ruby", "jsonextended:item/test_ruby", FIRST_ID));
        testSword = builder.build(new Item("test.sword", "jsonextended:item/test_sword", FIRST_ID + 1));
        testDisplay = builder.build(new Item("test.display", "jsonextended:item/test_display", FIRST_ID + 2));
        testLayered = builder.build(new Item("test.layered", "jsonextended:item/test_layered", FIRST_ID + 3));
        testGem = builder.build(new Item("test.gem", "jsonextended:item/test_gem", FIRST_ID + 4));
        testBroken = builder.build(new Item("test.broken", "jsonextended:item/test_broken", FIRST_ID + 5));
    }

    /** Called on every item model reload. */
    @Environment(EnvType.CLIENT)
    public static void addModels(ItemModelDispatcher dispatcher) {
        dispatcher.addDispatch(JsonItems.model(testRuby, "jsonextended:item/test_ruby"));
        dispatcher.addDispatch(JsonItems.model(testSword, "jsonextended:item/test_sword"));
        dispatcher.addDispatch(JsonItems.model(testDisplay, "jsonextended:item/test_display"));
        dispatcher.addDispatch(JsonItems.model(testLayered, "jsonextended:item/test_layered"));
        dispatcher.addDispatch(JsonItems.model(testGem, "jsonextended:item/test_gem"));
        dispatcher.addDispatch(JsonItems.model(testBroken, "jsonextended:item/test_broken"));
    }
}
