package com.dsysm.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家渲染仲裁。
 *
 * <p>DS 的 {@code ClientDragonRenderer#renderDragon} 监听器被 Mixin 从原注册位置摘掉，
 * 改由这里按配置在指定优先级上调用：</p>
 * <ul>
 *   <li>{@code DS_PRIORITY}：在 {@code HIGHEST} 调用，DS 先取消事件并画龙，YSM 的
 *       {@code receiveCanceled=false} 监听器随后被事件总线跳过；</li>
 *   <li>{@code AUTO}：在 {@code LOWEST} 调用，此时 YSM 的监听器已经跑过。若 YSM 真的接管了模型，
 *       事件已被它取消，DS 的「事件被取消就让位」分支生效；若 YSM 没接管（关了自己的模型、没有可用模型、
 *       spectator、第一人称等），事件仍是未取消状态，DS 正常画龙。这样无需读 YSM 的配置/内部类，
 *       用事件本身的取消状态就能判断「YSM 是否替换了模型」；</li>
 *   <li>{@code YSM_PRIORITY}：不调用 DS，保持 DS 完全让位。</li>
 * </ul>
 */
@EventBusSubscriber(modid = DsYsmCompat.MOD_ID, value = Dist.CLIENT)
public final class DsYsmHooks {
    private static final Logger LOGGER = LoggerFactory.getLogger("ds_ysm_compat");

    private static final String CLIENT_DRAGON_RENDERER =
            "by.dragonsurvivalteam.dragonsurvival.client.render.ClientDragonRenderer";
    private static final String DRAGON_STATE_PROVIDER =
            "by.dragonsurvivalteam.dragonsurvival.common.capability.DragonStateProvider";
    private static final String SERVER_FLIGHT_HANDLER =
            "by.dragonsurvivalteam.dragonsurvival.server.handlers.ServerFlightHandler";
    /** YSM 的渲染器都在这个包下；只按包名过滤，不依赖它混淆后的类名。 */
    private static final String YSM_RENDERER_PACKAGE = "com.elfmcys.yesstevemodel.";

    /** 延迟到首次渲染时解析，避免在模组构造阶段提前加载 Dragon Survival 的类。 */
    private static Method renderDragonMethod;
    private static Method isDragonMethod;
    private static Method isFlyingMethod;
    private static boolean dsMethodsResolved;

    /** 防止 Mixin 把「我们自己发起的调用」也短路掉。渲染线程单线程，普通静态字段即可。 */
    private static boolean replayingDsHandler;
    private static boolean invokeFailureLogged;
    private static boolean dragonCheckFailureLogged;
    private static boolean dragonFlightCheckFailureLogged;

    /**
     * 玩家上一次「非第一人称龙身通道」渲染的归属：true = YSM 接管，false = DS 接管。
     *
     * <p>DS 的第一人称龙身通道发生在 YSM 监听器之前，此时无法用事件取消状态判断 YSM 会不会接管，
     * 所以沿用最近一次正常渲染（第三人称/纸娃娃）的结论。没有记录时默认认为 YSM 接管，
     * 避免「用着 YSM 模型却在第一人称冒出龙身」。</p>
     */
    private static final Map<UUID, Boolean> YSM_OWNS_PLAYER = new ConcurrentHashMap<>();
    private static final boolean DEFAULT_YSM_OWNS = true;

    private DsYsmHooks() {
    }

    public static boolean isReplayingDsRender() {
        return replayingDsHandler;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderPlayerPreHighest(final RenderPlayerEvent.Pre event) {
        if (!DsYsmMixinPlugin.isEnabled()) {
            return;
        }
        final Player player = event.getEntity();
        final DsYsmConfig.RenderMode mode = DsYsmRenderSync.modeFor(player);

        if (isFirstPersonBodyPass(player)) {
            if (mode == DsYsmConfig.RenderMode.DS_PRIORITY
                    || (mode == DsYsmConfig.RenderMode.AUTO && !ysmOwns(player))) {
                invokeRenderDragon(event);
            } else {
                // YSM 接管该玩家：这个通道里不要画龙身，同时取消事件以跳过 YSM 的第三人称模型
                //（它在第一人称会把没剔除的头挡在视野里）。第一人称手臂/手走各自的事件，不受影响。
                event.setCanceled(true);
            }
            return;
        }

        if (mode == DsYsmConfig.RenderMode.DS_PRIORITY) {
            rememberYsmOwnership(player, false);
            invokeRenderDragon(event);
        }
    }

    // receiveCanceled = true：YSM 接管时会在 NORMAL 取消事件，这里必须仍然收到才能记录「YSM 已接管」
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onRenderPlayerPreLowest(final RenderPlayerEvent.Pre event) {
        if (!DsYsmMixinPlugin.isEnabled()) {
            return;
        }
        final Player player = event.getEntity();

        if (isFirstPersonBodyPass(player)) {
            return;
        }

        final DsYsmConfig.RenderMode mode = DsYsmRenderSync.modeFor(player);

        if (mode == DsYsmConfig.RenderMode.AUTO) {
            // 此时 YSM 的 NORMAL 监听器已经跑过：事件被取消 = YSM 接管了这名玩家。
            final boolean ysmRendered = event.isCanceled();
            rememberYsmOwnership(player, ysmRendered);

            if (!ysmRendered) {
                invokeRenderDragon(event);
            }
        } else if (mode == DsYsmConfig.RenderMode.YSM_PRIORITY) {
            rememberYsmOwnership(player, true);
        }
    }

    private static void rememberYsmOwnership(final Player player, final boolean ysmOwns) {
        YSM_OWNS_PLAYER.put(player.getUUID(), ysmOwns);
    }

    private static boolean ysmOwns(final Player player) {
        return YSM_OWNS_PLAYER.getOrDefault(player.getUUID(), DEFAULT_YSM_OWNS);
    }

    static Boolean lastYsmOwnership(final Player player) {
        return YSM_OWNS_PLAYER.get(player.getUUID());
    }

    public static boolean shouldSkipDragonShadowOffset(final Player player) {
        if (DsYsmRenderSync.modeFor(player) != DsYsmConfig.RenderMode.AUTO) {
            return false;
        }

        final Boolean ysmOwnsPlayer = YSM_OWNS_PLAYER.get(player.getUUID());
        return ysmOwnsPlayer != null ? ysmOwnsPlayer : DEFAULT_YSM_OWNS && isFirstPersonBodyPass(player);
    }

    /**
     * {@code DS_PRIORITY} 下 DS 自己有不取消事件的分支（BetterCombat 攻击中、noDragonModelRendering），
     * 这些情况下 YSM 仍会照常画人形模型。这里在 YSM 自己的渲染器内部拦掉它的 {@code RenderLivingEvent.Pre}，
     * 让龙形态下只保留 DS 想要的结果。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void suppressYsmModelForDragons(final RenderLivingEvent.Pre<?, ?> event) {
        if (!DsYsmMixinPlugin.isEnabled() || !(event.getEntity() instanceof Player player)) {
            return;
        }

        if (DsYsmRenderSync.modeFor(player) != DsYsmConfig.RenderMode.DS_PRIORITY && !isFirstPersonBodyPass(player)) {
            return;
        }

        if (!event.getRenderer().getClass().getName().startsWith(YSM_RENDERER_PACKAGE)) {
            return;
        }

        if (isDragon(player)) {
            event.setCanceled(true);
        }
    }

    /**
     * DS 的第一人称龙身是在 {@code LevelRenderer#renderLevel} 里手动 {@code renderEntity(本地玩家)} 画出来的。
     * 这是第一人称下本地玩家唯一会进入 {@code RenderPlayerEvent.Pre} 的时机（原版实体循环会跳过相机实体），
     * 纸娃娃/HUD 预览发生在 renderLevel 之外，因此可以用「世界渲染中 + 本地玩家 + 第一人称」唯一识别。
     */
    private static boolean isFirstPersonBodyPass(final Player player) {
        if (!DsYsmRenderState.isInWorldRender()) {
            return false;
        }

        final Minecraft minecraft = Minecraft.getInstance();

        return player == minecraft.player
                && minecraft.options.getCameraType().isFirstPerson()
                && !minecraft.gameRenderer.getMainCamera().isDetached();
    }

    private static void invokeRenderDragon(final RenderPlayerEvent.Pre event) {
        final Method renderDragon = renderDragonMethod();

        if (renderDragon == null) {
            return;
        }

        final boolean previous = replayingDsHandler;
        replayingDsHandler = true;

        try {
            renderDragon.invoke(null, event);
        } catch (final InvocationTargetException exception) {
            logInvokeFailure(exception.getCause());
        } catch (final Throwable throwable) {
            logInvokeFailure(throwable);
        } finally {
            replayingDsHandler = previous;
        }
    }

    public static boolean isDragon(final Player player) {
        final Method isDragon = isDragonMethod();

        if (isDragon == null) {
            return false;
        }

        try {
            return Boolean.TRUE.equals(isDragon.invoke(null, player));
        } catch (final Throwable throwable) {
            if (!dragonCheckFailureLogged) {
                dragonCheckFailureLogged = true;
                LOGGER.error("[ds_ysm_compat] Failed to query the Dragon Survival dragon state", throwable);
            }
            return false;
        }
    }

    private static void logInvokeFailure(final Throwable throwable) {
        if (invokeFailureLogged) {
            return;
        }

        invokeFailureLogged = true;
        LOGGER.error("[ds_ysm_compat] Failed to invoke Dragon Survival's player renderer", throwable);
    }

    private static Method renderDragonMethod() {
        resolveDsMethods();
        return renderDragonMethod;
    }

    private static Method isDragonMethod() {
        resolveDsMethods();
        return isDragonMethod;
    }

    private static Method isFlyingMethod() {
        resolveDsMethods();
        return isFlyingMethod;
    }

    private static void resolveDsMethods() {
        if (dsMethodsResolved) {
            return;
        }

        dsMethodsResolved = true;
        renderDragonMethod = findStaticMethod(CLIENT_DRAGON_RENDERER, "renderDragon", RenderPlayerEvent.Pre.class);
        isDragonMethod = findStaticMethod(DRAGON_STATE_PROVIDER, "isDragon", Entity.class);
        isFlyingMethod = findStaticMethod(SERVER_FLIGHT_HANDLER, "isFlying", Player.class);
    }

    /**
     * 龙之生存的「飞行」状态：翅膀展开、离地、不在水里/熔岩里/载具上。
     * 供 YSM 的 {@code fly}（创造飞行）动画条件使用。
     */
    public static boolean isDragonFlying(final Player player) {
        if (!isDragon(player)) {
            return false;
        }

        final Method isFlying = isFlyingMethod();

        if (isFlying == null) {
            return false;
        }

        try {
            return Boolean.TRUE.equals(isFlying.invoke(null, player));
        } catch (final Throwable throwable) {
            if (!dragonFlightCheckFailureLogged) {
                dragonFlightCheckFailureLogged = true;
                LOGGER.error("[ds_ysm_compat] Failed to query the Dragon Survival flight state", throwable);
            }
            return false;
        }
    }

    private static Method findStaticMethod(final String className, final String methodName,
                                           final Class<?>... parameterTypes) {
        try {
            // 只加载不初始化，避免在模组构造阶段触发目标模组的静态初始化。
            final Class<?> target = Class.forName(className, false, DsYsmHooks.class.getClassLoader());
            final Method method = target.getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (final ClassNotFoundException exception) {
            LOGGER.info("[ds_ysm_compat] {} is not installed, related handling disabled", className);
            return null;
        } catch (final Throwable throwable) {
            LOGGER.warn("[ds_ysm_compat] {}#{}({}) is unavailable, related handling disabled",
                    className, methodName, parameterTypes.length, throwable);
            return null;
        }
    }
}
