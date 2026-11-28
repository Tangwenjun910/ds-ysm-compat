package com.dsysm.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;

/**
 * 只在客户端加载：注册配置文件，并把 NeoForge 自带配置界面挂到模组列表的「配置」按钮上。
 */
final class DsYsmClientSetup {
    private static DsYsmConfig.RenderMode lastSentMode;

    private DsYsmClientSetup() {
    }

    static void register(final ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, DsYsmConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        if (DsYsmMixinPlugin.isEnabled()) {
            DsYsmAnimationCompat.register();
            NeoForge.EVENT_BUS.addListener(DsYsmClientSetup::sendRenderMode);
            NeoForge.EVENT_BUS.addListener(DsYsmClientSetup::onLogin);
            NeoForge.EVENT_BUS.addListener(DsYsmClientSetup::onLogout);
        }
    }

    private static void sendRenderMode(final ClientTickEvent.Post event) {
        final Minecraft minecraft = Minecraft.getInstance();
        final ClientPacketListener connection = minecraft.getConnection();
        final DsYsmConfig.RenderMode mode = DsYsmConfig.mode();
        if (minecraft.player != null && connection != null && lastSentMode != mode
                && DsYsmRenderSync.canSendPreference((ICommonPacketListener) connection)) {
            DsYsmRenderSync.sendPreference(mode);
            lastSentMode = mode;
        }
    }

    private static void onLogin(final ClientPlayerNetworkEvent.LoggingIn event) {
        resetConnectionState();
    }

    private static void onLogout(final ClientPlayerNetworkEvent.LoggingOut event) {
        resetConnectionState();
    }

    private static void resetConnectionState() {
        lastSentMode = null;
        DsYsmRenderSync.clearClientModes();
    }
}
