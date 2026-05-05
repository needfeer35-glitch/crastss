package net.craftsi.mod.gui;

import net.craftsi.mod.logic.CraftExecutor;
import net.craftsi.mod.node.NodeGraph;
import net.craftsi.mod.node.RecipeNode;
import net.minecraft.client.MinecraftClient;
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

    // Панели
    private enum Panel { NONE, EDIT_NODE, ITEM_PICKER, SETTINGS, AUTO_STATUS }
    private Panel activePanel = Panel.NONE;

    // Граф
    private RecipeNode dragNode = null;
    private int dragOffX, dragOffY;
    private RecipeNode resizeNode = null;
    private int resizeStartW, resizeStartH, resizeMouseX, resizeMouseY;
    private RecipeNode connectFrom = null;
    private int canvasOffX = 0, canvasOffY = 0;
    private boolean draggingCanvas = false;
    private int canvasDragStartX, canvasDragStartY;
    private float zoom = 1.0f;
    private Set<RecipeNode> selected = new HashSet<>();

    // Редактор ноды
    private RecipeNode editingNode = null;
    private int editingSlot = -1;
    private String inputBuffer = "";
    private boolean pickingForSlot = false;

    // Выбор предмета
    private String searchQuery = "";
    private List<Item> filteredItems = new ArrayList<>();
    private int itemPickerPage = 0;
    private static final int ITEMS_PER_PAGE = 40;
    private String selectedCategory = "Все";

    // Авто-статус
    private String autoStatusText = "";
    private String missingText = "";

    // Цвета
    private static final int C_BG        = 0xFF111111;
    private static final int C_PANEL     = 0xFF1C1C1C;
    private static final int C_PANEL2    = 0xFF242424;
    private static final int C_BORDER    = 0xFF3A3A3A;
    private static final int C_ACCENT    = 0xFF4CAF50;
    private static final int C_ACCENT2   = 0xFF2196F3;
    private static final int C_RED       = 0xFFE53935;
    private static final int C_ORANGE    = 0xFFFF6F00;
    private static final int C_NODE_BG   = 0xFF1E1E1E;
    private static final int C_NODE_SEL  = 0xFF2A3A2A;
    private static final int C_HDR_ON    = 0xFF1B5E20;
    private static final int C_HDR_OFF   = 0xFF7F0000;
    private static final int C_PORT      = 0xFFFFAA00;
    private static final int C_LINE      = 0xAA4CAF50;
    private static final int C_WHITE     = 0xFFFFFFFF;
    private static final int C_GRAY      = 0xFF888888;
    private static final int C_LTGRAY    = 0xFFBBBBBB;
    private static final int C_GREEN     = 0xFF55FF55;
    private static final int C_DKRED     = 0xFFFF5555;

    // Левая панель инструментов
    private static final String[] TOOLS = {"Выделить","Переместить","Соединить","Удалить","Авто-раскладка","Масштаб"};
    private int activeTool = 0;
    private static final int LEFT_W = 100;
    private static final int TOP_H = 30;

    public CraftsiScreen() {
        super(Text.literal("Craftsi"));
        rebuildItemList();
    }

    // ─────────────── RENDER ───────────────

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        // Фон
        ctx.fill(0, 0, width, height, C_BG);

        // Холст графа
        renderCanvas(ctx, mx, my);

        // Левая панель инструментов
        renderLeftPanel(ctx, mx, my);

        // Верхняя панель
        renderTopBar(ctx, mx, my);

        // Строка состояния
        renderStatusBar(ctx, mx, my);

        // Миникарта
        renderMinimap(ctx);

        // Всплывающие панели
        if (activePanel == Panel.EDIT_NODE && editingNode != null)
            renderEditPanel(ctx, mx, my);
        if (activePanel == Panel.ITEM_PICKER)
            renderItemPicker(ctx, mx, my);
        if (activePanel == Panel.SETTINGS)
            renderSettings(ctx, mx, my);
        if (activePanel == Panel.AUTO_STATUS)
            renderAutoStatus(ctx, mx, my);

        super.render(ctx, mx, my, delta);
    }

    private void renderTopBar(DrawContext ctx, int mx, int my) {
        ctx.fill(0, 0, width, TOP_H, C_PANEL);
        ctx.fill(0, TOP_H, width, TOP_H+1, C_BORDER);

        // Кнопки
        int x = LEFT_W + 5;
        x = topBtn(ctx, x, 3, "＋ Добавить ноду", mx, my, C_ACCENT) + 5;
        x = topBtn(ctx, x, 3, "📄 Новый", mx, my, C_PANEL2) + 5;
        x = topBtn(ctx, x, 3, "📂 Открыть", mx, my, C_PANEL2) + 5;
        x = topBtn(ctx, x, 3, "💾 Сохранить", mx, my, C_PANEL2) + 5;
        x = topBtn(ctx, x, 3, "📤 Экспорт", mx, my, C_PANEL2) + 5;
        x = topBtn(ctx, x, 3, "🔧 Авто-раскладка", mx, my, C_PANEL2) + 5;
        x = topBtn(ctx, x, 3, "⚙", mx, my, C_PANEL2) + 5;

        // Зум справа
        ctx.drawTextWithShadow(textRenderer, Text.literal("⊕  " + (int)(zoom*100) + "%"), width - 60, 10, C_GRAY);
    }

    private int topBtn(DrawContext ctx, int x, int y, String label, int mx, int my, int col) {
        int w = textRenderer.getWidth(label) + 10;
        boolean hov = mx >= x && mx <= x+w && my >= y && my <= y+24;
        ctx.fill(x, y, x+w, y+24, hov ? lighten(col) : col);
        ctx.drawBorder(x, y, w, 24, C_BORDER);
        ctx.drawTextWithShadow(textRenderer, Text.literal(label), x+5, y+8, C_WHITE);
        return x + w;
    }

    private void renderLeftPanel(DrawContext ctx, int mx, int my) {
        ctx.fill(0, TOP_H, LEFT_W, height - 20, C_PANEL);
        ctx.fill(LEFT_W, TOP_H, LEFT_W+1, height - 20, C_BORDER);

        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Инструменты"), LEFT_W/2, TOP_H+6, C_GRAY);

        for (int i = 0; i < TOOLS.length; i++) {
            int ty = TOP_H + 22 + i * 22;
            boolean hov = mx >= 4 && mx <= LEFT_W-4 && my >= ty && my <= ty+18;
            boolean active = activeTool == i;
            ctx.fill(4, ty, LEFT_W-4, ty+18, active ? C_ACCENT : (hov ? C_PANEL2 : C_PANEL));
            ctx.drawBorder(4, ty, LEFT_W-8, 18, active ? C_ACCENT : C_BORDER);
            ctx.drawTextWithShadow(textRenderer, Text.literal(TOOLS[i]), 8, ty+5, active ? C_WHITE : C_LTGRAY);
        }

        // Миникарта заголовок
        int mmY = TOP_H + 22 + TOOLS.length * 22 + 10;
        ctx.drawTextWithShadow(textRenderer, Text.literal("Миникарта"), 4, mmY, C_GRAY);
    }

    private void renderStatusBar(DrawContext ctx, int mx, int my) {
        int y = height - 20;
        ctx.fill(0, y, width, height, C_PANEL);
        ctx.fill(0, y, width, y+1, C_BORDER);

        int nodes = NodeGraph.getInstance().getNodes().size();
        int conns = NodeGraph.getInstance().getConnections().size();
        long active = NodeGraph.getInstance().getNodes().stream().filter(n -> n.autoCraftOn).count();
        ctx.drawTextWithShadow(textRenderer, Text.literal(
            "Узлов: " + nodes + "   Связей: " + conns + "   Авто-крафт: " + active + "   Масштаб: " + (int)(zoom*100) + "%"
        ), LEFT_W + 5, y+6, C_GRAY);

        // Авто-статус кнопка справа
        int btnX = width - 160;
        boolean hov = mx >= btnX && mx <= btnX+150 && my >= y && my <= height;
        ctx.fill(btnX, y+2, btnX+150, height-2, hov ? C_PANEL2 : C_PANEL);
        ctx.drawBorder(btnX, y+2, 150, 16, C_BORDER);
        ctx.drawTextWithShadow(textRenderer, Text.literal("📊 Авто-крафт статус"), btnX+5, y+6, C_ACCENT);
    }

    private void renderCanvas(DrawContext ctx, int mx, int my) {
        int cx = LEFT_W, cy = TOP_H, cw = width - LEFT_W, ch = height - TOP_H - 20;

        // Сетка
        ctx.enableScissor(cx, cy, cx+cw, cy+ch);
        int gridSize = (int)(32 * zoom);
        if (gridSize > 8) {
            for (int gx = (canvasOffX % gridSize); gx < cw; gx += gridSize)
                ctx.fill(cx+gx, cy, cx+gx+1, cy+ch, 0xFF1E1E1E);
            for (int gy = (canvasOffY % gridSize); gy < ch; gy += gridSize)
                ctx.fill(cx, cy+gy, cx+cw, cy+gy+1, 0xFF1E1E1E);
        }

        // Соединения
        for (String[] conn : NodeGraph.getInstance().getConnections()) {
            RecipeNode from = NodeGraph.getInstance().findById(conn[0]);
            RecipeNode to   = NodeGraph.getInstance().findById(conn[1]);
            if (from == null || to == null) continue;
            int fx = cx + canvasOffX + (int)(from.x * zoom) + (int)(from.w * zoom);
            int fy = cy + canvasOffY + (int)((from.y + from.h/2) * zoom);
            int tx = cx + canvasOffX + (int)(to.x * zoom);
            int ty = cy + canvasOffY + (int)((to.y + to.h/2) * zoom);
            drawBezier(ctx, fx, fy, tx, ty, C_LINE);
        }

        // Линия соединения в процессе
        if (connectFrom != null) {
            int fx = cx + canvasOffX + (int)((connectFrom.x + connectFrom.w) * zoom);
            int fy = cy + canvasOffY + (int)((connectFrom.y + connectFrom.h/2) * zoom);
            drawBezier(ctx, fx, fy, mx, my, C_ORANGE);
        }

        // Ноды
        for (RecipeNode node : NodeGraph.getInstance().getNodes()) {
            int nx = cx + canvasOffX + (int)(node.x * zoom);
            int ny = cy + canvasOffY + (int)(node.y * zoom);
            int nw = (int)(node.w * zoom);
            int nh = (int)(node.h * zoom);
            drawNode(ctx, node, nx, ny, nw, nh, mx, my);
        }

        ctx.disableScissor();
    }

    private void drawNode(DrawContext ctx, RecipeNode node, int x, int y, int w, int h, int mx, int my) {
        boolean sel = selected.contains(node);
        boolean hov = mx >= x && mx <= x+w && my >= y && my <= y+h;
        int HEADER_H = Math.max(16, (int)(16 * zoom));

        // Тень
        ctx.fill(x+4, y+4, x+w+4, y+h+4, 0x55000000);

        // Фон
        ctx.fill(x, y, x+w, y+h, sel ? C_NODE_SEL : C_NODE_BG);

        // Рамка
        int borderCol = sel ? C_ACCENT2 : (node.isActive && node.autoCraftOn ? C_ACCENT : (node.autoCraftOn ? C_RED : C_BORDER));
        ctx.drawBorder(x, y, w, h, borderCol);

        // Заголовок
        ctx.fill(x, y, x+w, y+HEADER_H, node.autoCraftOn ? (node.isActive ? C_HDR_ON : C_HDR_OFF) : 0xFF2A2A2A);
        String title = node.id;
        if (textRenderer.getWidth(title) > w-10) title = title.substring(0, Math.max(1,(w-20)/6)) + "…";
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(title), x+w/2, y+4, C_WHITE);

        // Режим badge
        String badge = node.autoCraftOn ? (node.isActive ? "АКТИВЕН" : "НЕТ МАТ.") : "ВЫКЛ";
        int badgeCol = node.autoCraftOn ? (node.isActive ? C_GREEN : C_DKRED) : C_GRAY;
        ctx.drawTextWithShadow(textRenderer, Text.literal("● " + badge), x+w - textRenderer.getWidth("● "+badge) - 4, y+4, badgeCol);

        if (zoom > 0.5f) {
            // Результат
            if (!node.result.isEmpty()) {
                String res = node.result.getItem().toString().replace("minecraft:","");
                ctx.drawTextWithShadow(textRenderer, Text.literal("→ " + res), x+4, y+HEADER_H+3, C_LTGRAY);
            }

            // Счётчик крафтов
            if (node.craftCount > 0)
                ctx.drawTextWithShadow(textRenderer, Text.literal("✔ " + node.craftCount), x+4, y+HEADER_H+14, C_GREEN);

            // Недостающие
            if (node.autoCraftOn && !node.isActive) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player != null) {
                    String miss = CraftExecutor.getMissingInfo(node, mc.player);
                    if (!miss.isEmpty()) {
                        ctx.drawTextWithShadow(textRenderer, Text.literal("✘ " + miss), x+4, y+HEADER_H+25, C_DKRED);
                    }
                }
            }

            // Кнопка авто-крафт
            int btnY = y+h-14;
            boolean autoBtnHov = mx >= x+4 && mx <= x+w-4 && my >= btnY && my <= btnY+12;
            ctx.fill(x+4, btnY, x+w-4, btnY+12, node.autoCraftOn ? (autoBtnHov?0xFF1B5E20:0xFF2E7D32) : (autoBtnHov?0xFF37474F:0xFF263238));
            ctx.drawBorder(x+4, btnY, w-8, 12, node.autoCraftOn ? C_ACCENT : C_BORDER);
            String autotxt = node.autoCraftOn ? "⚙ Авто-крафт: ВКЛ" : "⚙ Авто-крафт: ВЫКЛ";
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(autotxt), x+w/2, btnY+2, C_WHITE);
        }

        // Порты
        ctx.fill(x-5, y+h/2-5, x+5, y+h/2+5, C_PORT);
        ctx.drawBorder(x-5, y+h/2-5, 10, 10, C_WHITE);
        ctx.fill(x+w-5, y+h/2-5, x+w+5, y+h/2+5, C_PORT);
        ctx.drawBorder(x+w-5, y+h/2-5, 10, 10, C_WHITE);

        // Ресайз угол
        ctx.fill(x+w-7, y+h-7, x+w, y+h, 0xFF555555);
    }

    private void renderMinimap(DrawContext ctx) {
        int mmX = 4, mmY = TOP_H + 22 + TOOLS.length*22 + 20;
        int mmW = LEFT_W - 8, mmH = 80;
        ctx.fill(mmX, mmY, mmX+mmW, mmY+mmH, 0xFF0A0A0A);
        ctx.drawBorder(mmX, mmY, mmW, mmH, C_BORDER);

        for (RecipeNode node : NodeGraph.getInstance().getNodes()) {
            int nx = mmX + (int)(node.x / 20.0 * mmW / 50) + mmW/2;
            int ny = mmY + (int)(node.y / 20.0 * mmH / 50) + mmH/2;
            if (nx >= mmX && nx < mmX+mmW && ny >= mmY && ny < mmY+mmH)
                ctx.fill(nx, ny, nx+4, ny+3, node.autoCraftOn ? C_ACCENT : C_GRAY);
        }
        ctx.drawTextWithShadow(textRenderer, Text.literal("100%"), mmX+2, mmY+mmH+2, C_GRAY);
    }

    // ─────────────── EDIT PANEL ───────────────

    private void renderEditPanel(DrawContext ctx, int mx, int my) {
        int pw = 420, ph = 340;
        int px = (width - pw) / 2, py = (height - ph) / 2;

        ctx.fill(px-1, py-1, px+pw+1, py+ph+1, C_ACCENT);
        ctx.fill(px, py, px+pw, py+ph, C_PANEL);

        // Заголовок
        ctx.fill(px, py, px+pw, py+22, 0xFF162716);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Узел: " + editingNode.id), px+8, py+7, C_WHITE);
        // Режим badge
        String modeTxt = "● Режим: " + (editingNode.autoCraftOn ? "АКТИВЕН" : "ВЫКЛ");
        int modeCol = editingNode.autoCraftOn ? C_GREEN : C_GRAY;
        ctx.drawTextWithShadow(textRenderer, Text.literal(modeTxt), px+180, py+7, modeCol);
        // Кнопка закрыть
        smallBtn(ctx, px+pw-22, py+4, 16, 14, "✕", mx, my, C_RED);

        // Левая часть — рецепт
        ctx.drawTextWithShadow(textRenderer, Text.literal("Рецепт крафта"), px+8, py+28, C_GRAY);

        // Сетка 3x3
        for (int i = 0; i < 9; i++) {
            int col = i%3, row = i/3;
            int sx = px+8 + col*44, sy = py+40 + row*44;
            boolean sel = editingSlot == i;
            ctx.fill(sx, sy, sx+40, sy+40, sel ? 0xFF2A3A2A : 0xFF1A1A1A);
            ctx.drawBorder(sx, sy, 40, 40, sel ? C_ACCENT : C_BORDER);
            ItemStack stack = editingNode.inputs[i];
            if (stack != null && !stack.isEmpty()) {
                String nm = stack.getItem().toString().replace("minecraft:","");
                if (nm.length() > 6) nm = nm.substring(0,6);
                ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(nm), sx+20, sy+8, C_WHITE);
                ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("×"+stack.getCount()), sx+20, sy+24, C_GRAY);
            }
        }

        // Стрелка и результат
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("→"), px+148, py+90, C_WHITE);
        boolean resSel = editingSlot == 9;
        ctx.fill(px+160, py+72, px+200, py+112, resSel ? 0xFF2A3A2A : 0xFF1A1A1A);
        ctx.drawBorder(px+160, py+72, 40, 40, resSel ? C_ACCENT : C_BORDER);
        if (!editingNode.result.isEmpty()) {
            String rn = editingNode.result.getItem().toString().replace("minecraft:","");
            if (rn.length() > 6) rn = rn.substring(0,6);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(rn), px+180, py+82, C_WHITE);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("×"+editingNode.result.getCount()), px+180, py+96, C_GRAY);
        } else {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("?"), px+180, py+88, C_GRAY);
        }

        // Инвентарь (показываем что есть у игрока)
        ctx.drawTextWithShadow(textRenderer, Text.literal("Инвентарь (входы):"), px+8, py+180, C_GRAY);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            int shown = 0;
            for (int i = 0; i < mc.player.getInventory().size() && shown < 8; i++) {
                ItemStack inv = mc.player.getInventory().getStack(i);
                if (!inv.isEmpty()) {
                    int ix = px+8 + shown*26, iy = py+192;
                    ctx.fill(ix, iy, ix+22, iy+22, 0xFF1A1A1A);
                    ctx.drawBorder(ix, iy, 22, 22, C_BORDER);
                    String nm = inv.getItem().toString().replace("minecraft:","");
                    if (nm.length()>3) nm=nm.substring(0,3);
                    ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(nm), ix+11, iy+3, C_WHITE);
                    ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(""+inv.getCount()), ix+11, iy+14, C_GRAY);
                    shown++;
                }
            }
        }

        // Правая часть — настройки узла
        int rx = px+220;
        ctx.fill(rx, py+22, rx+1, py+ph, C_BORDER);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Настройки узла"), rx+8, py+28, C_GRAY);

        // Название
        ctx.drawTextWithShadow(textRenderer, Text.literal("Название:"), rx+8, py+44, C_LTGRAY);
        ctx.fill(rx+8, py+54, rx+188, py+68, 0xFF1A1A1A);
        ctx.drawBorder(rx+8, py+54, 180, 14, C_BORDER);
        ctx.drawTextWithShadow(textRenderer, Text.literal(editingNode.id), rx+10, py+57, C_WHITE);

        // Режим
        ctx.drawTextWithShadow(textRenderer, Text.literal("Режим:"), rx+8, py+76, C_LTGRAY);
        boolean modeHov = mx >= rx+8 && mx <= rx+188 && my >= py+86 && my <= py+100;
        ctx.fill(rx+8, py+86, rx+188, py+100, modeHov ? C_PANEL2 : 0xFF1A1A1A);
        ctx.drawBorder(rx+8, py+86, 180, 14, C_BORDER);
        ctx.drawTextWithShadow(textRenderer, Text.literal(editingNode.mode + " ▾"), rx+10, py+89, C_WHITE);

        // Приоритет
        ctx.drawTextWithShadow(textRenderer, Text.literal("Приоритет: " + editingNode.priority), rx+8, py+108, C_LTGRAY);
        smallBtn(ctx, rx+100, py+105, 20, 12, "▲", mx, my, C_PANEL2);
        smallBtn(ctx, rx+124, py+105, 20, 12, "▼", mx, my, C_PANEL2);

        // Авто-крафт тоггл
        ctx.drawTextWithShadow(textRenderer, Text.literal("Авто-крафт:"), rx+8, py+126, C_LTGRAY);
        boolean autoHov = mx >= rx+100 && mx <= rx+188 && my >= py+122 && my <= py+136;
        ctx.fill(rx+100, py+122, rx+188, py+136, editingNode.autoCraftOn ? C_HDR_ON : 0xFF3A1A1A);
        ctx.drawBorder(rx+100, py+122, 88, 14, editingNode.autoCraftOn ? C_ACCENT : C_RED);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(editingNode.autoCraftOn ? "ВКЛ ✔" : "ВЫКЛ ✘"), rx+144, py+125, C_WHITE);

        // Статистика
        ctx.drawTextWithShadow(textRenderer, Text.literal("Создано: " + editingNode.craftCount), rx+8, py+144, C_LTGRAY);

        // Поле ввода предмета
        ctx.drawTextWithShadow(textRenderer, Text.literal("Предмет для слота " + (editingSlot<0?"—":editingSlot==9?"(результат)":String.valueOf(editingSlot+1))+":"), rx+8, py+162, C_GRAY);
        ctx.fill(rx+8, py+172, rx+188, py+186, 0xFF1A1A1A);
        ctx.drawBorder(rx+8, py+172, 180, 14, editingSlot>=0 ? C_ACCENT : C_BORDER);
        ctx.drawTextWithShadow(textRenderer, Text.literal(inputBuffer + "|"), rx+10, py+175, C_WHITE);

        // Кнопки нижние
        smallBtn(ctx, rx+8, py+ph-40, 85, 16, "✔ Применить", mx, my, C_ACCENT);
        smallBtn(ctx, rx+100, py+ph-40, 85, 16, "✘ Очистить", mx, my, C_RED);
        smallBtn(ctx, rx+8, py+ph-20, 85, 16, "🔍 Выбрать...", mx, my, C_ACCENT2);
        smallBtn(ctx, rx+100, py+ph-20, 85, 16, "Сохранить", mx, my, 0xFF37474F);
    }

    // ─────────────── ITEM PICKER ───────────────

    private void renderItemPicker(DrawContext ctx, int mx, int my) {
        int pw = 500, ph = 380;
        int px = (width-pw)/2, py = (height-ph)/2;

        ctx.fill(px-1, py-1, px+pw+1, py+ph+1, C_ACCENT);
        ctx.fill(px, py, px+pw, py+ph, C_PANEL);
        ctx.fill(px, py, px+pw, py+22, 0xFF162716);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Выбор предмета/блока"), px+8, py+7, C_WHITE);
        smallBtn(ctx, px+pw-22, py+4, 16, 14, "✕", mx, my, C_RED);

        // Категории
        String[] cats = {"Все","Блоки","Предметы","Природа","Еда","Инструменты","Броня","Зелья","Разное"};
        int catX = px+4;
        for (String cat : cats) {
            int cw = textRenderer.getWidth(cat)+8;
            boolean sel = cat.equals(selectedCategory);
            boolean hov = mx >= catX && mx <= catX+cw && my >= py+24 && my <= py+38;
            ctx.fill(catX, py+24, catX+cw, py+38, sel ? C_ACCENT : (hov ? C_PANEL2 : C_PANEL));
            ctx.drawBorder(catX, py+24, cw, 14, sel ? C_ACCENT : C_BORDER);
            ctx.drawTextWithShadow(textRenderer, Text.literal(cat), catX+4, py+27, sel ? C_WHITE : C_GRAY);
            catX += cw+2;
        }

        // Поиск
        ctx.fill(px+4, py+42, px+pw-80, py+56, 0xFF1A1A1A);
        ctx.drawBorder(px+4, py+42, pw-84, 14, C_BORDER);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Поиск: " + searchQuery + "|"), px+8, py+45, C_WHITE);

        // Сетка предметов
        int startIdx = itemPickerPage * ITEMS_PER_PAGE;
        int cols = 10, rows = 4;
        for (int i = 0; i < Math.min(ITEMS_PER_PAGE, filteredItems.size()-startIdx); i++) {
            Item item = filteredItems.get(startIdx+i);
            int col = i%cols, row = i/cols;
            int ix = px+4 + col*46, iy = py+60 + row*46;
            boolean hov = mx >= ix && mx <= ix+42 && my >= iy && my <= iy+42;
            ctx.fill(ix, iy, ix+42, iy+42, hov ? 0xFF2A3A2A : 0xFF1A1A1A);
            ctx.drawBorder(ix, iy, 42, 42, hov ? C_ACCENT : C_BORDER);
            String nm = item.toString().replace("minecraft:","");
            if (nm.length()>6) nm=nm.substring(0,6);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(nm), ix+21, iy+8, C_WHITE);
        }

        // Правая — информация о выбранном
        int infoX = px+pw-80;
        ctx.fill(infoX, py+24, infoX+1, py+ph, C_BORDER);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Информация"), infoX+4, py+28, C_GRAY);

        // Пагинация
        int pages = (int)Math.ceil(filteredItems.size() / (double)ITEMS_PER_PAGE);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("◀ " + (itemPickerPage+1) + " / " + Math.max(1,pages) + " ▶"), px+pw/2-40, py+ph-18, C_GRAY);

        // Кнопка Выбрать
        smallBtn(ctx, px+pw-78, py+ph-22, 70, 16, "Выбрать", mx, my, C_ACCENT);
        smallBtn(ctx, px+pw-78, py+ph-40, 70, 16, "Отмена", mx, my, C_RED);
    }

    // ─────────────── SETTINGS ───────────────

    private void renderSettings(DrawContext ctx, int mx, int my) {
        int pw = 360, ph = 280;
        int px = (width-pw)/2, py = (height-ph)/2;
        ctx.fill(px, py, px+pw, py+ph, C_PANEL);
        ctx.drawBorder(px, py, pw, ph, C_ACCENT);
        ctx.fill(px, py, px+pw, py+22, 0xFF162716);
        ctx.drawTextWithShadow(textRenderer, Text.literal("⚙ Настройки редактора"), px+8, py+7, C_WHITE);
        smallBtn(ctx, px+pw-22, py+4, 16, 14, "✕", mx, my, C_RED);

        String[] tabs = {"Основные","Отображение","Управление","Прочее"};
        int tx = px+4;
        for (String tab : tabs) {
            int tw = textRenderer.getWidth(tab)+8;
            ctx.fill(tx, py+24, tx+tw, py+36, C_PANEL2);
            ctx.drawBorder(tx, py+24, tw, 12, C_BORDER);
            ctx.drawTextWithShadow(textRenderer, Text.literal(tab), tx+4, py+27, C_GRAY);
            tx += tw+2;
        }

        ctx.drawTextWithShadow(textRenderer, Text.literal("Сетка"), px+8, py+44, C_LTGRAY);
        ctx.drawTextWithShadow(textRenderer, Text.literal("☑ Показывать сетку"), px+12, py+56, C_WHITE);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Размер сетки: 32"), px+12, py+68, C_WHITE);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Привязка"), px+8, py+84, C_LTGRAY);
        ctx.drawTextWithShadow(textRenderer, Text.literal("☑ Привязка к сетке"), px+12, py+96, C_WHITE);
        ctx.drawTextWithShadow(textRenderer, Text.literal("☑ Привязка к узлам"), px+12, py+108, C_WHITE);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Соединения"), px+8, py+124, C_LTGRAY);
        ctx.drawTextWithShadow(textRenderer, Text.literal("☑ Показывать соединения"), px+12, py+136, C_WHITE);
        ctx.drawTextWithShadow(textRenderer, Text.literal("☑ Анимация потоков"), px+12, py+148, C_WHITE);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Толщина линий: 2"), px+12, py+160, C_WHITE);

        // Инструменты
        int itX = px+pw/2;
        ctx.drawTextWithShadow(textRenderer, Text.literal("Инструменты"), itX, py+44, C_LTGRAY);
        smallBtn(ctx, itX, py+56, 100, 14, "Выделить все", mx, my, C_PANEL2);
        smallBtn(ctx, itX, py+74, 100, 14, "Очистить выделение", mx, my, C_PANEL2);
        smallBtn(ctx, itX, py+92, 100, 14, "Удалить выделенное", mx, my, C_RED);
        smallBtn(ctx, itX, py+110, 100, 14, "Копировать", mx, my, C_PANEL2);
        smallBtn(ctx, itX, py+128, 100, 14, "Вставить", mx, my, C_PANEL2);
        smallBtn(ctx, itX, py+146, 100, 14, "Дублировать", mx, my, C_PANEL2);

        smallBtn(ctx, px+8, py+ph-22, 80, 16, "Сбросить", mx, my, C_PANEL2);
        smallBtn(ctx, px+pw-90, py+ph-22, 80, 16, "Закрыть", mx, my, C_RED);
    }

    // ─────────────── AUTO STATUS ───────────────

    private void renderAutoStatus(DrawContext ctx, int mx, int my) {
        int pw = 360, ph = 280;
        int px = (width-pw)/2, py = (height-ph)/2;
        ctx.fill(px, py, px+pw, py+ph, C_PANEL);
        ctx.drawBorder(px, py, pw, ph, C_ACCENT);
        ctx.fill(px, py, px+pw, py+22, 0xFF162716);
        ctx.drawTextWithShadow(textRenderer, Text.literal("⚙ Авто-крафт — управление"), px+8, py+7, C_WHITE);
        smallBtn(ctx, px+pw-22, py+4, 16, 14, "✕", mx, my, C_RED);

        // Большая кнопка
        boolean anyActive = NodeGraph.getInstance().getNodes().stream().anyMatch(n->n.autoCraftOn);
        int btnX = px+8, btnY = py+28, btnW = 80, btnH = 60;
        ctx.fill(btnX, btnY, btnX+btnW, btnY+btnH, anyActive ? 0xFF1B5E20 : 0xFF37474F);
        ctx.drawBorder(btnX, btnY, btnW, btnH, anyActive ? C_ACCENT : C_BORDER);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("▶"), btnX+btnW/2, btnY+10, C_WHITE);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Авто-"), btnX+btnW/2, btnY+28, C_WHITE);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("крафтинг"), btnX+btnW/2, btnY+38, C_WHITE);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(anyActive ? "ВКЛЮЧЁН" : "ВЫКЛЮЧЕН"), btnX+btnW/2, btnY+50, anyActive ? C_GREEN : C_GRAY);

        // Статистика
        long total = NodeGraph.getInstance().getNodes().stream().mapToLong(n->n.craftCount).sum();
        long noMat  = NodeGraph.getInstance().getNodes().stream().filter(n->n.autoCraftOn&&!n.isActive).count();
        ctx.drawTextWithShadow(textRenderer, Text.literal("Режим: Авто-крафт в узлах"), px+100, py+34, C_LTGRAY);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Успешно: " + total), px+100, py+50, C_GREEN);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Нет материалов: " + noMat), px+100, py+62, C_DKRED);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Проверка каждые 1.0 сек"), px+100, py+74, C_GRAY);

        // Список нод
        ctx.drawTextWithShadow(textRenderer, Text.literal("Узлы:"), px+8, py+100, C_GRAY);
        int rowY = py+112;
        MinecraftClient mc = MinecraftClient.getInstance();
        for (RecipeNode node : NodeGraph.getInstance().getNodes()) {
            if (rowY > py+ph-30) break;
            String st = node.autoCraftOn ? (node.isActive ? "✔" : "✘") : "—";
            int stCol = node.autoCraftOn ? (node.isActive ? C_GREEN : C_DKRED) : C_GRAY;
            ctx.drawTextWithShadow(textRenderer, Text.literal(st + " " + node.id + " × " + node.craftCount), px+8, rowY, stCol);
            if (node.autoCraftOn && !node.isActive && mc.player != null) {
                String miss = CraftExecutor.getMissingInfo(node, mc.player);
                if (!miss.isEmpty())
                    ctx.drawTextWithShadow(textRenderer, Text.literal("    ↳ " + miss), px+8, rowY+10, C_DKRED);
                rowY += 10;
            }
            rowY += 12;
        }

        smallBtn(ctx, px+8, py+ph-22, 100, 16, "■ Остановить всё", mx, my, C_RED);
    }

    private void smallBtn(DrawContext ctx, int x, int y, int w, int h, String label, int mx, int my, int col) {
        boolean hov = mx >= x && mx <= x+w && my >= y && my <= y+h;
        ctx.fill(x, y, x+w, y+h, hov ? lighten(col) : col);
        ctx.drawBorder(x, y, w, h, C_BORDER);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(label), x+w/2, y+(h-7)/2, C_WHITE);
    }

    // ─────────────── MOUSE / KEY ───────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int)mouseX, my = (int)mouseY;

        // Клик в панелях
        if (activePanel == Panel.EDIT_NODE) return handleEditClick(mx, my, button);
        if (activePanel == Panel.ITEM_PICKER) return handlePickerClick(mx, my, button);
        if (activePanel == Panel.SETTINGS) { if (isCloseBtn(mx,my,button)) { activePanel=Panel.NONE; return true; } return true; }
        if (activePanel == Panel.AUTO_STATUS) return handleAutoStatusClick(mx, my, button);

        // Верхняя панель
        if (my < TOP_H) return handleTopBarClick(mx, my, button);

        // Левая панель
        if (mx < LEFT_W) return handleLeftPanelClick(mx, my, button);

        // Статусбар — кнопка авто-статус
        if (my >= height-20 && mx >= width-160) { activePanel = Panel.AUTO_STATUS; return true; }

        // Холст
        return handleCanvasClick(mx, my, button);
    }

    private boolean handleTopBarClick(int mx, int my, int button) {
        // Кнопка "Добавить ноду"
        int x = LEFT_W+5;
        int w = textRenderer.getWidth("＋ Добавить ноду")+10;
        if (mx >= x && mx <= x+w && my >= 3 && my <= 27) { addNode(); return true; }
        // Настройки
        // (упрощённо — любой клик в топбаре после кнопок открывает настройки если на ⚙)
        return true;
    }

    private boolean handleLeftPanelClick(int mx, int my, int button) {
        for (int i = 0; i < TOOLS.length; i++) {
            int ty = TOP_H + 22 + i * 22;
            if (mx >= 4 && mx <= LEFT_W-4 && my >= ty && my <= ty+18) {
                activeTool = i;
                return true;
            }
        }
        return true;
    }

    private boolean handleCanvasClick(int mx, int my, int button) {
        int cx = LEFT_W, cy = TOP_H;
        List<RecipeNode> nodes = NodeGraph.getInstance().getNodes();
        for (int i = nodes.size()-1; i >= 0; i--) {
            RecipeNode node = nodes.get(i);
            int nx = cx + canvasOffX + (int)(node.x*zoom);
            int ny = cy + canvasOffY + (int)(node.y*zoom);
            int nw = (int)(node.w*zoom), nh = (int)(node.h*zoom);
            int HEADER_H = Math.max(16,(int)(16*zoom));
            int btnY = ny+nh-14;

            if (mx < nx-6 || mx > nx+nw+6 || my < ny || my > ny+nh) continue;

            // ПКМ — удалить
            if (button == 1) {
                NodeGraph.getInstance().removeNode(node.id);
                selected.remove(node);
                return true;
            }

            // Авто-крафт кнопка
            if (mx >= nx+4 && mx <= nx+nw-4 && my >= btnY && my <= btnY+12) {
                node.autoCraftOn = !node.autoCraftOn;
                return true;
            }

            // Порт выход
            if (mx >= nx+nw-6 && mx <= nx+nw+6 && my >= ny+nh/2-6 && my <= ny+nh/2+6) {
                connectFrom = node;
                return true;
            }

            // Порт вход — завершить соединение
            if (mx >= nx-6 && mx <= nx+6 && my >= ny+nh/2-6 && my <= ny+nh/2+6) {
                if (connectFrom != null && !connectFrom.id.equals(node.id)) {
                    NodeGraph.getInstance().addConnection(connectFrom.id, node.id);
                    connectFrom = null;
                    return true;
                }
            }

            // Ресайз
            if (mx >= nx+nw-8 && my >= ny+nh-8) {
                resizeNode = node; resizeStartW = node.w; resizeStartH = node.h;
                resizeMouseX = mx; resizeMouseY = my;
                return true;
            }

            // Клик по заголовку — перетащить или выделить
            if (my >= ny && my <= ny+HEADER_H) {
                dragNode = node; dragOffX = mx-nx; dragOffY = my-ny;
                if (!selected.contains(node)) { selected.clear(); selected.add(node); }
                return true;
            }

            // Клик по телу — открыть редактор
            if (my > ny+HEADER_H && my < btnY) {
                editingNode = node; editingSlot = -1; inputBuffer = "";
                activePanel = Panel.EDIT_NODE;
                return true;
            }
        }

        // Клик по пустому месту — перетащить холст или сбросить выделение
        connectFrom = null;
        selected.clear();
        draggingCanvas = true;
        canvasDragStartX = mx - canvasOffX;
        canvasDragStartY = my - canvasOffY;
        return true;
    }

    private boolean handleEditClick(int mx, int my, int button) {
        int pw = 420, ph = 340;
        int px = (width-pw)/2, py = (height-ph)/2;
        int rx = px+220;

        // Закрыть
        if (mx >= px+pw-22 && mx <= px+pw-6 && my >= py+4 && my <= py+18) {
            activePanel = Panel.NONE; return true;
        }

        // Слоты 3x3
        for (int i = 0; i < 9; i++) {
            int col=i%3, row=i/3;
            int sx=px+8+col*44, sy=py+40+row*44;
            if (mx>=sx && mx<=sx+40 && my>=sy && my<=sy+40) {
                editingSlot=i; inputBuffer=""; return true;
            }
        }
        // Результат
        if (mx>=px+160 && mx<=px+200 && my>=py+72 && my<=py+112) {
            editingSlot=9; inputBuffer=""; return true;
        }

        // Авто-крафт тоггл
        if (mx>=rx+100 && mx<=rx+188 && my>=py+122 && my<=py+136) {
            editingNode.autoCraftOn = !editingNode.autoCraftOn; return true;
        }

        // Приоритет
        if (mx>=rx+100 && mx<=rx+120 && my>=py+105 && my<=py+117) { editingNode.priority++; return true; }
        if (mx>=rx+124 && mx<=rx+144 && my>=py+105 && my<=py+117) { editingNode.priority--; return true; }

        // Применить
        if (mx>=rx+8 && mx<=rx+93 && my>=py+ph-40 && my<=py+ph-24) { applyInput(); return true; }
        // Очистить
        if (mx>=rx+100 && mx<=rx+185 && my>=py+ph-40 && my<=py+ph-24) { clearSlot(); return true; }
        // Выбрать предмет
        if (mx>=rx+8 && mx<=rx+93 && my>=py+ph-20 && my<=py+ph-4) {
            pickingForSlot = true; activePanel = Panel.ITEM_PICKER;
            rebuildItemList(); return true;
        }

        return true;
    }

    private boolean handlePickerClick(int mx, int my, int button) {
        int pw = 500, ph = 380;
        int px = (width-pw)/2, py = (height-ph)/2;

        // Закрыть
        if (mx>=px+pw-22 && mx<=px+pw-6 && my>=py+4 && my<=py+18) {
            activePanel = pickingForSlot ? Panel.EDIT_NODE : Panel.NONE; return true;
        }

        // Категории
        String[] cats = {"Все","Блоки","Предметы","Природа","Еда","Инструменты","Броня","Зелья","Разное"};
        int catX = px+4;
        for (String cat : cats) {
            int cw = textRenderer.getWidth(cat)+8;
            if (mx>=catX && mx<=catX+cw && my>=py+24 && my<=py+38) {
                selectedCategory=cat; itemPickerPage=0; rebuildItemList(); return true;
            }
            catX += cw+2;
        }

        // Поиск
        if (mx>=px+4 && mx<=px+pw-80 && my>=py+42 && my<=py+56) return true;

        // Клик по предмету
        int startIdx = itemPickerPage * ITEMS_PER_PAGE;
        int cols = 10;
        for (int i = 0; i < Math.min(ITEMS_PER_PAGE, filteredItems.size()-startIdx); i++) {
            int col=i%cols, row=i/cols;
            int ix=px+4+col*46, iy=py+60+row*46;
            if (mx>=ix && mx<=ix+42 && my>=iy && my<=iy+42) {
                Item item = filteredItems.get(startIdx+i);
                applyItemToSlot(item);
                activePanel = Panel.EDIT_NODE;
                return true;
            }
        }

        // Пагинация
        int pages = (int)Math.ceil(filteredItems.size()/(double)ITEMS_PER_PAGE);
        if (mx >= px+pw/2-60 && mx <= px+pw/2-40 && my >= py+ph-20 && my <= py+ph) {
            if (itemPickerPage > 0) itemPickerPage--;
        }
        if (mx >= px+pw/2-20 && mx <= px+pw/2 && my >= py+ph-20 && my <= py+ph) {
            if (itemPickerPage < pages-1) itemPickerPage++;
        }

        return true;
    }

    private boolean handleAutoStatusClick(int mx, int my, int button) {
        int pw=360,ph=280;
        int px=(width-pw)/2, py=(height-ph)/2;
        if (mx>=px+pw-22 && mx<=px+pw-6 && my>=py+4 && my<=py+18) { activePanel=Panel.NONE; return true; }
        // Остановить всё
        if (mx>=px+8 && mx<=px+108 && my>=py+ph-22 && my<=py+ph-6) {
            NodeGraph.getInstance().getNodes().forEach(n -> n.autoCraftOn = false);
            return true;
        }
        return true;
    }

    private boolean isCloseBtn(int mx, int my, int button) {
        // Упрощённая проверка — будет переопределена в каждой панели
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        int mx=(int)mouseX, my=(int)mouseY;
        if (dragNode != null) {
            int nx = mx - LEFT_W - canvasOffX - dragOffX;
            int ny = my - TOP_H - canvasOffY - dragOffY;
            dragNode.x = (int)(nx / zoom);
            dragNode.y = (int)(ny / zoom);
            return true;
        }
        if (resizeNode != null) {
            resizeNode.w = Math.max(140, resizeStartW + (int)((mx-resizeMouseX)/zoom));
            resizeNode.h = Math.max(80,  resizeStartH + (int)((my-resizeMouseY)/zoom));
            return true;
        }
        if (draggingCanvas) {
            canvasOffX = mx - canvasDragStartX;
            canvasOffY = my - canvasDragStartY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragNode = null; resizeNode = null; draggingCanvas = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX > LEFT_W && mouseY > TOP_H && mouseY < height-20 && activePanel == Panel.NONE) {
            zoom = Math.max(0.3f, Math.min(2.0f, zoom + (float)verticalAmount * 0.1f));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (activePanel == Panel.EDIT_NODE || activePanel == Panel.ITEM_PICKER) {
            if (keyCode == 259 && !inputBuffer.isEmpty()) { inputBuffer=inputBuffer.substring(0,inputBuffer.length()-1); return true; }
            if (keyCode == 257 || keyCode == 335) { applyInput(); return true; }
            if (keyCode == 256) { activePanel = Panel.NONE; return true; }
            return true;
        }
        if (keyCode == 256) { this.close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if ((activePanel == Panel.EDIT_NODE || activePanel == Panel.ITEM_PICKER) && editingSlot >= 0) {
            if (activePanel == Panel.ITEM_PICKER) {
                searchQuery += chr;
                rebuildItemList();
            } else {
                inputBuffer += chr;
            }
            return true;
        }
        return false;
    }

    // ─────────────── HELPERS ───────────────

    private void applyInput() {
        if (editingNode == null || editingSlot < 0 || inputBuffer.isEmpty()) return;
        String itemId = inputBuffer.trim().toLowerCase();
        if (!itemId.contains(":")) itemId = "minecraft:" + itemId;
        try {
            Item item = Registries.ITEM.get(Identifier.of(itemId));
            if (item == Items.AIR) return;
            applyItemToSlot(item);
            inputBuffer = ""; editingSlot = -1;
        } catch (Exception ignored) {}
    }

    private void applyItemToSlot(Item item) {
        if (editingNode == null || editingSlot < 0) return;
        ItemStack stack = new ItemStack(item, 1);
        if (editingSlot == 9) editingNode.result = stack;
        else editingNode.inputs[editingSlot] = stack;
    }

    private void clearSlot() {
        if (editingNode == null || editingSlot < 0) return;
        if (editingSlot == 9) editingNode.result = ItemStack.EMPTY;
        else editingNode.inputs[editingSlot] = ItemStack.EMPTY;
        inputBuffer = "";
    }

    private void addNode() {
        int count = NodeGraph.getInstance().getNodes().size();
        RecipeNode node = new RecipeNode("Рецепт " + (count+1));
        node.x = 60 + (count*190) % 600;
        node.y = 60 + (count/4)*130;
        node.w = 180; node.h = 110;
        NodeGraph.getInstance().addNode(node);
    }

    private void rebuildItemList() {
        filteredItems.clear();
        String q = searchQuery.toLowerCase();
        for (Item item : Registries.ITEM) {
            String id = item.toString().replace("minecraft:","");
            if (!q.isEmpty() && !id.contains(q)) continue;
            filteredItems.add(item);
        }
        itemPickerPage = 0;
    }

    private void drawBezier(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(20, Math.abs(x2-x1)/2);
        int cx1 = x1 + Math.abs(x2-x1)/2, cy1 = y1;
        int cx2 = x2 - Math.abs(x2-x1)/2, cy2 = y2;
        for (int i = 0; i <= steps; i++) {
            float t = i/(float)steps;
            float u = 1-t;
            int px = (int)(u*u*u*x1 + 3*u*u*t*cx1 + 3*u*t*t*cx2 + t*t*t*x2);
            int py = (int)(u*u*u*y1 + 3*u*u*t*cy1 + 3*u*t*t*cy2 + t*t*t*y2);
            ctx.fill(px, py, px+2, py+2, color);
        }
    }

    private int lighten(int color) {
        int r = Math.min(255, ((color>>16)&0xFF)+30);
        int g = Math.min(255, ((color>>8)&0xFF)+30);
        int b = Math.min(255, (color&0xFF)+30);
        return (color & 0xFF000000) | (r<<16) | (g<<8) | b;
    }

    @Override public boolean shouldPause() { return false; }
    @Override public boolean shouldCloseOnEsc() { return true; }
}
