package com.dsysm.compat;

import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

/**
 * Only enables the mixins when both Dragon Survival and Yes Steve Model are loaded, and only for the
 * Dragon Survival builds whose player-render hook has the shape the mixins were written against.
 * This keeps the patch a no-op (and crash-free) on other mod sets / other Dragon Survival versions.
 */
public final class DsYsmMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("ds_ysm_compat");

    private static final String DRAGONSURVIVAL = "dragonsurvival";
    private static final String YES_STEVE_MODEL = "yes_steve_model";

    /** Dragon Survival 2.0.70 - 2.0.79 known to have this render hook. */
    private static final String SUPPORTED_DRAGONSURVIVAL_PREFIX = "2.0.7";

    private static boolean enabled;
    private static boolean ysmEmoteWheelEnabled;

    static boolean isEnabled() {
        return enabled;
    }

    @Override
    public void onLoad(final String mixinPackage) {
        enabled = false;
        ysmEmoteWheelEnabled = false;

        try {
            LoadingModList modList = FMLLoader.getLoadingModList();

            if (modList == null) {
                LOGGER.warn("[ds_ysm_compat] Loading mod list is not available yet, mixins disabled");
                return;
            }

            ModFileInfo dragonSurvival = modList.getModFileById(DRAGONSURVIVAL);
            ModFileInfo yesSteveModel = modList.getModFileById(YES_STEVE_MODEL);
            String version = dragonSurvival == null ? null : dragonSurvival.versionString();
            boolean supportedVersion = version != null && version.startsWith(SUPPORTED_DRAGONSURVIVAL_PREFIX);

            if (dragonSurvival != null && yesSteveModel != null && supportedVersion) {
                enabled = true;
                ysmEmoteWheelEnabled = "2.6.5".equals(yesSteveModel.versionString().split("[+-]", 2)[0]);
                LOGGER.info("[ds_ysm_compat] Mixins enabled: player rendering for Dragon Survival {} is now arbitrated by the client config (render_mode)", version);
            } else {
                LOGGER.warn(
                        "[ds_ysm_compat] Mixins disabled (Dragon Survival loaded: {}, version: {}, Yes Steve Model loaded: {}; supported Dragon Survival: {}x)",
                        dragonSurvival != null, version, yesSteveModel != null, SUPPORTED_DRAGONSURVIVAL_PREFIX);
            }
        } catch (Throwable throwable) {
            LOGGER.warn("[ds_ysm_compat] Failed to inspect the loading mod list, mixins stay disabled", throwable);
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
        return enabled && (!mixinClassName.startsWith("com.dsysm.compat.mixin.YsmEmote") || ysmEmoteWheelEnabled);
    }

    @Override
    public void acceptTargets(final Set<String> myTargets, final Set<String> otherTargets) {
        // Nothing to do
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(final String targetClassName, final ClassNode targetClass, final String mixinClassName, final IMixinInfo mixinInfo) {
        // Nothing to do
    }

    @Override
    public void postApply(final String targetClassName, final ClassNode targetClass, final String mixinClassName, final IMixinInfo mixinInfo) {
        // Nothing to do
    }
}
