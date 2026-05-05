package net.craftsi.mod.mixin;

import net.craftsi.mod.logic.CraftExecutor;
import net.craftsi.mod.node.NodeGraph;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingScreen.class)
public abstract class AutoCraftMixin {

    @Inject(at = @At("HEAD"), method = "render")
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        CraftingScreen self = (CraftingScreen)(Object)this;
        PlayerEntity player = net.minecraft.client.MinecraftClient.getInstance().player;
        if (player == null) return;

        // Запускаем автокрафт только если верстак открыт
        CraftExecutor.tickWithCraftingTable(
            NodeGraph.getInstance(),
            player,
            self.getScreenHandler()
        );
    }
}
