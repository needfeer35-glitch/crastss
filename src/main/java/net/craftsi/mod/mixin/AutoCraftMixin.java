package net.craftsi.mod.mixin;

import net.craftsi.mod.logic.CraftExecutor;
import net.craftsi.mod.node.NodeGraph;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingScreen.class)
public abstract class AutoCraftMixin {

    @Inject(at = @At("HEAD"), method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V")
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        PlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;

        CraftingScreen self = (CraftingScreen)(Object)this;
        CraftExecutor.tickWithCraftingTable(
            NodeGraph.getInstance(),
            player,
            self.getScreenHandler()
        );
    }
}
