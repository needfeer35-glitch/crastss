package net.craftsi.mod.mixin;

import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class NoBlurMixin {

    @Inject(at = @At("HEAD"), method = "applyBlur", cancellable = true)
    private void cancelBlur(CallbackInfo ci) {
        Screen self = (Screen)(Object)this;
        if (self instanceof net.craftsi.mod.gui.CraftsiScreen) {
            ci.cancel();
        }
    }
}
