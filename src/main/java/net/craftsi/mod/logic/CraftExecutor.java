package net.craftsi.mod.logic;

import net.craftsi.mod.node.NodeGraph;
import net.craftsi.mod.node.RecipeNode;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.slot.Slot;

public class CraftExecutor {

    public static void tickWithCraftingTable(NodeGraph graph, PlayerEntity player,
                                              CraftingScreenHandler handler) {
        for (RecipeNode node : graph.getNodes()) {
            if (!node.autoCraftOn) continue;
            if (node.result == null || node.result.isEmpty()) continue;

            if (canCraft(node, player)) {
                node.isActive = true;
                craftInTable(node, player, handler);
            } else {
                node.isActive = false;
            }
        }
    }

    public static void tick(NodeGraph graph, PlayerEntity player) {
        if (!player.getWorld().isClient()) return;
        for (RecipeNode node : graph.getNodes()) {
            if (!node.autoCraftOn) continue;
            node.isActive = canCraft(node, player);
        }
    }

    public static boolean canCraft(RecipeNode node, PlayerEntity player) {
        // Считаем сколько каждого предмета нужно суммарно
        java.util.Map<net.minecraft.item.Item, Integer> needed = new java.util.HashMap<>();
        for (ItemStack need : node.inputs) {
            if (need == null || need.isEmpty()) continue;
            needed.merge(need.getItem(), need.getCount(), Integer::sum);
        }
        for (java.util.Map.Entry<net.minecraft.item.Item, Integer> e : needed.entrySet()) {
            int found = 0;
            for (int i = 0; i < player.getInventory().size(); i++) {
                ItemStack inv = player.getInventory().getStack(i);
                if (inv.getItem() == e.getKey()) found += inv.getCount();
            }
            if (found < e.getValue()) return false;
        }
        return true;
    }

    private static void craftInTable(RecipeNode node, PlayerEntity player,
                                      CraftingScreenHandler handler) {
        // Очищаем сетку верстака — возвращаем всё в инвентарь
        for (int i = 1; i <= 9; i++) {
            Slot slot = handler.getSlot(i);
            ItemStack current = slot.getStack();
            if (!current.isEmpty()) {
                player.getInventory().insertStack(current.copy());
                slot.setStack(ItemStack.EMPTY);
            }
        }

        // Раскладываем ингредиенты по слотам верстака
        for (int i = 0; i < 9; i++) {
            ItemStack need = node.inputs[i];
            if (need == null || need.isEmpty()) continue;

            Slot craftSlot = handler.getSlot(i + 1);
            // Ищем нужный предмет в инвентаре
            for (int j = 0; j < player.getInventory().size(); j++) {
                ItemStack inv = player.getInventory().getStack(j);
                if (inv.getItem() == need.getItem() && inv.getCount() >= need.getCount()) {
                    craftSlot.setStack(new ItemStack(need.getItem(), need.getCount()));
                    inv.decrement(need.getCount());
                    break;
                }
            }
        }

        // Берём результат из слота 0 (выходной слот верстака)
        ItemStack result = handler.getSlot(0).getStack();
        if (!result.isEmpty() && result.getItem() == node.result.getItem()) {
            player.getInventory().insertStack(result.copy());
            handler.getSlot(0).setStack(ItemStack.EMPTY);
            node.craftCount++;
        }
    }

    public static String getMissingInfo(RecipeNode node, PlayerEntity player) {
        StringBuilder sb = new StringBuilder();
        java.util.Map<net.minecraft.item.Item, Integer> needed = new java.util.HashMap<>();
        for (ItemStack need : node.inputs) {
            if (need == null || need.isEmpty()) continue;
            needed.merge(need.getItem(), need.getCount(), Integer::sum);
        }
        for (java.util.Map.Entry<net.minecraft.item.Item, Integer> e : needed.entrySet()) {
            int found = 0;
            for (int i = 0; i < player.getInventory().size(); i++) {
                ItemStack inv = player.getInventory().getStack(i);
                if (inv.getItem() == e.getKey()) found += inv.getCount();
            }
            if (found < e.getValue()) {
                sb.append(e.getKey().toString().replace("minecraft:", ""))
                  .append(" ×").append(e.getValue() - found).append("  ");
            }
        }
        return sb.toString();
    }
}
