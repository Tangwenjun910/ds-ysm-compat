package com.dsysm.compat.mixin;

import com.dsysm.compat.DsYsmEmoteCompat;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(targets = "com.elfmcys.yesstevemodel.Oo0Oo0Oooo00OoOOOO0O00o0", remap = false)
public class YsmEmoteWheelMixin {
    @Unique
    private Map<String, String> dsYsmCompat$dragonEntries;

    @Inject(
            method = {
                    "<init>(Ljava/lang/String;Lcom/elfmcys/yesstevemodel/o0o0o00oo0OO0o000OOo0oo0;Lcom/elfmcys/yesstevemodel/OoO0oo0o0o0oOoo0oOOO0Ooo;)V",
                    "<init>(Ljava/util/Map;Ljava/util/Map;Lcom/elfmcys/yesstevemodel/o0o0o00oo0OO0o000OOo0oo0;Lcom/elfmcys/yesstevemodel/OoO0oo0o0o0oOoo0oOOO0Ooo;)V"
            },
            at = @At("RETURN"),
            require = 2
    )
    private void dsYsmCompat$replaceWheelEntries(final CallbackInfo callback) {
        dsYsmCompat$dragonEntries = DsYsmEmoteCompat.installDragonEntries((Screen) (Object) this);
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
            ((Screen) (Object) this).onClose();
        }
    }
}
