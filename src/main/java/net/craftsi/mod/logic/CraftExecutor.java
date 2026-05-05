package net.craftsi.mod.logic;

import net.craftsi.mod.node.NodeGraph;
import net.craftsi.mod.node.RecipeNode;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.slot.Slot;

public class CraftExecutor {

    // Вызывается каждый тик пока верстак открыт
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

    // Простой тик без верстака (для отображения статуса)
    public static void tick(NodeGraph graph, PlayerEntity player) {
        if (!player.getWorld().isClient()) return;
        for (RecipeNode node : graph.getNodes()) {
            if (!node.autoCraftOn) continue;
            node.isActive = canCraft(node, player);
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

    // Реальный крафт — берёт из инвентаря, кладёт в слоты верстака
    private static void craftInTable(RecipeNode node, PlayerEntity player,
                                      CraftingScreenHandler handler) {
        // Очищаем сетку верстака
        for (int i = 1; i <= 9; i++) {
            Slot slot = handler.getSlot(i);
            if (!slot.getStack().isEmpty()) {
                // Возвращаем в инвентарь
                player.getInventory().insertStack(slot.getStack().copy());
                slot.setStack(ItemStack.EMPTY);
            }
        }

        // Раскладываем ингредиенты по слотам
        for (int i = 0; i < 9; i++) {
            ItemStack need = node.inputs[i];
            if (need == null || need.isEmpty()) continue;

            Slot craftSlot = handler.getSlot(i + 1);
            // Берём из инвентаря игрока
            for (int j = 0; j < player.getInventory().size(); j++) {
                ItemStack inv = player.getInventory().getStack(j);
                if (inv.getItem() == need.getItem() && inv.getCount() >= need.getCount()) {
                    craftSlot.setStack(new ItemStack(need.getItem(), need.getCount()));
                    inv.decrement(need.getCount());
                    break;
                }
            }
        }

        // Берём результат из слота 0
        ItemStack result = handler.getSlot(0).getStack();
        if (!result.isEmpty() && result.getItem() == node.result.getItem()) {
            player.getInventory().insertStack(result.copy());
            handler.getSlot(0).setStack(ItemStack.EMPTY);
            node.craftCount++;
        }
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
                sb.append(need.getItem().toString().replace("minecraft:", ""))
                  .append(" ×").append(need.getCount() - found).append("  ");
            }
        }
        return sb.toString();
    }
}
