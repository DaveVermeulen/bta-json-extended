package io.github.tryrodave.jsonextended.animation.example;

import io.github.tryrodave.jsonextended.JsonExtended;
import io.github.tryrodave.jsonextended.animation.AnimatedMobRenderer;
import io.github.tryrodave.jsonextended.dev.DevContent.ModelLogic;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import io.github.tryrodave.jsonextended.model.Models;
import net.minecraft.client.render.EntityRendererDispatcher;
import net.minecraft.client.render.block.model.BlockModelDispatcher;
import net.minecraft.client.render.block.model.generic.BlockModelGeneric;
import net.minecraft.core.block.Block;
import net.minecraft.core.entity.EntityDispatcher;
import net.minecraft.core.entity.animal.MobAnimal;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.util.collection.NamespaceID;
import net.minecraft.core.util.helper.Side;
import net.minecraft.core.world.World;
import net.minecraft.core.world.pos.TilePosc;
import turniplabs.halplibe.helper.BlockBuilder;
import turniplabs.halplibe.helper.creativeInventory.CreativeInventoryCategory;
import turniplabs.halplibe.helper.creativeInventory.CreativeInventoryPlacement;

/**
 * Dev-only phase 6 test: a wandering mob with our own geo model ({@code models/entity/test_mob/}) and texture,
 * posed by {@code animations/test_mob.animation.json}: {@code idle} (nods, looks around) blends into {@code walk}
 * (legs swing, body bobs) as it walks. BTA has no spawn egg, so right-clicking the Test Mob Spawner block puts
 * one on top of it.
 */
public final class TestMob extends MobAnimal {
    private static final int SPAWNER_ID = 9126;

    public static Block<SpawnerLogic> spawner;

    public TestMob(World world) {
        super(world);
        setTextureIdentifier(JsonExtended.MOD_ID, "test_mob");
        setSize(0.9f, 1.0f);
    }

    public static void register() {
        EntityDispatcher.getInstance().addMapping(TestMob.class, NamespaceID.fromPool(JsonExtended.MOD_ID, "test_mob"), TestMob::new);
        spawner = new BlockBuilder(JsonExtended.MOD_ID)
                .setHardness(1.0f)
                .setCreativeInventoryPlacement(new CreativeInventoryPlacement.Category(CreativeInventoryCategory.MISCELLANEOUS))
                .build("test_mob_spawner", SPAWNER_ID, SpawnerLogic::new);
    }

    /** The spawner block reuses the phase 0 Blockbench test model. */
    @Environment(EnvType.CLIENT)
    public static void addModels(BlockModelDispatcher dispatcher) {
        dispatcher.addDispatch(spawner, new BlockModelGeneric<>(spawner, Models.load("jsonextended:block/blockbench_test")));
    }

    @Environment(EnvType.CLIENT)
    public static void addRenderers(EntityRendererDispatcher dispatcher) {
        dispatcher.assignRenderer(TestMob.class, new AnimatedMobRenderer<TestMob>(0.6f, "jsonextended:test_mob",
                "animation.test_mob.idle", "animation.test_mob.walk", AnimatedMobRenderer.VANILLA_WALK_CYCLE));
    }

    /** Right-click spawns a test mob on top. */
    public static class SpawnerLogic extends ModelLogic {
        public SpawnerLogic(Block<?> block) {
            super(block);
        }

        @Override
        public boolean onInteracted(World world, TilePosc tilePos, Player player, Side side, double xHit, double yHit) {
            if (!world.isClientSide) {
                TestMob mob = new TestMob(world);
                mob.moveTo(tilePos.x() + 0.5, tilePos.y() + 1, tilePos.z() + 0.5, player.yRot + 180f, 0f);
                world.entityJoinedWorld(mob);
            }
            return true;
        }
    }
}
