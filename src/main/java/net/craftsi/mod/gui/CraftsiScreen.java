package net.craftsi.mod.screen;

import net.craftsi.mod.node.NodeGraph;
import net.craftsi.mod.node.RecipeNode;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public class CraftsiNodeScreen extends Screen {

    private final NodeGraph graph = NodeGraph.getInstance();

    public CraftsiNodeScreen() {
        super(Text.of("CraftSi"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ─────────────────────────────────────────────
    // 🎨 РЕНДЕР БЕЗ МЫЛА (ТОЛЬКО VANILLA STYLE)
    // ─────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {

        // ЧИСТЫЙ ФОН (без текстур)
        ctx.fill(0, 0, width, height, 0xFF0F172A);

        drawGrid(ctx);
        drawConnections(ctx);

        for (RecipeNode node : graph.getAllNodes()) {
            drawNode(ctx, node);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    // ─────────────────────────────────────────────
    // 🧩 СЕТКА (чёткая, без blur)
    // ─────────────────────────────────────────────

    private void drawGrid(DrawContext ctx) {
        int size = 40;

        for (int x = 0; x < width; x += size) {
            ctx.fill(x, 0, x + 1, height, 0xFF1E293B);
        }

        for (int y = 0; y < height; y += size) {
            ctx.fill(0, y, width, y + 1, 0xFF1E293B);
        }
    }

    // ─────────────────────────────────────────────
    // 🔗 ЛИНИИ (без сглаживания = чётко)
    // ─────────────────────────────────────────────

    private void drawConnections(DrawContext ctx) {
        for (RecipeNode node : graph.getAllNodes()) {
            for (String parentId : node.parentIds) {
                RecipeNode parent = graph.getNode(parentId);
                if (parent == null) continue;

                drawLine(
                        ctx,
                        parent.x + 60,
                        parent.y + 80,
                        node.x + 60,
                        node.y,
                        0xFF4ADE80
                );
            }
        }
    }

    private void drawLine(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);

        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;

        int err = dx - dy;

        while (true) {
            ctx.fill(x1, y1, x1 + 1, y1 + 1, color);

            if (x1 == x2 && y1 == y2) break;

            int e2 = 2 * err;

            if (e2 > -dy) {
                err -= dy;
                x1 += sx;
            }

            if (e2 < dx) {
                err += dx;
                y1 += sy;
            }
        }
    }

    // ─────────────────────────────────────────────
    // 📦 NODE (идеально чёткий)
    // ─────────────────────────────────────────────

    private void drawNode(DrawContext ctx, RecipeNode node) {

        int x = node.x;
        int y = node.y;
        int w = 120;
        int h = 100;

        // ТЕНЬ
        ctx.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x55000000);

        // ФОН
        ctx.fill(x, y, x + w, y + h, 0xFF1E293B);

        // БОРДЕР
        drawBorder(ctx, x, y, w, h, 0xFF3B82F6);

        // HEADER
        ctx.fill(x, y, x + w, y + 16, node.isActive ? 0xFF22C55E : 0xFF334155);

        ctx.drawText(textRenderer,
                node.isActive ? "ACTIVE" : "OFF",
                x + 5,
                y + 4,
                0xFFFFFFFF,
                false
        );

        // INPUT SLOTS
        int slotSize = 18;

        for (int i = 0; i < 9; i++) {
            int sx = x + 5 + (i % 3) * slotSize;
            int sy = y + 20 + (i / 3) * slotSize;

            ctx.fill(sx, sy, sx + 16, sy + 16, 0xFF020617);
            drawBorder(ctx, sx, sy, 16, 16, 0xFF475569);

            ItemStack stack = node.inputs[i];
            if (stack != null && !stack.isEmpty()) {
                ctx.drawItem(stack, sx, sy);
            }
        }

        // RESULT
        if (!node.result.isEmpty()) {
            ctx.drawItem(node.result, x + 85, y + 40);
        }
    }

    // ─────────────────────────────────────────────
    // 🧱 BORDER
    // ─────────────────────────────────────────────

    private void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }
}
