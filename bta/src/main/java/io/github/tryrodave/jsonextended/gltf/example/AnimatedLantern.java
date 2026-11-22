package io.github.tryrodave.jsonextended.gltf.example;

import com.mojang.nbt.tags.CompoundTag;
import io.github.tryrodave.jsonextended.JsonExtended;
import io.github.tryrodave.jsonextended.animation.Animation.Loop;
import io.github.tryrodave.jsonextended.dev.DevContent.ModelLogic;
import io.github.tryrodave.jsonextended.gltf.GltfAnimation;
import io.github.tryrodave.jsonextended.gltf.GltfAnimationPlayer;
import io.github.tryrodave.jsonextended.gltf.GltfModel;
import io.github.tryrodave.jsonextended.gltf.GltfTileEntityRenderer;
import io.github.tryrodave.jsonextended.model.Models;
import java.util.Map;
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
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.util.collection.NamespaceID;
import net.minecraft.core.util.helper.Side;
import net.minecraft.core.world.World;
import net.minecraft.core.world.WorldSource;
import net.minecraft.core.world.pos.TilePosc;
import turniplabs.halplibe.helper.BlockBuilder;
import turniplabs.halplibe.helper.creativeInventory.CreativeInventoryCategory;
import turniplabs.halplibe.helper.creativeInventory.CreativeInventoryPlacement;

/**
 * Dev-only phase 7 step 2 test: the GLB lantern as a tile entity, played from the file's own glTF animations.
 * {@code idle} loops (a slow turn and a bob); right-click sets metadata bit 0 and the gem crossfades to
 * {@code spin} (fast, and bigger), right-click again goes back.
 */
public final class AnimatedLantern {
    // 9127 is the static Test GLB Lantern.
    private static final int ID = 9128;
    private static final String MODEL = "jsonextended:block/test_glb_animated_lantern";
    private static final int SPINNING = 1;

    public static Block<LanternLogic> animatedLantern;

    private AnimatedLantern() {}

    public static void registerBlocks() {
        TileEntityDispatcher.addMapping(LanternTileEntity.class,
                NamespaceID.fromPool(JsonExtended.MOD_ID, "test_glb_animated_lantern"));
        animatedLantern = new BlockBuilder(JsonExtended.MOD_ID)
                .setHardness(1.0f)
                .setCreativeInventoryPlacement(new CreativeInventoryPlacement.Category(CreativeInventoryCategory.MISCELLANEOUS))
                .setTileEntity(LanternTileEntity::new)
                .build("test_glb_animated_lantern", ID, LanternLogic::new);
    }

    /** The chunk draws nothing (the tile entity renderer draws the model); the item and particles use the model. */
    @Environment(EnvType.CLIENT)
    public static void addModels(BlockModelDispatcher dispatcher) {
        dispatcher.addDispatch(animatedLantern, new BlockModelGeneric<LanternLogic>(animatedLantern, Models.load(MODEL)) {
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

    /** A new renderer, and so a fresh read of the model and its animations, on every pack reload. */
    @Environment(EnvType.CLIENT)
    public static void addRenderers(TileEntityRenderDispatcher dispatcher) {
        dispatcher.assignRenderer(LanternTileEntity.class, new GltfTileEntityRenderer<LanternTileEntity>(MODEL,
                (lantern, model, partialTick) -> {
                    float now = (lantern.ticks + partialTick) / 20f;
                    boolean spinning = (lantern.getBlockMeta() & SPINNING) != 0;
                    GltfAnimation wanted = model.animations().get(spinning ? "spin" : "idle");
                    if (wanted == null)
                        return Map.of();
                    GltfAnimationPlayer player = lantern.player(model);
                    if (player.current() == null)
                        player.play(wanted, Loop.LOOP, now, 0);
                    else
                        player.ensure(wanted, Loop.LOOP, now, 0.3f);
                    return player.pose(now);
                }));
    }

    public static class LanternLogic extends ModelLogic {
        public LanternLogic(Block<?> block) {
            super(block);
        }

        @Override
        public boolean onInteracted(World world, TilePosc tilePos, Player player, Side side, double xHit, double yHit) {
            world.setBlockDataNotify(tilePos, world.getBlockData(tilePos) ^ SPINNING);
            return true;
        }
    }

    /** Only counts ticks; the animation player is client state that isn't saved. */
    public static class LanternTileEntity extends TileEntity {
        public int ticks;
        private Object player;
        private Object playerModel;

        public LanternTileEntity() {}

        @Override
        public void tick() {
            ticks++;
        }

        /** One player per lantern, made again when the model is (after a pack reload). */
        @Environment(EnvType.CLIENT)
        GltfAnimationPlayer player(GltfModel model) {
            if (player == null || playerModel != model) {
                player = new GltfAnimationPlayer(model);
                playerModel = model;
            }
            return (GltfAnimationPlayer) player;
        }

        @Override
        public void readAdditionalData(CompoundTag tag) {}

        @Override
        public void writeAdditionalData(CompoundTag tag) {}
    }
}
