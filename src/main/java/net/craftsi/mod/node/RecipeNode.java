package net.craftsi.mod.node;

import net.minecraft.item.ItemStack;

public class RecipeNode {
    public String id;
    public int x, y;
    public int w = 160, h = 100;
    public boolean isActive = true;
    public ItemStack result = ItemStack.EMPTY;
    public ItemStack[] inputs = new ItemStack[9];

    public RecipeNode(String id) {
        this.id = id;
        for (int i = 0; i < 9; i++) {
            this.inputs[i] = ItemStack.EMPTY;
        }
    }
}
