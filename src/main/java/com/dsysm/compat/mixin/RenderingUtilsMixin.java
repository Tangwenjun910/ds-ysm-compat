package com.dsysm.compat.mixin;

import com.dsysm.compat.DsYsmRenderState;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DS 用 {@code RenderingUtils#isFirstPerson(player)} 决定第一人称时隐藏龙模型的 {@code Neck} 骨骼。
 *
 * <p>这个方法只判断“相机是不是第一人称”，所以在纸娃娃/HUD 预览（同样会渲染本地玩家）时也会返回 true，
 * 导致预览里的龙没有头。这里只在真正处于世界渲染（DS 的第一人称龙身通道）时才报告第一人称，
 * 其他渲染场景一律返回 false。</p>
 */
@Mixin(targets = "by.dragonsurvivalteam.dragonsurvival.client.util.RenderingUtils", remap = false)
public class RenderingUtilsMixin {
    @Inject(
            method = "isFirstPerson(Lnet/minecraft/world/entity/player/Player;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void dsYsmCompat$onlyInWorldRender(final Player player, final CallbackInfoReturnable<Boolean> callback) {
        if (!DsYsmRenderState.isInWorldRender()) {
            callback.setReturnValue(false);
        }
    }
}
