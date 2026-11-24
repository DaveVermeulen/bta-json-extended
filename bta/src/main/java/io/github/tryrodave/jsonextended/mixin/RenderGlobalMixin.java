package io.github.tryrodave.jsonextended.mixin;

import io.github.tryrodave.jsonextended.tileentity.TileEntityBatch;
import net.minecraft.client.render.RenderGlobal;
import net.minecraft.client.render.camera.ICamera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the tile entity model meshes queued during BTA's tile entity loop, all together (see TileEntityBatch). */
@Mixin(value = RenderGlobal.class, remap = false)
public abstract class RenderGlobalMixin {
    @Inject(method = "renderEntities", at = @At("RETURN"))
    private void jsonextended$drawTileEntityBatch(ICamera camera, float partialTick, CallbackInfo ci) {
        TileEntityBatch.flush();
    }
}
