package com.dsysm.compat;

import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.function.BiPredicate;

/**
 * 把龙之生存的「飞行」映射成 Yes Steve Model 的 {@code fly}（创造飞行）动画。
 *
 * <p>YSM 2.6.5 的内置动画条件（{@code oo000ooO0O00oo0OO0ooO000}）注册在
 * {@code OoOOo0OOoOOooo00OO00oo0O} 的静态条件表里，其中 {@code fly} 的判定是
 * {@code player.getAbilities().flying}。龙之生存的飞行不是原版创造飞行，所以这里再注册一个
 * 同名、同优先级（1）的条件：玩家是龙且 DS 处于飞行状态时命中，让 YSM 选到 {@code fly}。
 *
 * <p>只追加条件，不改 YSM 的方法；所有类名/方法名都是 2.6.5 的混淆产物，因此按 YSM 版本门控，
 * 反射失败时只打日志、不影响渲染。</p>
 */
public final class DsYsmAnimationCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger("ds_ysm_compat");

    private static final String SUPPORTED_YES_STEVE_MODEL_PREFIX = "2.6.5";
    private static final String CONDITION_REGISTRY = "com.elfmcys.yesstevemodel.OoOOo0OOoOOooo00OO00oo0O";
    private static final String CONDITION = "com.elfmcys.yesstevemodel.O0O00oOooOo000oo00OoO0Oo";
    private static final String ANIMATION_OPTIONS = "com.elfmcys.yesstevemodel.ooOoOOooOoooOoooo0oO0Oo0";
    /** {@code oo000ooO0O00oo0OO0ooO000} 里 fly 条件用的默认选项（枚举常量名）。 */
    private static final String DEFAULT_OPTIONS = "oOo0OO0O0o000OO0O000oo0o";
    private static final String REGISTER_METHOD = "oOo0OO0O0o000OO0O000oo0o";
    private static final String ANIMATION_NAME = "fly";
    private static final int ANIMATION_PRIORITY = 1;

    private DsYsmAnimationCompat() {
    }

    static void register() {
        final LoadingModList modList = FMLLoader.getLoadingModList();
        final ModFileInfo yesSteveModel = modList == null ? null : modList.getModFileById("yes_steve_model");
        final String version = yesSteveModel == null ? null : yesSteveModel.versionString();

        if (version == null || !version.startsWith(SUPPORTED_YES_STEVE_MODEL_PREFIX)) {
            LOGGER.info(
                    "[ds_ysm_compat] Yes Steve Model version {} is not {}x; dragon flight animation mapping is disabled",
                    version, SUPPORTED_YES_STEVE_MODEL_PREFIX);
            return;
        }

        try {
            final ClassLoader loader = DsYsmAnimationCompat.class.getClassLoader();
            final Class<?> registryClass = Class.forName(CONDITION_REGISTRY, false, loader);
            final Class<?> conditionClass = Class.forName(CONDITION, false, loader);
            final Class<?> optionsClass = Class.forName(ANIMATION_OPTIONS, false, loader);

            final Object options = optionsClass.getField(DEFAULT_OPTIONS).get(null);
            final Constructor<?> constructor = conditionClass.getConstructor(
                    String.class, optionsClass, int.class, BiPredicate.class);

            final BiPredicate<Player, Object> isDragonFlying = (player, context) ->
                    player != null && DsYsmConfig.dragonFlightAnimation() && DsYsmHooks.isDragonFlying(player);

            final Object condition = constructor.newInstance(
                    ANIMATION_NAME, options, ANIMATION_PRIORITY, isDragonFlying);

            registryClass.getMethod(REGISTER_METHOD, conditionClass).invoke(null, condition);

            LOGGER.info("[ds_ysm_compat] Dragon flight now maps to Yes Steve Model's `fly` animation (YSM {})", version);
        } catch (final ClassNotFoundException exception) {
            LOGGER.info("[ds_ysm_compat] Yes Steve Model animation condition classes not found; flight animation mapping is disabled");
        } catch (final Throwable throwable) {
            LOGGER.warn("[ds_ysm_compat] Failed to register the dragon flight animation condition", throwable);
        }
    }
}
