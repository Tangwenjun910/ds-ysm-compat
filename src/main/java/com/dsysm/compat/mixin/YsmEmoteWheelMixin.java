package com.dsysm.compat.mixin;

import com.dsysm.compat.DsYsmEmoteCompat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

@Mixin(targets = "com.elfmcys.yesstevemodel.Oo0Oo0Oooo00OoOOOO0O00o0", remap = false)
public abstract class YsmEmoteWheelMixin extends Screen {
    @Shadow @Final
    private static LinkedList<?> OoooO0OO0000O00oo0Oo00OO;

    protected YsmEmoteWheelMixin(final Component title) {
        super(title);
    }

    @Unique
    private Map<String, String> dsYsmCompat$dragonEntries;

    @Unique
    private Set<String> dsYsmCompat$playingEmotes = Set.of();

    @Inject(
            method = {
                    "<init>(Ljava/lang/String;Lcom/elfmcys/yesstevemodel/o0o0o00oo0OO0o000OOo0oo0;Lcom/elfmcys/yesstevemodel/OoO0oo0o0o0oOoo0oOOO0Ooo;)V",
                    "<init>(Ljava/util/Map;Ljava/util/Map;Lcom/elfmcys/yesstevemodel/o0o0o00oo0OO0o000OOo0oo0;Lcom/elfmcys/yesstevemodel/OoO0oo0o0o0oOoo0oOOO0Ooo;)V"
            },
            at = @At("RETURN"),
            require = 2
    )
    private void dsYsmCompat$replaceWheelEntries(final CallbackInfo callback) {
        dsYsmCompat$dragonEntries = DsYsmEmoteCompat.installDragonEntries(this);
    }

    @Inject(method = "init()V", at = @At("RETURN"))
    private void dsYsmCompat$initializePlaybackStatus(final CallbackInfo callback) {
        if (!dsYsmCompat$dragonEntries.isEmpty()) {
            dsYsmCompat$playingEmotes = DsYsmEmoteCompat.playingDragonEmoteKeys();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!dsYsmCompat$dragonEntries.isEmpty()) {
            dsYsmCompat$playingEmotes = DsYsmEmoteCompat.playingDragonEmoteKeys();
        }
    }

    @Inject(method = "oOo0OO0O0o000OO0O000oo0o(Lnet/minecraft/client/gui/GuiGraphics;III)V",
            at = @At("HEAD"), cancellable = true)
    private void dsYsmCompat$hideYsmKeyHint(final GuiGraphics graphics, final int index, final int x, final int y,
                                          final CallbackInfo callback) {
        if (!dsYsmCompat$dragonEntries.isEmpty()) {
            callback.cancel();
        }
    }

    @Inject(
            method = "oOoo00O0o0oO0o0oO00OO0O0(Lnet/minecraft/client/gui/GuiGraphics;)V",
            at = @At(value = "INVOKE", shift = At.Shift.AFTER,
                    target = "Lcom/elfmcys/yesstevemodel/Oo0Oo0Oooo00OoOOOO0O00o0;oOo0OO0O0o000OO0O000oo0o(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/network/chat/MutableComponent;IIZ)V"),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void dsYsmCompat$drawPlaybackStatus(final GuiGraphics graphics, final CallbackInfo callback,
                                                final float angle, final int remaining, final int radius,
                                                final int pageSlot, final int index, final int x, final int y) {
        final Component status = dsYsmCompat$slotPlaybackStatus(index);
        if (status != null) {
            // YSM moves the names down a line on later pages and in nested menus.
            final int labelOffset = index >= 8 || OoooO0OO0000O00oo0Oo00OO.size() > 1 ? font.lineHeight : 0;
            graphics.drawCenteredString(font, status, x, y + 4 + labelOffset, 0xFFFFFF);
        }
    }

    @Unique
    private Component dsYsmCompat$slotPlaybackStatus(final int index) {
        final String emote = dsYsmCompat$dragonEntries.get("ds_ysm_compat." + index);
        if (emote == null) {
            return null;
        }
        return dsYsmCompat$playingEmotes.contains(emote)
                ? Component.translatable("ds_ysm_compat.emote.playing").withColor(0x80FF98)
                : Component.translatable("ds_ysm_compat.emote.idle").withColor(0xC2C2C2);
    }

    @Inject(method = "OOo0o0000Ooo0o00OO0oOOoO(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void dsYsmCompat$playDragonEmote(final String slot, final CallbackInfo callback) {
        if (dsYsmCompat$dragonEntries.isEmpty()) {
            return;
        }
        final String emote = dsYsmCompat$dragonEntries.get(slot);
        if (emote != null) {
            callback.cancel();
            DsYsmEmoteCompat.select(slot, emote);
            onClose();
        }
    }
}
