package com.dsysm.compat.mixin;

import com.bawnorton.mixinsquared.TargetHandler;
import com.dsysm.compat.DsYsmHooks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityRenderDispatcher.class, priority = 1500, remap = false)
public class EntityRenderDispatcherMixin {
    @TargetHandler(
            mixin = "by.dragonsurvivalteam.dragonsurvival.mixins.client.EntityRenderDispatcherMixin",
            name = "dragonSurvival$modifyShadow"
    )
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, require = 0)
    private static void dsYsmCompat$keepPlayerShadowPosition(final PoseStack poseStack,
                                                            final MultiBufferSource buffers,
                                                            final Entity entity, final float weight,
                                                            final float partialTick, final LevelReader level,
                                                            final float size, final CallbackInfo originalCallback,
                                                            final CallbackInfo callback) {
        if (entity instanceof Player player && DsYsmHooks.shouldSkipDragonShadowOffset(player)) {
            // Skip DS's pose translation before it pushes a pose. Vanilla still draws the shadow.
            callback.cancel();
        }
    }
}
