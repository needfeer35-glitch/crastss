package net.craftsi.mod.node;

import net.minecraft.item.ItemStack;

public class RecipeNode {
    public String id;
    public int x, y;
    public int w = 180, h = 110;
    public boolean isActive = true;
    public boolean autoCraftOn = false;
    public int priority = 0;
    public String mode = "Активен"; // Активен / Отключен
    public ItemStack result = ItemStack.EMPTY;
    public ItemStack[] inputs = new ItemStack[9];
    public int craftCount = 0; // сколько раз скрафтили

    public RecipeNode(String id) {
        this.id = id;
        for (int i = 0; i < 9; i++) {
            this.inputs[i] = ItemStack.EMPTY;
        }
    }
}
