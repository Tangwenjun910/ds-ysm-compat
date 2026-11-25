package com.dsysm.compat;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * Dragon Survival x Yes Steve Model compatibility patch.
 *
 * <p>Both mods listen to {@code RenderPlayerEvent.Pre} and cancel the vanilla player render to draw
 * their own model. This patch arbitrates between them according to the client config
 * ({@code AUTO}, {@code YSM_PRIORITY} or {@code DS_PRIORITY}).</p>
 */
@Mod(DsYsmCompat.MOD_ID)
public final class DsYsmCompat {
    public static final String MOD_ID = "ds_ysm_compat";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DsYsmCompat(final IEventBus modEventBus, final ModContainer modContainer) {
        if (FMLEnvironment.dist.isClient()) {
            DsYsmClientSetup.register(modContainer);
        }

        LOGGER.info("[ds_ysm_compat] Loaded. Player render priority: {}", DsYsmConfig.mode());
    }
}
