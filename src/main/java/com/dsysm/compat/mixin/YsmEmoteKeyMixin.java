package com.dsysm.compat.mixin;

import com.dsysm.compat.DsYsmEmoteCompat;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;
import java.util.function.Consumer;

@Mixin(targets = "com.elfmcys.yesstevemodel.OoOOO0OOoO0OOOOOOOOooOo0", remap = false)
public class YsmEmoteKeyMixin {
    @Redirect(
            method = "oOo0OO0O0o000OO0O000oo0o(Lnet/neoforged/neoforge/client/event/InputEvent$Key;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Optional;ifPresent(Ljava/util/function/Consumer;)V")
    )
    private static <T> void dsYsmCompat$openDragonMenuWhenWheelUnavailable(
            final Optional<T> model, final Consumer<T> openWheel) {
        final boolean noScreen = Minecraft.getInstance().screen == null;
        model.ifPresent(openWheel);
        if (noScreen) {
            DsYsmEmoteCompat.openDragonMenuIfUnchanged(null);
        }
    }
}
