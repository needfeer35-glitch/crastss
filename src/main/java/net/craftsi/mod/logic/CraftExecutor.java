package net.craftsi.mod.logic;

import net.craftsi.mod.node.NodeGraph;
import net.craftsi.mod.node.RecipeNode;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

public class CraftExecutor {

    public static void tick(NodeGraph graph, PlayerEntity player) {
        if (!player.getWorld().isClient()) return;
        for (RecipeNode node : graph.getNodes()) {
            if (!node.autoCraftOn) continue;
            if (node.result == null || node.result.isEmpty()) continue;
            if (canCraft(node, player)) {
                node.isActive = true;
                craftOne(node, player);
            } else {
                node.isActive = false;
            }
        }
    }

    public static boolean canCraft(RecipeNode node, PlayerEntity player) {
        for (ItemStack need : node.inputs) {
            if (need == null || need.isEmpty()) continue;
            int found = 0;
            for (int i = 0; i < player.getInventory().size(); i++) {
                ItemStack inv = player.getInventory().getStack(i);
                if (inv.getItem() == need.getItem()) found += inv.getCount();
            }
            if (found < need.getCount()) return false;
        }
        return true;
    }

    private static void craftOne(RecipeNode node, PlayerEntity player) {
        for (ItemStack need : node.inputs) {
            if (need == null || need.isEmpty()) continue;
            int toRemove = need.getCount();
            for (int i = 0; i < player.getInventory().size() && toRemove > 0; i++) {
                ItemStack inv = player.getInventory().getStack(i);
                if (inv.getItem() == need.getItem()) {
                    int take = Math.min(inv.getCount(), toRemove);
                    inv.decrement(take);
                    toRemove -= take;
                }
            }
        }
        player.getInventory().insertStack(node.result.copy());
        node.craftCount++;
    }

    public static String getMissingInfo(RecipeNode node, PlayerEntity player) {
        StringBuilder sb = new StringBuilder();
        for (ItemStack need : node.inputs) {
            if (need == null || need.isEmpty()) continue;
            int found = 0;
            for (int i = 0; i < player.getInventory().size(); i++) {
                ItemStack inv = player.getInventory().getStack(i);
                if (inv.getItem() == need.getItem()) found += inv.getCount();
            }
            if (found < need.getCount()) {
                sb.append(need.getItem().toString()
                    .replace("minecraft:", ""))
                  .append(" x").append(need.getCount() - found)
                  .append("  ");
            }
        }
        return sb.toString();
    }
}
