
package net.craftsi.mod.logic;

import net.craftsi.mod.node.NodeGraph;
import net.craftsi.mod.node.RecipeNode;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

public class CraftExecutor {

   private static void transferItem(int fromSlot, int toSlot) {
    var manager = client.interactionManager;
    var handler = client.player.currentScreenHandler;
    
    // Клик по предмету в инвентаре
    manager.clickSlot(handler.syncId, fromSlot, 0, SlotActionType.PICKUP, client.player);
    // Клик по слоту в сетке верстака
    manager.clickSlot(handler.syncId, toSlot, 0, SlotActionType.PICKUP, client.player);
}

    private static boolean canCraft(RecipeNode node, PlayerEntity player) {
        for (ItemStack stack : node.inputs) {
            if (stack == null || stack.isEmpty()) continue;

            int count = 0;
            for (ItemStack inv : player.getInventory().main) {
                if (ItemStack.areItemsEqual(inv, stack)) {
                    count += inv.getCount();
                }
            }
            if (count < stack.getCount()) return false;
        }
        return true;
    }

    private static void craft(RecipeNode node, PlayerEntity player) {
        for (ItemStack stack : node.inputs) {
            if (stack == null || stack.isEmpty()) continue;

            int need = stack.getCount();

            for (ItemStack inv : player.getInventory().main) {
                if (ItemStack.areItemsEqual(inv, stack)) {
                    int take = Math.min(inv.getCount(), need);
                    inv.decrement(take);
                    need -= take;
                    if (need <= 0) break;
   private void drawNodeLine(DrawContext context, int x1, int y1, int x2, int y2) {
    // Рисуем простую линию. Для красоты можно использовать кривые Безье.
    context.fill(x1 + 20, y1 + 20, x2 + 20, y2 + 20, 0xFFAAAAAA); 
}
                }
            }
        }

        player.getInventory().insertStack(node.result.copy());
    }
}
