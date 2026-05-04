package net.craftsi.mod.mixin;

import net.craftsi.mod.logic.CraftExecutor;
import net.craftsi.mod.node.NodeGraph;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingScreen.class)
public class AutoCraftMixin {

    @Inject(at = @At("HEAD"), method = "render")
    private void onRenderCraftingScreen(CallbackInfo ci) {
        // автокрафт срабатывает при открытии верстака
    }
}
