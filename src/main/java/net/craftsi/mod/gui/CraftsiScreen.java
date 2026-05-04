package net.craftsi.mod.gui;

import net.craftsi.mod.node.NodeGraph;
import net.craftsi.mod.node.RecipeNode;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.*;

public class CraftsiScreen extends Screen {

    private static final int NODE_WIDTH = 160;
    private static final int NODE_HEIGHT = 100;
    private static final int HEADER_H = 16;
    private static final int RESIZE_ZONE = 8;

    // Цвета
    private static final int BG          = 0xFF1A1A1A;
    private static final int NODE_BG     = 0xFF2B2B2B;
    private static final int NODE_HOV    = 0xFF363636;
    private static final int BORDER_ON   = 0xFF4CAF50;
    private static final int BORDER_OFF  = 0xFFE53935;
    private static final int HEADER_ON   = 0xFF2E7D32;
    private static final int HEADER_OFF  = 0xFF7f0000;
    private static final int PORT_COLOR  = 0xFFFFAA00;
    private static final int LINE_COLOR  = 0xAAFFAA00;
    private static final int TEXT_WHITE  = 0xFFFFFFFF;
    private static final int TEXT_GRAY   = 0xFF888888;
    private static final int TEXT_GREEN  = 0xFF55FF55;
    private static final int TEXT_RED    = 0xFFFF5555;

    // Состояние
    private RecipeNode dragNode = null;
    private int dragOffX, dragOffY;
    private RecipeNode resizeNode = null;
    private int resizeStartW, resizeStartH, resizeMouseX, resizeMouseY;

    // Соединения: пара (fromId, toId)
    private final List<String[]> connections = new ArrayList<>();
    private RecipeNode connectFrom = null; // нода, от которой тянем связь

    // Редактирование рецепта
    private RecipeNode editingNode = null;
    private String inputBuffer = "";
    private int editingSlot = -1; // 0-8 = слот ингредиента, 9 = результат

    // Авто-крафт
    private final Set<String> autoCraftEnabled = new HashSet<>();

    public CraftsiScreen() {
        super(Text.literal("Craftsi"));
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        // Фон без блюра
        ctx.fill(0, 0, width, height, BG);

        // Заголовок
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal("✦ CRAFTSI NODE EDITOR ✦"), width / 2, 8, 0xFFFFAA00);

