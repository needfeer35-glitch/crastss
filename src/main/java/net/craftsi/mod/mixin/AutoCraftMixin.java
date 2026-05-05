package net.craftsi.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;

@Mixin(CraftingScreen.class)
public class AutoCraftMixin {
    // Пустой — крафт работает через tick в CraftExecutor
}
