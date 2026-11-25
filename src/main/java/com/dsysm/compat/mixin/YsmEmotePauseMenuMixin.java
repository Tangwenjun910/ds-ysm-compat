package com.dsysm.compat.mixin;

import com.dsysm.compat.DsYsmEmoteCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;
import java.util.function.Consumer;

@Mixin(targets = "com.elfmcys.yesstevemodel.o0O0Ooo0O00OooO00OoO00OO", remap = false)
public class YsmEmotePauseMenuMixin {
    @Redirect(
            method = "oOo0OO0O0o000OO0O000oo0o(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/gui/components/Button;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Optional;ifPresent(Ljava/util/function/Consumer;)V")
    )
    private static <T> void dsYsmCompat$openDragonMenuWhenWheelUnavailable(
            final Optional<T> model, final Consumer<T> openWheel) {
        final Screen previous = Minecraft.getInstance().screen;
        model.ifPresent(openWheel);
        DsYsmEmoteCompat.openDragonMenuIfUnchanged(previous);
    }
}
