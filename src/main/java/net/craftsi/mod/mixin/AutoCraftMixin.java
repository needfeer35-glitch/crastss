package net.craftsi.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

@Mixin(CraftingScreen.class)
public class AutoCraftMixin {

    @Inject(at = @At("HEAD"), method = "render(Lnet/minecraft/client/gui/DrawContext;IILnet/minecraft/client/render/RenderTickCounter;)V")
    private void onOpen(DrawContext context, int mouseX, int mouseY, RenderTickCounter tickCounter, CallbackInfo ci) {
        // хук для автокрафта при открытии верстака
    }
}
