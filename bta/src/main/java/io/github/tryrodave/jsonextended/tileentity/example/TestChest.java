package io.github.tryrodave.jsonextended.tileentity.example;

import com.mojang.nbt.tags.CompoundTag;
import io.github.tryrodave.jsonextended.JsonExtended;
import io.github.tryrodave.jsonextended.dev.DevContent.ModelLogic;
import io.github.tryrodave.jsonextended.model.Models;
import io.github.tryrodave.jsonextended.pose.PartTransform;
import io.github.tryrodave.jsonextended.pose.Pose;
import io.github.tryrodave.jsonextended.tileentity.ModelTileEntityRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.TileEntityRenderDispatcher;
import net.minecraft.client.render.block.model.BlockModelDispatcher;
import net.minecraft.client.render.block.model.generic.BlockModelGeneric;
import net.minecraft.client.render.tessellator.TessellatorGeneral;
import net.minecraft.client.render.texture.stitcher.IconCoordinate;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.entity.TileEntity;
import net.minecraft.core.block.entity.TileEntityDispatcher;
import net.minecraft.core.entity.Mob;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.util.collection.NamespaceID;
import net.minecraft.core.util.helper.MathHelper;
import net.minecraft.core.util.helper.Side;
import net.minecraft.core.world.World;
import net.minecraft.core.world.WorldSource;
import net.minecraft.core.world.pos.TilePosc;
import turniplabs.halplibe.helper.BlockBuilder;
import turniplabs.halplibe.helper.creativeInventory.CreativeInventoryCategory;
import turniplabs.halplibe.helper.creativeInventory.CreativeInventoryPlacement;

/**
 * Dev-only phase 5 test: a chest from a Blockbench model whose {@code lid} group opens and closes on right-click.
 * Metadata: bits 0-1 facing (the front faces the player who placed it), bit 2 open. The tile entity only animates;
 * the open state lives in the metadata, so it's saved with the world and nothing extra goes in the save file.
 */
public final class TestChest {
    // 9120-9123 are the model tests (ModelExamples).
    private static final int ID = 9124;
    private static final String MODEL = "jsonextended:block/test_chest";
    private static final int OPEN = 4;

    public static Block<ChestLogic> testChest;

    private TestChest() {}

    public static void registerBlocks() {
        TileEntityDispatcher.addMapping(ChestTileEntity.class, NamespaceID.fromPool(JsonExtended.MOD_ID, "test_chest"));
        testChest = new BlockBuilder(JsonExtended.MOD_ID)
                .setHardness(1.0f)
                .setCreativeInventoryPlacement(new CreativeInventoryPlacement.Category(CreativeInventoryCategory.MISCELLANEOUS))
                .setTileEntity(ChestTileEntity::new)
                .build("test_chest", ID, ChestLogic::new);
    }

    /** The chunk draws nothing (the renderer draws the chest); the item still shows the model, closed. */
    @Environment(EnvType.CLIENT)
    public static void addModels(BlockModelDispatcher dispatcher) {
        dispatcher.addDispatch(testChest, new BlockModelGeneric<ChestLogic>(testChest, Models.load(MODEL)) {
            @Override
            public boolean render(TessellatorGeneral tess, WorldSource world, TilePosc pos) {
                return false;
            }

            @Override
            public boolean renderAttached(TessellatorGeneral tess, WorldSource world, TilePosc pos, boolean noCulling,
                                          IconCoordinate overrideTexture) {
                return false;
            }
        });
    }

    @Environment(EnvType.CLIENT)
    public static void addRenderers(TileEntityRenderDispatcher dispatcher) {
        dispatcher.assignRenderer(ChestTileEntity.class, new ModelTileEntityRenderer<ChestTileEntity>(MODEL,
                (chest, partialTick) -> {
                    float t = chest.prevLid + (chest.lid - chest.prevLid) * partialTick;
                    float eased = 1f - (1f - t) * (1f - t) * (1f - t);
                    // Hinge at the back top; +X swings the front up and back (knowledge/formats/pose.md).
                    return Pose.REST.with("lid", PartTransform.rotation(eased * 90f, 0f, 0f));
                },
                chest -> -(chest.getBlockMeta() & 3)));
    }

    public static class ChestLogic extends ModelLogic {
        public ChestLogic(Block<?> block) {
            super(block);
        }

        @Override
        public void onPlacedByMob(World world, TilePosc tilePos, Side side, Mob mob, double xHit, double yHit) {
            world.setBlockDataNotify(tilePos, MathHelper.floor(mob.yRot * 4.0f / 360.0f + 0.5) & 3);
        }

        @Override
        public boolean onInteracted(World world, TilePosc tilePos, Player player, Side side, double xHit, double yHit) {
            world.setBlockDataNotify(tilePos, world.getBlockData(tilePos) ^ OPEN);
            return true;
        }
    }

    /** Moves {@code lid} (0 closed, 1 open) a tenth of the way per tick towards the metadata's open bit. */
    public static class ChestTileEntity extends TileEntity {
        public float lid;
        public float prevLid;

        public ChestTileEntity() {}

        @Override
        public void tick() {
            prevLid = lid;
            boolean open = (getBlockMeta() & OPEN) != 0;
            lid = open ? Math.min(1f, lid + 0.1f) : Math.max(0f, lid - 0.1f);
        }

        @Override
        public void readAdditionalData(CompoundTag tag) {}

        @Override
        public void writeAdditionalData(CompoundTag tag) {}
    }
}
