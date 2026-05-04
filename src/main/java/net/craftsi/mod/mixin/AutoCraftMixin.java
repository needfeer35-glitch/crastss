package net.craftsi.mod.mixin;

import net.craftsi.mod.logic.CraftExecutor;
import net.craftsi.mod.node.NodeGraph;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingScreen.class)
public class AutoCraftMixin {
    @Inject(method = "handledScreenTick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        // Проверяем все активные узлы из NodeGraph
        for (var node : NodeGraph.activeNodes) {
            if (node.canCraft) {
                CraftExecutor.executeAutoCraft(node);[cite: 1]
            }
        }
    }
}
