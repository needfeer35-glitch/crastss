
package net.craftsi.mod.node;

import net.minecraft.item.ItemStack;

public class RecipeNode {

    public String id;
    public int x, y;
    public boolean isActive = true;

    public ItemStack result = ItemStack.EMPTY;
    public ItemStack[] inputs = new ItemStack[9];

    public RecipeNode(String id) {
        this.id = id;
    }
}
