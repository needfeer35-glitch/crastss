package net.craftsi.mod.gui;

import net.craftsi.mod.node.NodeGraph;
import net.craftsi.mod.node.RecipeNode;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.UUID;

public class CraftsiScreen extends Screen {

    private static final int NODE_WIDTH = 120;
    private static final int NODE_HEIGHT = 60;
    private static final int BG_COLOR = 0xCC1D1D1D;
    private static final int NODE_COLOR = 0xFF2D2D2D;
    private static final int ACTIVE_COLOR = 0xFF55FF55;
    private static final int INACTIVE_COLOR = 0xFFFF5555;
    private static final int TITLE_COLOR = 0xFFFFFFFF;

    private RecipeNode selectedNode = null;
    private int dragOffsetX, dragOffsetY;
    private boolean dragging = false;

    public CraftsiScreen() {
        super(Text.literal("Craftsi"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, BG_COLOR);

        context.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("✦ CRAFTSI NODE EDITOR ✦"), this.width / 2, 10, 0xFFFFAA00);

        drawConnections(context);

        for (RecipeNode node : NodeGraph.getInstance().getNodes()) {
            drawNode(context, node, mouseX, mouseY);
        }

        int btnX = 10;
        int btnY = this.height - 30;
        boolean hovered = mouseX >= btnX && mouseX <= btnX + 120 && mouseY >= btnY && mouseY <= btnY + 20;
        context.fill(btnX, btnY, btnX + 120, btnY + 20, hovered ? 0xFF4A7A4A : 0xFF3A5A3A);
        context.drawBorder(btnX, btnY, 120, 20, 0xFF55AA55);
        context.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("+ Добавить ноду"), btnX + 60, btnY + 6, 0xFFFFFFFF);

        context.drawTextWithShadow(this.textRenderer,
            Text.literal("ПКМ — удалить  |  ЛКМ — перетащить  |  ESC — закрыть"),
            10, this.height - 12, 0xFF888888);

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawConnections(DrawContext context) {
    }

    private void drawNode(DrawContext context, RecipeNode node, int mouseX, int mouseY) {
        int x = node.x;
        int y = node.y;
        boolean hovered = mouseX >= x && mouseX <= x + NODE_WIDTH &&
                           mouseY >= y && mouseY <= y + NODE_HEIGHT;

        context.fill(x + 3, y + 3, x + NODE_WIDTH + 3, y + NODE_HEIGHT + 3, 0x88000000);

        int bgColor = hovered ? 0xFF383838 : NODE_COLOR;
        context.fill(x, y, x + NODE_WIDTH, y + NODE_HEIGHT, bgColor);

        context.drawBorder(x, y, NODE_WIDTH, NODE_HEIGHT, node.isActive ? 0xFF55AA55 : 0xFFAA2222);

        context.fill(x, y, x + NODE_WIDTH, y + 14, node.isActive ? 0xFF2A5A2A : 0xFF5A1A1A);
        context.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal(node.id.length() > 14 ? node.id.substring(0, 14) : node.id),
            x + NODE_WIDTH / 2, y + 3, TITLE_COLOR);

        String status = node.isActive ? "✔ Активен" : "✘ Нет материалов";
        int statusColor = node.isActive ? ACTIVE_COLOR : INACTIVE_COLOR;
        context.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal(status), x + NODE_WIDTH / 2, y + 22, statusColor);

        if (!node.result.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("→ " + node.result.getItem().toString().replace("minecraft:", "")),
                x + NODE_WIDTH / 2, y + 36, 0xFFCCCCCC);
        } else {
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("(нет рецепта)"), x + NODE_WIDTH / 2, y + 36, 0xFF666666);
        }

        context.fill(x + NODE_WIDTH - 5, y + NODE_HEIGHT / 2 - 3,
                     x + NODE_WIDTH + 5, y + NODE_HEIGHT / 2 + 3, 0xFFFFAA00);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;

        int btnX = 10, btnY = this.height - 30;
        if (mx >= btnX && mx <= btnX + 120 && my >= btnY && my <= btnY + 20) {
            addNewNode();
            return true;
        }

        for (RecipeNode node : NodeGraph.getInstance().getNodes()) {
            if (mx >= node.x && mx <= node.x + NODE_WIDTH &&
                my >= node.y && my <= node.y + NODE_HEIGHT) {
                if (button == 1) {
                    NodeGraph.getInstance().removeNode(node.id);
                    return true;
                } else if (button == 0) {
                    selectedNode = node;
                    dragOffsetX = mx - node.x;
                    dragOffsetY = my - node.y;
                    dragging = true;
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging && selectedNode != null) {
            selectedNode.x = (int) mouseX - dragOffsetX;
            selectedNode.y = (int) mouseY - dragOffsetY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        selectedNode = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void addNewNode() {
        String id = "Рецепт " + (NodeGraph.getInstance().getNodes().size() + 1);
        RecipeNode node = new RecipeNode(id);
        node.x = 50 + (NodeGraph.getInstance().getNodes().size() * 140) % (this.width - 150);
        node.y = 50 + (NodeGraph.getInstance().getNodes().size() / 5) * 100;
        NodeGraph.getInstance().addNode(node);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
