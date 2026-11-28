package com.dsysm.compat.mixin;

import com.dsysm.compat.DsYsmConfig;
import com.dsysm.compat.DsYsmEmoteCompat;
import com.dsysm.compat.DsYsmRenderSync;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dragon Survival 用 {@code ClientProxy#dragonRenderingWasCancelled(player)} 表示“这一帧没有画龙模型”。
 * 阴影偏移、render offset、第一人称龙身体、飞行相机等几处补丁都靠这个返回值决定是否要退回原版行为。
 *
 * <p>只有「YSM 优先」时 DS 的渲染监听器完全不会执行，龙实体上的状态不会被更新，
 * 所以这里统一返回 true，让那些补丁一起退回。
 * 「DS 优先」「自动」模式下 DS 的监听器会正常执行（哪怕是被 {@code DsYsmHooks} 调用），
 * 状态由它自己维护，不再强制覆盖。</p>
 *
 * <p>本 mixin 只在 {@code DsYsmMixinPlugin} 判定「DS 2.0.7x + YSM 同时存在」时才会被应用。</p>
 */
@Mixin(targets = "by.dragonsurvivalteam.dragonsurvival.util.proxy.ClientProxy", remap = false)
public class ClientProxyMixin {
    @Inject(
            method = "stopEmote(Lnet/minecraft/world/entity/player/Player;Lby/dragonsurvivalteam/dragonsurvival/registry/dragon/body/emotes/DragonEmote;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void dsYsmCompat$stopSyncedDragonEmote(final Player player, @Coerce final Object emote,
                                                 final CallbackInfo callback) {
        if (DsYsmEmoteCompat.stopSyncedEmote(player, emote)) {
            callback.cancel();
        }
    }

    @Inject(
            method = "dragonRenderingWasCancelled(Lnet/minecraft/world/entity/player/Player;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void dsYsmCompat$pretendDragonWasNotRendered(final Player player, final CallbackInfoReturnable<Boolean> callback) {
        if (DsYsmRenderSync.modeFor(player) == DsYsmConfig.RenderMode.YSM_PRIORITY) {
            callback.setReturnValue(true);
        }
    }
}
