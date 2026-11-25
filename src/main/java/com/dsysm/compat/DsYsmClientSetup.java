package com.dsysm.compat;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * 只在客户端加载：注册配置文件，并把 NeoForge 自带配置界面挂到模组列表的「配置」按钮上。
 */
final class DsYsmClientSetup {
    private DsYsmClientSetup() {
    }

    static void register(final ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, DsYsmConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        DsYsmAnimationCompat.register();
    }
}