        // Подсказки
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("ЛКМ=перетащить  ПКМ=удалить  [+]=добавить  Shift+ЛКМ порт=соединить  2x клик=редактировать рецепт"),
            6, height - 12, TEXT_GRAY);

        // Кнопка добавить
        drawButton(ctx, width - 130, 4, 120, 16, "+ Добавить ноду", mx, my, 0xFF2E7D32, 0xFF4CAF50);

        // Линии соединений
        drawConnections(ctx);

        // Если тянем соединение — рисуем временную линию
        if (connectFrom != null) {
            int fx = connectFrom.x + connectFrom.w;
            int fy = connectFrom.y + connectFrom.h / 2;
            drawLine(ctx, fx, fy, mx, my, LINE_COLOR);
        }

        // Ноды
        for (RecipeNode node : NodeGraph.getInstance().getNodes()) {
            drawNode(ctx, node, mx, my);
        }

        // Панель редактирования рецепта
        if (editingNode != null) {
            drawEditPanel(ctx, mx, my);
        }

        super.render(ctx, mx, my, delta);
    }

    private void drawNode(DrawContext ctx, RecipeNode node, int mx, int my) {
        int x = node.x, y = node.y, w = node.w, h = node.h;
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        boolean autoCraft = autoCraftEnabled.contains(node.id);

        // Тень
        ctx.fill(x+3, y+3, x+w+3, y+h+3, 0x66000000);

        // Фон
        ctx.fill(x, y, x+w, y+h, hov ? NODE_HOV : NODE_BG);

        // Рамка
        ctx.drawBorder(x, y, w, h, node.isActive ? BORDER_ON : BORDER_OFF);

        // Заголовок
        ctx.fill(x, y, x+w, y+HEADER_H, node.isActive ? HEADER_ON : HEADER_OFF);
        String title = node.id.length() > 18 ? node.id.substring(0,18) : node.id;
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(title), x+w/2, y+4, TEXT_WHITE);

        // Статус
        String statusTxt = node.isActive ? "✔ Активен" : "✘ Нет ингредиентов";
        int statusCol = node.isActive ? TEXT_GREEN : TEXT_RED;
        ctx.drawTextWithShadow(textRenderer, Text.literal(statusTxt), x+4, y+HEADER_H+3, statusCol);

        // Результат
        if (!node.result.isEmpty()) {
            String res = node.result.getItem().toString().replace("minecraft:","");
            ctx.drawTextWithShadow(textRenderer, Text.literal("→ " + res + " x" + node.result.getCount()), x+4, y+HEADER_H+14, 0xFFCCCCCC);
        } else {
            ctx.drawTextWithShadow(textRenderer, Text.literal("→ (нет рецепта)"), x+4, y+HEADER_H+14, TEXT_GRAY);
        }

        // Ингредиенты
        int ingCount = 0;
        for (ItemStack s : node.inputs) if (s != null && !s.isEmpty()) ingCount++;
        ctx.drawTextWithShadow(textRenderer, Text.literal("Ингр: " + ingCount + "/9"), x+4, y+HEADER_H+25, TEXT_GRAY);

        // Кнопка авто-крафт
        int btnX = x+4, btnY = y+h-18, btnW = w-8, btnH = 14;
        boolean autoBtnHov = mx >= btnX && mx <= btnX+btnW && my >= btnY && my <= btnY+btnH;
        int autoBtnCol = autoCraft ? (autoBtnHov ? 0xFF1B5E20 : 0xFF2E7D32) : (autoBtnHov ? 0xFF4A148C : 0xFF6A1B9A);
        ctx.fill(btnX, btnY, btnX+btnW, btnY+btnH, autoBtnCol);
        ctx.drawBorder(btnX, btnY, btnW, btnH, autoCraft ? 0xFF66BB6A : 0xFFAB47BC);
        String autoBtnTxt = autoCraft ? "⚙ Авто-крафт: ВКЛ" : "⚙ Авто-крафт: ВЫКЛ";
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(autoBtnTxt), btnX+btnW/2, btnY+3, TEXT_WHITE);

        // Порт выхода (правый)
        ctx.fill(x+w-4, y+h/2-4, x+w+4, y+h/2+4, PORT_COLOR);
        ctx.drawBorder(x+w-4, y+h/2-4, 8, 8, 0xFFFFFFFF);

        // Порт входа (левый)
        ctx.fill(x-4, y+h/2-4, x+4, y+h/2+4, PORT_COLOR);
        ctx.drawBorder(x-4, y+h/2-4, 8, 8, 0xFFFFFFFF);

        // Маркер изменения размера (правый нижний угол)
        ctx.fill(x+w-6, y+h-6, x+w, y+h, 0xFF555555);
        ctx.fill(x+w-4, y+h-4, x+w, y+h, 0xFF888888);
    }

    private void drawConnections(DrawContext ctx) {
        for (String[] conn : connections) {
            RecipeNode from = findNode(conn[0]);
            RecipeNode to   = findNode(conn[1]);
            if (from == null || to == null) continue;
            int fx = from.x + from.w;
            int fy = from.y + from.h / 2;
            int tx = to.x;
            int ty = to.y + to.h / 2;
            drawLine(ctx, fx, fy, tx, ty, LINE_COLOR);
        }
    }

    private void drawLine(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2-x1), Math.abs(y2-y1));
        if (steps == 0) return;
        for (int i = 0; i <= steps; i++) {
            int px = x1 + (x2-x1)*i/steps;
            int py = y1 + (y2-y1)*i/steps;
            ctx.fill(px, py, px+2, py+2, color);
        }
    }

    private void drawEditPanel(DrawContext ctx, int mx, int my) {
        int pw = 320, ph = 200;
        int px = (width - pw) / 2, py = (height - ph) / 2;

        ctx.fill(px-2, py-2, px+pw+2, py+ph+2, 0xFF000000);
        ctx.fill(px, py, px+pw, py+ph, 0xFF222222);
        ctx.drawBorder(px, py, pw, ph, 0xFFFFAA00);

        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal("Редактор рецепта: " + editingNode.id), px+pw/2, py+5, 0xFFFFAA00);

        // Сетка 3x3 слотов
        for (int i = 0; i < 9; i++) {
            int col = i % 3, row = i / 3;
            int sx = px + 10 + col*38, sy = py + 20 + row*38;
            boolean sel = editingSlot == i;
            ctx.fill(sx, sy, sx+34, sy+34, sel ? 0xFF444444 : 0xFF333333);
            ctx.drawBorder(sx, sy, 34, 34, sel ? 0xFFFFAA00 : 0xFF555555);
            ItemStack stack = editingNode.inputs[i];
            if (stack != null && !stack.isEmpty()) {
                String name = stack.getItem().toString().replace("minecraft:","");
                if (name.length() > 5) name = name.substring(0,5);
                ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(name), sx+17, sy+6, TEXT_WHITE);
                ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("x"+stack.getCount()), sx+17, sy+20, TEXT_GRAY);
            } else {
                ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(String.valueOf(i+1)), sx+17, sy+13, TEXT_GRAY);
            }
        }

        // Слот результата
        int rx = px + 10 + 3*38 + 20, ry = py + 20 + 38;
        ctx.drawTextWithShadow(textRenderer, Text.literal("→"), rx-14, ry+10, TEXT_WHITE);
        boolean resSel = editingSlot == 9;
        ctx.fill(rx, ry, rx+34, ry+34, resSel ? 0xFF444444 : 0xFF333333);
        ctx.drawBorder(rx, ry, 34, 34, resSel ? 0xFFFFAA00 : 0xFF555555);
        if (!editingNode.result.isEmpty()) {
            String rn = editingNode.result.getItem().toString().replace("minecraft:","");
            if (rn.length() > 5) rn = rn.substring(0,5);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(rn), rx+17, ry+6, TEXT_WHITE);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("x"+editingNode.result.getCount()), rx+17, ry+20, TEXT_GRAY);
        } else {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("?"), rx+17, ry+13, TEXT_GRAY);
        }

        // Поле ввода
        int inputY = py + ph - 45;
        ctx.fill(px+10, inputY, px+pw-10, inputY+16, 0xFF333333);
        ctx.drawBorder(px+10, inputY, pw-20, 16, 0xFF666666);
        String prompt = editingSlot >= 0
            ? (editingSlot == 9 ? "Результат" : "Слот " + (editingSlot+1)) + ": " + inputBuffer + "|"
            : "Выбери слот выше";
        ctx.drawTextWithShadow(textRenderer, Text.literal(prompt), px+13, inputY+4, TEXT_WHITE);

        // Кнопки
        drawButton(ctx, px+10, py+ph-25, 80, 14, "Применить", mx, my, 0xFF1B5E20, 0xFF4CAF50);
        drawButton(ctx, px+100, py+ph-25, 80, 14, "Очистить слот", mx, my, 0xFF5A1A1A, 0xFFAA2222);
        drawButton(ctx, px+190, py+ph-25, 80, 14, "Закрыть", mx, my, 0xFF333333, 0xFF666666);
    }

    private void drawButton(DrawContext ctx, int x, int y, int w, int h, String label, int mx, int my, int col, int hoverCol) {
        boolean hov = mx >= x && mx <= x+w && my >= y && my <= y+h;
        ctx.fill(x, y, x+w, y+h, hov ? hoverCol : col);
        ctx.drawBorder(x, y, w, h, hoverCol);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(label), x+w/2, y+(h-7)/2, TEXT_WHITE);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX, my = (int) mouseY;

        // Кнопка добавить ноду
        if (mx >= width-130 && mx <= width-10 && my >= 4 && my <= 20) {
            addNode();
            return true;
        }

        // Клик внутри панели редактирования
        if (editingNode != null) {
            return handleEditClick(mx, my, button);
        }

        // Клик по нодам
        List<RecipeNode> nodes = NodeGraph.getInstance().getNodes();
        for (int i = nodes.size()-1; i >= 0; i--) {
            RecipeNode node = nodes.get(i);
            int x = node.x, y = node.y, w = node.w, h = node.h;

            if (mx < x-4 || mx > x+w+4 || my < y || my > y+h) continue;

            // ПКМ — удалить
            if (button == 1) {
                NodeGraph.getInstance().removeNode(node.id);
                connections.removeIf(c -> c[0].equals(node.id) || c[1].equals(node.id));
                return true;
            }

            // Порт выхода (правый) — Shift+ЛКМ или просто ЛКМ на порт
            if (mx >= x+w-6 && mx <= x+w+6 && my >= y+h/2-6 && my <= y+h/2+6) {
                connectFrom = node;
                return true;
            }

            // Порт входа (левый) — завершить соединение
            if (mx >= x-6 && mx <= x+6 && my >= y+h/2-6 && my <= y+h/2+6) {
                if (connectFrom != null && !connectFrom.id.equals(node.id)) {
                    connections.add(new String[]{connectFrom.id, node.id});
                    connectFrom = null;
                    return true;
                }
            }

            // Кнопка авто-крафт
            int btnX = x+4, btnY = y+h-18, btnW = w-8, btnH = 14;
            if (mx >= btnX && mx <= btnX+btnW && my >= btnY && my <= btnY+btnH) {
                if (autoCraftEnabled.contains(node.id)) {
                    autoCraftEnabled.remove(node.id);
                } else {
                    autoCraftEnabled.add(node.id);
                }
                return true;
            }

            // Маркер ресайза (правый нижний угол)
            if (mx >= x+w-RESIZE_ZONE && my >= y+h-RESIZE_ZONE) {
                resizeNode = node;
                resizeStartW = w; resizeStartH = h;
                resizeMouseX = mx; resizeMouseY = my;
                return true;
            }

            // Двойной клик — редактировать рецепт
            // (используем обычный клик для простоты — открывает редактор)
            if (my >= y+HEADER_H && my <= y+h-20) {
                editingNode = node;
                editingSlot = -1;
                inputBuffer = "";
                return true;
            }

            // Перетаскивание за заголовок
            if (my >= y && my <= y+HEADER_H) {
                dragNode = node;
                dragOffX = mx - x;
                dragOffY = my - y;
                return true;
            }
        }

        // Клик вне ноды — сбросить соединение
        connectFrom = null;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleEditClick(int mx, int my, int button) {
        int pw = 320, ph = 200;
        int px = (width - pw) / 2, py = (height - ph) / 2;

        // Слоты ингредиентов
        for (int i = 0; i < 9; i++) {
            int col = i % 3, row = i / 3;
            int sx = px + 10 + col*38, sy = py + 20 + row*38;
            if (mx >= sx && mx <= sx+34 && my >= sy && my <= sy+34) {
                editingSlot = i;
                inputBuffer = "";
                return true;
            }
        }

        // Слот результата
        int rx = px + 10 + 3*38 + 20, ry = py + 20 + 38;
        if (mx >= rx && mx <= rx+34 && my >= ry && my <= ry+34) {
            editingSlot = 9;
            inputBuffer = "";
            return true;
        }

        int inputY = py + ph - 45;

        // Кнопка Применить
        if (mx >= px+10 && mx <= px+90 && my >= py+ph-25 && my <= py+ph-11) {
            applyInput();
            return true;
        }

        // Кнопка Очистить слот
        if (mx >= px+100 && mx <= px+180 && my >= py+ph-25 && my <= py+ph-11) {
            clearSlot();
            return true;
        }

        // Кнопка Закрыть
        if (mx >= px+190 && mx <= px+270 && my >= py+ph-25 && my <= py+ph-11) {
            editingNode = null;
            return true;
        }

        return true;
    }

    private void applyInput() {
        if (editingSlot < 0 || inputBuffer.isEmpty()) return;
        String itemId = inputBuffer.trim().toLowerCase();
        if (!itemId.contains(":")) itemId = "minecraft:" + itemId;
        try {
            Item item = Registries.ITEM.get(Identifier.of(itemId));
            if (item == Items.AIR) return;
            ItemStack stack = new ItemStack(item, 1);
            if (editingSlot == 9) {
                editingNode.result = stack;
            } else {
                editingNode.inputs[editingSlot] = stack;
            }
            inputBuffer = "";
            editingSlot = -1;
        } catch (Exception ignored) {}
    }

    private void clearSlot() {
        if (editingSlot < 0) return;
        if (editingSlot == 9) {
            editingNode.result = ItemStack.EMPTY;
        } else {
            editingNode.inputs[editingSlot] = ItemStack.EMPTY;
        }
        inputBuffer = "";
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editingNode != null) {
            if (keyCode == 259) { // Backspace
                if (!inputBuffer.isEmpty())
                    inputBuffer = inputBuffer.substring(0, inputBuffer.length()-1);
                return true;
            }
            if (keyCode == 257 || keyCode == 335) { // Enter
                applyInput();
                return true;
            }
            if (keyCode == 256) { // ESC
                editingNode = null;
                return true;
            }
            return true;
        }
        if (keyCode == 256) { // ESC
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (editingNode != null && editingSlot >= 0) {
            inputBuffer += chr;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        int mx = (int) mouseX, my = (int) mouseY;
        if (dragNode != null) {
            dragNode.x = mx - dragOffX;
            dragNode.y = my - dragOffY;
            return true;
        }
        if (resizeNode != null) {
            resizeNode.w = Math.max(140, resizeStartW + (mx - resizeMouseX));
            resizeNode.h = Math.max(80, resizeStartH + (my - resizeMouseY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragNode = null;
        resizeNode = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void addNode() {
        int count = NodeGraph.getInstance().getNodes().size();
        String id = "Рецепт " + (count + 1);
        RecipeNode node = new RecipeNode(id);
        node.x = 60 + (count * 180) % (width - 200);
        node.y = 50 + (count / 4) * 120;
        node.w = NODE_WIDTH;
        node.h = NODE_HEIGHT;
        NodeGraph.getInstance().addNode(node);
    }

    public Set<String> getAutoCraftEnabled() {
        return autoCraftEnabled;
    }

    private RecipeNode findNode(String id) {
        return NodeGraph.getInstance().getNodes().stream()
            .filter(n -> n.id.equals(id)).findFirst().orElse(null);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return true; }
}
