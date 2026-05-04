package net.craftsi.mod.logic;

import net.craftsi.mod.node.NodeGraph;
import net.craftsi.mod.node.RecipeNode;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

public class CraftExecutor {

    public static void tick(NodeGraph graph, PlayerEntity player) {
        for (RecipeNode node : graph.getNodes()) {
            if (!node.isActive) continue;
            if (canCraft(node, player)) {
                craft(node, player);
            } else {
                node.isActive = false;
            }
        }
    }

    private static boolean canCraft(RecipeNode node, PlayerEntity player) {
        for (ItemStack stack : node.inputs) {
            if (stack == null || stack.isEmpty()) continue;
            int count = 0;
            int size = player.getInventory().size();
            for (int i = 0; i < size; i++) {
                ItemStack inv = player.getInventory().getStack(i);
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
            int size = player.getInventory().size();
            for (int i = 0; i < size; i++) {
                ItemStack inv = player.getInventory().getStack(i);
                if (ItemStack.areItemsEqual(inv, stack)) {
                    int take = Math.min(inv.getCount(), need);
                    inv.decrement(take);
                    need -= take;
                    if (need <= 0) break;
                }
            }
        }
        player.getInventory().insertStack(node.result.copy());
    }
}
