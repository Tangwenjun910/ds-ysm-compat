package com.dsysm.compat.mixin;

import com.dsysm.compat.DsYsmHooks;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dragon Survival 的 {@code ClientDragonRenderer#renderDragon(RenderPlayerEvent.Pre)} 会先取消原版玩家渲染、
 * 再自己画 GeckoLib 龙模型。它原本注册在 {@code NORMAL} 优先级，和 Yes Steve Model 的监听器同级，
 * 谁先注册谁赢（取决于 mod 加载顺序）。
 *
 * <p>这里把它从原注册位置摘掉，改由 {@link DsYsmHooks} 按客户端配置在指定优先级调用：
 * DS 优先用 {@code HIGHEST}、自动模式用 {@code LOWEST}、YSM 优先则完全不调用。
 * 我们自己发起的调用通过 {@link DsYsmHooks#isReplayingDsRender()} 识别并放行。</p>
 *
 * <p>本 mixin 只在 {@code DsYsmMixinPlugin} 判定「DS 2.0.7x + YSM 同时存在」时才会被应用。</p>
 */
@Mixin(targets = "by.dragonsurvivalteam.dragonsurvival.client.render.ClientDragonRenderer", remap = false)
public class ClientDragonRendererMixin {
    @Inject(
            method = "renderDragon(Lnet/neoforged/neoforge/client/event/RenderPlayerEvent$Pre;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void dsYsmCompat$arbitratePlayerRendering(final RenderPlayerEvent.Pre event, final CallbackInfo callback) {
        if (!DsYsmHooks.isReplayingDsRender()) {
            callback.cancel();
        }
    }
}
