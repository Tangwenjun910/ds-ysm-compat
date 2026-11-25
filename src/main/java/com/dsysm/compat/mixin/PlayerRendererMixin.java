package com.dsysm.compat.mixin;

import com.dsysm.compat.DsYsmConfig;
import com.dsysm.compat.DsYsmHooks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.entity.player.PlayerRenderer", remap = false)
public class PlayerRendererMixin {
    // NeoForge posts RenderArmEvent in renderLeftHand/renderRightHand before calling renderHand.
    @Inject(
            method = "renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void dsYsmCompat$hideVanillaDragonArm(final PoseStack poseStack, final MultiBufferSource buffers,
                                                final int light, final AbstractClientPlayer player,
                                                final ModelPart arm, final ModelPart sleeve, final CallbackInfo callback) {
        if (DsYsmConfig.mode() != DsYsmConfig.RenderMode.DS_PRIORITY
                && player == Minecraft.getInstance().player && DsYsmHooks.isDragon(player)) {
            callback.cancel();
        }
    }
}
