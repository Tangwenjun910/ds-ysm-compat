package com.dsysm.compat.mixin;

import com.bawnorton.mixinsquared.TargetHandler;
import com.dsysm.compat.DsYsmConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.renderer.ItemInHandRenderer", priority = 1500, remap = false)
public class ItemInHandRendererMixin {
    private static final String DS_MIXIN =
            "by.dragonsurvivalteam.dragonsurvival.mixins.client.ItemInHandRendererMixin";

    @TargetHandler(mixin = DS_MIXIN, name = "dragonSurvival$skipRender")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, require = 0)
    private void dsYsmCompat$allowArmEvent(final CallbackInfo callback) {
        if (DsYsmConfig.mode() != DsYsmConfig.RenderMode.DS_PRIORITY) {
            // End DS's handler so the arm transforms and NeoForge's RenderArmEvent can run.
            callback.cancel();
        }
    }

    @TargetHandler(mixin = DS_MIXIN, name = "dragonSurvival$skipRenderWhenHoldingMaps")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, require = 0)
    private void dsYsmCompat$allowMapArmEvents(final boolean invisible,
                                              final CallbackInfoReturnable<Boolean> callback) {
        if (DsYsmConfig.mode() != DsYsmConfig.RenderMode.DS_PRIORITY) {
            callback.setReturnValue(invisible);
        }
    }
}
