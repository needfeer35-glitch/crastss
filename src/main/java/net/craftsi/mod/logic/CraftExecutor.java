package net.craftsi.mod.logic;

import net.craftsi.mod.node.NodeGraph;
import net.craftsi.mod.node.RecipeNode;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

import java.util.Set;

public class CraftExecutor {

    public static void tick(NodeGraph graph, PlayerEntity player, Set<String> autoCraftEnabled) {
        for (RecipeNode node : graph.getNodes()) {
            if (!autoCraftEnabled.contains(node.id)) continue;
            if (node.result == null || node.result.isEmpty()) continue;

            if (canCraft(node, player)) {
                node.isActive = true;
                craft(node, player);
            } else {
                node.isActive = false;
            }
        }
    }

    private static boolean canCraft(RecipeNode node, PlayerEntity player) {
        for (ItemStack need : node.inputs) {
            if (need == null || need.isEmpty()) continue;
            int found = 0;
            int size = player.getInventory().size();
            for (int i = 0; i < size; i++) {
                ItemStack inv = player.getInventory().getStack(i);
                if (ItemStack.areItemsEqual(inv, need)) found += inv.getCount();
            }
            if (found < need.getCount()) return false;
        }
        return true;
    }

    private static void craft(RecipeNode node, PlayerEntity player) {
        for (ItemStack need : node.inputs) {
            if (need == null || need.isEmpty()) continue;
            int toRemove = need.getCount();
            int size = player.getInventory().size();
            for (int i = 0; i < size && toRemove > 0; i++) {
                ItemStack inv = player.getInventory().getStack(i);
                if (ItemStack.areItemsEqual(inv, need)) {
                    int take = Math.min(inv.getCount(), toRemove);
                    inv.decrement(take);
                    toRemove -= take;
                }
            }
        }
        player.getInventory().insertStack(node.result.copy());
    }
}
