package com.dsysm.compat;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.TranslatableEnum;

import java.util.Locale;

/**
 * 客户端配置。通过 NeoForge 自带的配置界面（模组列表 -> 配置）修改，改完立即生效。
 */
public final class DsYsmConfig {
    public enum RenderMode implements TranslatableEnum {
        /** YSM 真的接管了模型时用 YSM，否则（YSM 关了自己的模型 / 没有模型 / 不在第三人称）交给 DS。 */
        AUTO,
        /** 永远由 Yes Steve Model 画玩家。 */
        YSM_PRIORITY,
        /** 龙形态永远由 Dragon Survival 画龙。 */
        DS_PRIORITY;

        @Override
        public Component getTranslatedName() {
            return Component.translatable("ds_ysm_compat.configuration.render_mode." + name().toLowerCase(Locale.ROOT));
        }
    }

    private static final ModConfigSpec.EnumValue<RenderMode> RENDER_MODE;
    private static final ModConfigSpec.BooleanValue DRAGON_FLIGHT_ANIMATION;

    public static final ModConfigSpec SPEC;

    static {
        final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        RENDER_MODE = builder
                .comment(
                        "Dragon Survival 和 Yes Steve Model 同时安装时，由谁渲染玩家模型。",
                        "AUTO: 只有 YSM 真的替换了模型（YSM 自己的模型开关打开、模型可用且处于第三人称）时才用 YSM；否则交给 DS 画龙。",
                        "YSM_PRIORITY: 永远由 Yes Steve Model 接管玩家渲染。",
                        "DS_PRIORITY: 龙形态永远由 Dragon Survival 渲染，YSM 的人形模型被隐藏。")
                .translation("ds_ysm_compat.configuration.render_mode")
                .defineEnum("render_mode", RenderMode.AUTO);

        DRAGON_FLIGHT_ANIMATION = builder
                .comment(
                        "龙之生存的龙处于飞行状态（翅膀展开、离地）时，让 Yes Steve Model 判定为创造飞行，",
                        "从而播放模型的 fly（创造飞行）动画，而不是跳跃/下落动画。")
                .translation("ds_ysm_compat.configuration.dragon_flight_animation")
                .define("dragon_flight_animation", true);

        SPEC = builder.build();
    }

    private DsYsmConfig() {
    }

    public static RenderMode mode() {
        try {
            return RENDER_MODE.get();
        } catch (final IllegalStateException e) {
            // 配置尚未加载（例如专用服务端或极早期调用），退回默认行为。
            return RenderMode.AUTO;
        }
    }

    public static boolean dragonFlightAnimation() {
        try {
            return DRAGON_FLIGHT_ANIMATION.get();
        } catch (final IllegalStateException e) {
            return true;
        }
    }
}
