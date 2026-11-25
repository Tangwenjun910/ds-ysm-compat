package com.dsysm.compat.mixin;

import com.dsysm.compat.DsYsmRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 给世界渲染打一个“在 renderLevel 里”的标志，供第一人称龙身识别和纸娃娃渲染区分使用。
 */
@Mixin(targets = "net.minecraft.client.renderer.LevelRenderer", remap = false)
public class LevelRendererStateMixin {
    @Inject(
            method = "renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
            at = @At("HEAD"),
            remap = false
    )
    private void dsYsmCompat$enterWorldRender(final DeltaTracker deltaTracker, final boolean renderBlockOutline,
                                              final Camera camera, final GameRenderer gameRenderer,
                                              final LightTexture lightTexture, final Matrix4f frustumMatrix,
                                              final Matrix4f projectionMatrix, final CallbackInfo callback) {
        DsYsmRenderState.setInWorldRender(true);
    }

    @Inject(
            method = "renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
            at = @At("RETURN"),
            remap = false
    )
    private void dsYsmCompat$exitWorldRender(final DeltaTracker deltaTracker, final boolean renderBlockOutline,
                                             final Camera camera, final GameRenderer gameRenderer,
                                             final LightTexture lightTexture, final Matrix4f frustumMatrix,
                                             final Matrix4f projectionMatrix, final CallbackInfo callback) {
        DsYsmRenderState.setInWorldRender(false);
    }
}
