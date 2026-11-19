package io.github.tryrodave.jsonextended.animation.example;

import com.mojang.nbt.tags.CompoundTag;
import io.github.tryrodave.jsonextended.JsonExtended;
import io.github.tryrodave.jsonextended.animation.Animation;
import io.github.tryrodave.jsonextended.animation.AnimationFile;
import io.github.tryrodave.jsonextended.animation.AnimationPlayer;
import io.github.tryrodave.jsonextended.animation.Animations;
import io.github.tryrodave.jsonextended.model.Models;
import io.github.tryrodave.jsonextended.pose.Pose;
import io.github.tryrodave.jsonextended.tileentity.ModelTileEntityRenderer;
import io.github.tryrodave.jsonextended.tileentity.example.TestChest.ChestLogic;
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
import net.minecraft.core.util.collection.NamespaceID;
import net.minecraft.core.world.WorldSource;
import net.minecraft.core.world.pos.TilePosc;
import turniplabs.halplibe.helper.BlockBuilder;
import turniplabs.halplibe.helper.creativeInventory.CreativeInventoryCategory;
import turniplabs.halplibe.helper.creativeInventory.CreativeInventoryPlacement;

/**
 * Dev-only phase 6 test: the test chest's model, but the lid moves by {@code animations/test_chest.animation.json}
 * (Bedrock, as Blockbench exports it): {@code open} and {@code close} hold their last frame, {@code idle} wiggles
 * the knob while the chest is open. Same metadata as {@code TestChest}: bits 0-1 facing, bit 2 open.
 */
public final class AnimatedChest {
    // 9124 is TestChest.
    private static final int ID = 9125;
    private static final String MODEL = "jsonextended:block/test_chest";
    private static final String ANIMATIONS = "jsonextended:test_chest";
    private static final int OPEN = 4;

    public static Block<ChestLogic> animatedChest;

    private AnimatedChest() {}

    public static void registerBlocks() {
        TileEntityDispatcher.addMapping(AnimatedChestTileEntity.class,
                NamespaceID.fromPool(JsonExtended.MOD_ID, "test_animated_chest"));
        animatedChest = new BlockBuilder(JsonExtended.MOD_ID)
                .setHardness(1.0f)
                .setCreativeInventoryPlacement(new CreativeInventoryPlacement.Category(CreativeInventoryCategory.MISCELLANEOUS))
                .setTileEntity(AnimatedChestTileEntity::new)
                .build("test_animated_chest", ID, ChestLogic::new);
    }

    @Environment(EnvType.CLIENT)
    public static void addModels(BlockModelDispatcher dispatcher) {
        dispatcher.addDispatch(animatedChest, new BlockModelGeneric<ChestLogic>(animatedChest, Models.load(MODEL)) {
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

    /** A new renderer, and so a fresh read of the animation file, on every pack reload. */
    @Environment(EnvType.CLIENT)
    public static void addRenderers(TileEntityRenderDispatcher dispatcher) {
        AnimationFile[] file = new AnimationFile[1];
        dispatcher.assignRenderer(AnimatedChestTileEntity.class, new ModelTileEntityRenderer<AnimatedChestTileEntity>(MODEL,
                (chest, partialTick) -> {
                    if (file[0] == null)
                        file[0] = Animations.load(ANIMATIONS);
                    float now = (chest.ticks + partialTick) / 20f;
                    boolean open = (chest.getBlockMeta() & OPEN) != 0;
                    Animation wanted = file[0].get(open ? "animation.test_chest.open" : "animation.test_chest.close");
                    if (wanted == null)
                        return Pose.REST;
                    AnimationPlayer player = chest.player();
                    if (player.current() == null && !open) {
                        // A closed chest that was never opened: start at the end of "close", not mid-swing.
                        player.play(wanted, now - wanted.length());
                    } else {
                        player.ensure(wanted, now, 0.1f);
                    }
                    Pose pose = player.pose(now, Pose.REST);
                    Animation idle = file[0].get("animation.test_chest.idle");
                    return open && idle != null && player.elapsed(now) >= wanted.length() ? idle.sample(now, pose) : pose;
                },
                chest -> -(chest.getBlockMeta() & 3)));
    }

    /** Only counts ticks; the animation player is client state that isn't saved. */
    public static class AnimatedChestTileEntity extends TileEntity {
        public int ticks;
        private Object player;

        public AnimatedChestTileEntity() {}

        @Override
        public void tick() {
            ticks++;
        }

        @Environment(EnvType.CLIENT)
        AnimationPlayer player() {
            if (player == null)
                player = new AnimationPlayer();
            return (AnimationPlayer) player;
        }

        @Override
        public void readAdditionalData(CompoundTag tag) {}

        @Override
        public void writeAdditionalData(CompoundTag tag) {}
    }
}
