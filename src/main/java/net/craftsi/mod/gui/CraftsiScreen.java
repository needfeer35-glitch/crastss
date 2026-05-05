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

    private enum Panel { NONE, EDIT_NODE, ITEM_PICKER, SETTINGS, AUTO_PANEL, ADD_NODE }
    private Panel activePanel = Panel.NONE;

    // Canvas
    private int canvasOffX = 0, canvasOffY = 0;
    private boolean draggingCanvas = false;
    private int canvasDragStartX, canvasDragStartY;
    private float zoom = 1.0f;

    // Node interaction
    private RecipeNode dragNode = null;
    private int dragOffX, dragOffY;
    private RecipeNode resizeNode = null;
    private int resizeStartW, resizeStartH, resizeMouseX, resizeMouseY;
    private RecipeNode connectFrom = null;
    private Set<RecipeNode> selected = new HashSet<>();

    // Edit panel
    private RecipeNode editingNode = null;
    private int editingSlot = -1;
    private String inputBuffer = "";
    private int editingCount = 1;
    private String editingName = "";
    private boolean editingNameActive = false;

    // Item picker
    private String searchQuery = "";
    private List<Item> allItems = new ArrayList<>();
    private List<Item> filteredItems = new ArrayList<>();
    private int pickerScroll = 0;
    private Item hoveredItem = null;
    private String pickerCategory = "Все";
    private static final int PCOLS = 9;
    private static final int PSLOT = 40;

    // Auto panel
    private long lastCraftTime = 0;

    // Settings tab
    private int settingsTab = 0;

    // Russian item name map
    private static final Map<String, String> RU = new HashMap<>();
    static {
        RU.put("палка","stick"); RU.put("дерево","log"); RU.put("доски","planks");
        RU.put("камень","stone"); RU.put("земля","dirt"); RU.put("песок","sand");
        RU.put("железо","iron_ingot"); RU.put("золото","gold_ingot");
        RU.put("алмаз","diamond"); RU.put("уголь","coal"); RU.put("факел","torch");
        RU.put("верстак","crafting_table"); RU.put("печь","furnace");
        RU.put("сундук","chest"); RU.put("стекло","glass"); RU.put("шерсть","white_wool");
        RU.put("кирпич","brick"); RU.put("книга","book"); RU.put("лук","bow");
        RU.put("стрела","arrow"); RU.put("кирка","wooden_pickaxe");
        RU.put("яблоко","apple"); RU.put("хлеб","bread"); RU.put("ведро","bucket");
        RU.put("нить","string"); RU.put("перо","feather"); RU.put("кожа","leather");
        RU.put("кость","bone"); RU.put("порох","gunpowder");
        RU.put("дубовые доски","oak_planks"); RU.put("дубовое бревно","oak_log");
        RU.put("берёзовые доски","birch_planks"); RU.put("железная кирка","iron_pickaxe");
        RU.put("алмазный меч","diamond_sword"); RU.put("железный меч","iron_sword");
        RU.put("каменный меч","stone_sword");
    }

    // Colors
    private static final int BG        = 0xFF0D0F1A;
    private static final int CANVAS    = 0xFF0A0C14;
    private static final int PBG       = 0xFF13151F;
    private static final int PDARK     = 0xFF0D0F18;
    private static final int PLIGHT    = 0xFF1A1D2E;
    private static final int BOR       = 0xFF252840;
    private static final int BORL      = 0xFF3A3E60;
    private static final int GREEN     = 0xFF00C853;
    private static final int GREEND    = 0xFF003818;
    private static final int BLUE      = 0xFF2979FF;
    private static final int BLUED     = 0xFF001850;
    private static final int YELLOW    = 0xFFFFD600;
    private static final int RED       = 0xFFE53935;
    private static final int REDD      = 0xFF3A0808;
    private static final int ORANGE    = 0xFFFF6D00;
    private static final int NODEBG    = 0xFF111320;
    private static final int NODEHOV   = 0xFF181B2E;
    private static final int NODESEL   = 0xFF1A2040;
    private static final int HDRON     = 0xFF003818;
    private static final int HDROFF    = 0xFF380808;
    private static final int PORT      = 0xFFFFD600;
    private static final int LINE      = 0xCC00C853;
    private static final int WHITE     = 0xFFFFFFFF;
    private static final int GRAY      = 0xFF5A5E80;
    private static final int LGRAY     = 0xFFAAB0CC;
    private static final int DGRAY     = 0xFF1E2035;

    // Layout
    private static final int LW  = 120; // left panel width
    private static final int TH  = 32;  // top bar height
    private static final int BH  = 22;  // bottom bar height

    private static final String[] LEFT_TOOLS = {
        "Выделить", "Переместить", "Соединить", "Удалить",
        "Авто-раскладка", "Масштаб"
    };
    private int activeTool = 0;

    public CraftsiScreen() {
        super(Text.literal("Craftsi"));
    }

    @Override
    protected void init() {
        super.init();
        allItems.clear();
        for (Item it : Registries.ITEM) allItems.add(it);
        rebuildFilter();
    }

    // ═══════════════════════════════════════════════════
    //  RENDER
    // ═══════════════════════════════════════════════════

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, BG);
        renderCanvas(ctx, mx, my);
        renderTopBar(ctx, mx, my);
        renderLeftPanel(ctx, mx, my);
        renderBottomBar(ctx, mx, my);

        if (activePanel == Panel.EDIT_NODE && editingNode != null) renderEditPanel(ctx, mx, my);
        if (activePanel == Panel.ITEM_PICKER) renderItemPicker(ctx, mx, my);
        if (activePanel == Panel.SETTINGS) renderSettings(ctx, mx, my);
        if (activePanel == Panel.AUTO_PANEL) renderAutoPanel(ctx, mx, my);
        if (activePanel == Panel.ADD_NODE) renderAddNode(ctx, mx, my);

        super.render(ctx, mx, my, delta);
    }

    // ─── TOP BAR ─────────────────────────────────────

    private void renderTopBar(DrawContext ctx, int mx, int my) {
        ctx.fill(0, 0, width, TH, PDARK);
        ctx.fill(0, TH - 1, width, TH, BOR);

        // Left title area
        ctx.fill(0, 0, LW, TH, 0xFF0A0C14);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("CRAFTSI"), LW / 2, 11, YELLOW);

        int x = LW + 8;
        x = topBtn(ctx, "+ Добавить ноду", x, mx, my, GREEND, GREEN) + 4;
        x = topBtn(ctx, "Новый", x, mx, my, PDARK, BORL) + 4;
        x = topBtn(ctx, "Открыть", x, mx, my, PDARK, BORL) + 4;
        x = topBtn(ctx, "Сохранить", x, mx, my, PDARK, BORL) + 4;
        x = topBtn(ctx, "Экспорт", x, mx, my, PDARK, BORL) + 4;
        x = topBtn(ctx, "Авто-раскладка", x, mx, my, PDARK, BORL) + 4;

        // Right side
        int rx = width - 8;
        rx -= 24;
        topIconBtn(ctx, "...", rx, mx, my, PDARK, BORL);
        rx -= 60;
        ctx.drawTextWithShadow(textRenderer, Text.literal((int)(zoom * 100) + "%"), rx + 20, 11, LGRAY);
        topIconBtn(ctx, "+", rx, mx, my, PDARK, BORL);
        topIconBtn(ctx, "-", rx + 36, mx, my, PDARK, BORL);
    }

    private int topBtn(DrawContext ctx, String lbl, int x, int mx, int my, int col, int bc) {
        int w = textRenderer.getWidth(lbl) + 14, h = 22, y = 5;
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        ctx.fill(x, y, x + w, y + h, hov ? lighter(col, 25) : col);
        ctx.drawBorder(x, y, w, h, hov ? bc : BOR);
        ctx.drawTextWithShadow(textRenderer, Text.literal(lbl), x + 7, y + 7, WHITE);
        return x + w;
    }

    private void topIconBtn(DrawContext ctx, String lbl, int x, int mx, int my, int col, int bc) {
        int w = 22, h = 22, y = 5;
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        ctx.fill(x, y, x + w, y + h, hov ? lighter(col, 25) : col);
        ctx.drawBorder(x, y, w, h, hov ? bc : BOR);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(lbl), x + 11, y + 7, WHITE);
    }

    // ─── LEFT PANEL ──────────────────────────────────

    private void renderLeftPanel(DrawContext ctx, int mx, int my) {
        ctx.fill(0, TH, LW, height - BH, PDARK);
        ctx.fill(LW - 1, TH, LW, height - BH, BOR);

        // Tools
        for (int i = 0; i < LEFT_TOOLS.length; i++) {
            int ty = TH + 8 + i * 24;
            boolean hov = mx >= 4 && mx <= LW - 4 && my >= ty && my <= ty + 20;
            boolean act = activeTool == i;
            ctx.fill(4, ty, LW - 4, ty + 20,
                act ? BLUED : (hov ? PLIGHT : PDARK));
            ctx.drawBorder(4, ty, LW - 8, 20, act ? BLUE : BOR);
            ctx.drawTextWithShadow(textRenderer, Text.literal(LEFT_TOOLS[i]), 9, ty + 7,
                act ? WHITE : LGRAY);
        }

        // Divider
        int dy = TH + 8 + LEFT_TOOLS.length * 24 + 6;
        ctx.fill(4, dy, LW - 4, dy + 1, BOR);

        // Stats
        int nodes = NodeGraph.getInstance().getNodes().size();
        long autoC = NodeGraph.getInstance().getNodes().stream().filter(n -> n.autoCraftOn).count();
        long active = NodeGraph.getInstance().getNodes().stream().filter(n -> n.autoCraftOn && n.isActive).count();
        ctx.drawTextWithShadow(textRenderer, Text.literal("Узлов: " + nodes), 6, dy + 6, GRAY);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Авто: " + autoC), 6, dy + 18, GRAY);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Активных: " + active), 6, dy + 30, active > 0 ? GREEN : GRAY);

        // Minimap
        int mmY = dy + 50;
        renderMinimap(ctx, 4, mmY, LW - 8, 80);
    }

    private void renderMinimap(DrawContext ctx, int mx2, int my2, int mw, int mh) {
        ctx.fill(mx2, my2, mx2 + mw, my2 + mh, 0xFF080A12);
        ctx.drawBorder(mx2, my2, mw, mh, BOR);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Миникарта"), mx2 + 2, my2 - 10, GRAY);

        for (RecipeNode n : NodeGraph.getInstance().getNodes()) {
            int nx = mx2 + 2 + (int)(n.x / 25.0 * mw / 20) + mw / 2;
            int ny = my2 + 2 + (int)(n.y / 25.0 * mh / 20) + mh / 2;
            if (nx >= mx2 && nx < mx2 + mw - 4 && ny >= my2 && ny < my2 + mh - 4) {
                ctx.fill(nx, ny, nx + 5, ny + 3, n.autoCraftOn ? GREEN : BORL);
            }
        }
    }

    // ─── BOTTOM BAR ──────────────────────────────────

    private void renderBottomBar(DrawContext ctx, int mx, int my) {
        int y = height - BH;
        ctx.fill(0, y, width, height, PDARK);
        ctx.fill(0, y, width, y + 1, BOR);

        int nodes = NodeGraph.getInstance().getNodes().size();
        int conns = NodeGraph.getInstance().getConnections().size();
        long autoC = NodeGraph.getInstance().getNodes().stream().filter(n -> n.autoCraftOn).count();
        long total = NodeGraph.getInstance().getNodes().stream().mapToLong(n -> n.craftCount).sum();

        ctx.drawTextWithShadow(textRenderer,
            Text.literal("  Узлов: " + nodes +
                "   Связей: " + conns +
                "   Авто-крафт: " + autoC +
                "   Создано: " + total +
                "   Режим: " + (activePanel == Panel.EDIT_NODE ? "Редактирование" : "Просмотр")),
            LW + 4, y + 7, GRAY);

        // Right buttons
        int bx = width - 180;
        smallBtn(ctx, "Настройки", bx, y + 3, 80, 16, mx, my, PDARK, BORL);
        smallBtn(ctx, "Авто-крафт", bx + 84, y + 3, 90, 16, mx, my,
            NodeGraph.getInstance().getNodes().stream().anyMatch(n -> n.autoCraftOn) ? GREEND : PDARK,
            NodeGraph.getInstance().getNodes().stream().anyMatch(n -> n.autoCraftOn) ? GREEN : BORL);
    }

    // ─── CANVAS ──────────────────────────────────────

    private void renderCanvas(DrawContext ctx, int mx, int my) {
        int cx = LW, cy = TH, cw = width - LW, ch = height - TH - BH;
        ctx.fill(cx, cy, cx + cw, cy + ch, CANVAS);
        ctx.enableScissor(cx, cy, cx + cw, cy + ch);

        // Grid
        int gs = Math.max(8, (int)(32 * zoom));
        int ox = ((canvasOffX % gs) + gs) % gs;
        int oy = ((canvasOffY % gs) + gs) % gs;
        for (int gx = ox; gx < cw; gx += gs) ctx.fill(cx + gx, cy, cx + gx + 1, cy + ch, 0xFF0F1122);
        for (int gy = oy; gy < ch; gy += gs) ctx.fill(cx, cy + gy, cx + cw, cy + gy + 1, 0xFF0F1122);

        // Connections
        for (String[] c : NodeGraph.getInstance().getConnections()) {
            RecipeNode a = NodeGraph.getInstance().findById(c[0]);
            RecipeNode b = NodeGraph.getInstance().findById(c[1]);
            if (a == null || b == null) continue;
            int col = a.autoCraftOn && a.isActive ? LINE : 0xAA444466;
            bezier(ctx, sx(a.x + a.w), sy(a.y + a.h / 2),
                        sx(b.x),       sy(b.y + b.h / 2), col);
        }
        if (connectFrom != null)
            bezier(ctx, sx(connectFrom.x + connectFrom.w), sy(connectFrom.y + connectFrom.h / 2),
                        mx, my, YELLOW);

        // Nodes
        for (RecipeNode n : NodeGraph.getInstance().getNodes())
            drawNode(ctx, n, mx, my);

        ctx.disableScissor();
    }

    private void drawNode(DrawContext ctx, RecipeNode n, int mx, int my) {
        int x = sx(n.x), y = sy(n.y);
        int w = (int)(n.w * zoom), h = (int)(n.h * zoom);
        int HH = (int)(20 * zoom);
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        boolean sel = selected.contains(n);

        // Shadow
        ctx.fill(x + 4, y + 5, x + w + 4, y + h + 5, 0x55000000);

        // Body
        ctx.fill(x, y, x + w, y + h, sel ? NODESEL : (hov ? NODEHOV : NODEBG));

        // Border
        int borderCol = sel ? BLUE : (n.autoCraftOn ? (n.isActive ? GREEN : RED) : BORL);
        ctx.drawBorder(x, y, w, h, borderCol);

        // Header
        int hdrCol = n.autoCraftOn ? (n.isActive ? HDRON : HDROFF) : 0xFF0E1020;
        ctx.fill(x, y, x + w, y + HH, hdrCol);
        ctx.fill(x, y + HH, x + w, y + HH + 1, BOR);

        // Title
        String title = fit(n.id, w - 50);
        ctx.drawTextWithShadow(textRenderer, Text.literal(title), x + 5, y + (HH - 7) / 2 + 1, WHITE);

        // Status badge
        String badge = n.autoCraftOn ? (n.isActive ? "АКТИВЕН" : "СТОП") : "ВЫКЛ";
        int badgeCol = n.autoCraftOn ? (n.isActive ? GREEN : RED) : GRAY;
        int badgeX = x + w - textRenderer.getWidth(badge) - 5;
        ctx.drawTextWithShadow(textRenderer, Text.literal(badge), badgeX, y + (HH - 7) / 2 + 1, badgeCol);

        if (zoom >= 0.5f) {
            int contentY = y + HH + 4;

            // Result item
            if (!n.result.isEmpty()) {
                ctx.drawItem(n.result, x + 4, contentY);
                String rn = fit(n.result.getItem().toString().replace("minecraft:", ""), w - 30);
                ctx.drawTextWithShadow(textRenderer, Text.literal("-> " + rn), x + 22, contentY + 5, LGRAY);
            } else {
                ctx.drawTextWithShadow(textRenderer, Text.literal("-> нет рецепта"), x + 4, contentY + 5, GRAY);
            }

            // Ingredient icons
            int shown = 0;
            for (int i = 0; i < 9 && shown < 6; i++) {
                ItemStack s = n.inputs[i];
                if (s != null && !s.isEmpty()) {
                    ctx.drawItem(s, x + 4 + shown * 18, contentY + 20);
                    shown++;
                }
            }

            // Craft count
            if (n.craftCount > 0)
                ctx.drawTextWithShadow(textRenderer,
                    Text.literal("OK " + n.craftCount), x + 4, y + h - 22, GREEN);

            // Missing info
            if (n.autoCraftOn && !n.isActive) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player != null) {
                    String miss = CraftExecutor.getMissingInfo(n, mc.player);
                    if (!miss.isEmpty())
                        ctx.drawTextWithShadow(textRenderer,
                            Text.literal(fit("X " + miss, w - 8)), x + 4, y + h - 12, RED);
                }
            }

            // Auto-craft button
            int btnY = y + h - 12, btnX = x + 4, btnW = w - 8;
            boolean btnHov = mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + 10;
            ctx.fill(btnX, btnY, btnX + btnW, btnY + 10,
                n.autoCraftOn ? (btnHov ? 0xFF005022 : GREEND) : (btnHov ? 0xFF1A1A30 : 0xFF0E0E20));
            ctx.drawBorder(btnX, btnY, btnW, 10, n.autoCraftOn ? GREEN : BOR);
            ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal(n.autoCraftOn ? "Авто-крафт: ВКЛ" : "Авто-крафт: ВЫКЛ"),
                btnX + btnW / 2, btnY + 2, WHITE);
        }

        // Ports
        ctx.fill(x - 5, y + h / 2 - 5, x + 5, y + h / 2 + 5, PORT);
        ctx.drawBorder(x - 5, y + h / 2 - 5, 10, 10, 0xFFFFFFAA);
        ctx.fill(x + w - 5, y + h / 2 - 5, x + w + 5, y + h / 2 + 5, PORT);
        ctx.drawBorder(x + w - 5, y + h / 2 - 5, 10, 10, 0xFFFFFFAA);

        // Resize handle
        ctx.fill(x + w - 7, y + h - 7, x + w, y + h, BORL);
    }

    // ─── EDIT PANEL ──────────────────────────────────

    private void renderEditPanel(DrawContext ctx, int mx, int my) {
        int pw = 420, ph = 340;
        int px = (width - pw) / 2, py = (height - ph) / 2;

        // Shadow
        ctx.fill(px + 4, py + 6, px + pw + 4, py + ph + 6, 0x66000000);
        // Body
        ctx.fill(px, py, px + pw, py + ph, PBG);
        ctx.drawBorder(px, py, pw, ph, BLUE);
        // Header
        ctx.fill(px, py, px + pw, py + 28, PDARK);
        ctx.fill(px, py + 28, px + pw, py + 29, BOR);

        // Header content
        if (!editingNode.result.isEmpty())
            ctx.drawItem(editingNode.result, px + 6, py + 6);
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("Узел: " + editingNode.id), px + 26, py + 10, WHITE);

        // Mode badge
        String mode = "Режим: " + (editingNode.autoCraftOn ? "АКТИВЕН" : "ВЫКЛ");
        int modeCol = editingNode.autoCraftOn ? GREEN : GRAY;
        ctx.drawTextWithShadow(textRenderer, Text.literal(mode),
            px + pw / 2, py + 10, modeCol);

        // Header buttons
        smallBtn(ctx, "edit", px + pw - 70, py + 6, 16, 16, mx, my, PDARK, BORL);
        smallBtn(ctx, "set", px + pw - 50, py + 6, 16, 16, mx, my, PDARK, BORL);
        smallBtn(ctx, "X", px + pw - 26, py + 6, 18, 16, mx, my, REDD, RED);

        // ── LEFT: Recipe ──
        ctx.drawTextWithShadow(textRenderer, Text.literal("Рецепт крафта"), px + 8, py + 36, GRAY);

        // 3x3 grid
        for (int i = 0; i < 9; i++) {
            int col = i % 3, row = i / 3;
            int sx = px + 8 + col * 48, sy = py + 48 + row * 48;
            boolean sel = editingSlot == i;
            ctx.fill(sx, sy, sx + 44, sy + 44, sel ? 0xFF0A200E : DGRAY);
            ctx.drawBorder(sx, sy, 44, 44, sel ? GREEN : BOR);
            ItemStack s = editingNode.inputs[i];
            if (s != null && !s.isEmpty()) {
                ctx.drawItem(s, sx + 4, sy + 4);
                ctx.drawTextWithShadow(textRenderer,
                    Text.literal("" + s.getCount()), sx + 30, sy + 32, YELLOW);
            }
        }

        // Arrow + result
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("->"), px + 165, py + 96, YELLOW);
        int rsx = px + 176, rsy = py + 80;
        boolean rSel = editingSlot == 9;
        ctx.fill(rsx, rsy, rsx + 44, rsy + 44, rSel ? 0xFF0A200E : DGRAY);
        ctx.drawBorder(rsx, rsy, 44, 44, rSel ? GREEN : BOR);
        if (!editingNode.result.isEmpty()) {
            ctx.drawItem(editingNode.result, rsx + 4, rsy + 4);
            ctx.drawTextWithShadow(textRenderer,
                Text.literal("" + editingNode.result.getCount()), rsx + 30, rsy + 32, YELLOW);
        } else {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("?"), rsx + 22, rsy + 18, GRAY);
        }

        // Inventory
        ctx.drawTextWithShadow(textRenderer, Text.literal("Инвентарь (входы):"), px + 8, py + 200, GRAY);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            int shown = 0;
            for (int i = 0; i < mc.player.getInventory().size() && shown < 9; i++) {
                ItemStack inv = mc.player.getInventory().getStack(i);
                if (!inv.isEmpty()) {
                    int ix = px + 8 + shown * 24, iy = py + 212;
                    ctx.fill(ix, iy, ix + 22, iy + 22, DGRAY);
                    ctx.drawBorder(ix, iy, 22, 22, BOR);
                    ctx.drawItem(inv, ix + 3, iy + 3);
                    shown++;
                }
            }
        }

        // Выход
        ctx.drawTextWithShadow(textRenderer, Text.literal("Выход"), px + 176, py + 130, GRAY);
        int outX = px + 176, outY = py + 142;
        ctx.fill(outX, outY, outX + 44, outY + 44, DGRAY);
        ctx.drawBorder(outX, outY, 44, 44, BOR);
        if (!editingNode.result.isEmpty()) {
            ctx.drawItem(new ItemStack(editingNode.result.getItem(), 4), outX + 4, outY + 4);
            ctx.drawTextWithShadow(textRenderer, Text.literal("4"), outX + 30, outY + 32, WHITE);
        }

        // ── DIVIDER ──
        ctx.fill(px + 238, py + 29, px + 239, py + ph, BOR);

        // ── RIGHT: Settings ──
        int rx = px + 246;
        ctx.drawTextWithShadow(textRenderer, Text.literal("Настройки узла"), rx, py + 36, GRAY);

        // Name
        ctx.drawTextWithShadow(textRenderer, Text.literal("Название:"), rx, py + 50, LGRAY);
        ctx.fill(rx, py + 62, rx + 162, py + 76, DGRAY);
        ctx.drawBorder(rx, py + 62, 162, 14, editingNameActive ? BLUE : BOR);
        ctx.drawTextWithShadow(textRenderer,
            Text.literal((editingNameActive ? editingName : editingNode.id) + (editingNameActive ? "|" : "")),
            rx + 4, py + 65, WHITE);

        // Mode dropdown
        ctx.drawTextWithShadow(textRenderer, Text.literal("Режим:"), rx, py + 82, LGRAY);
        ctx.fill(rx, py + 94, rx + 162, py + 108, DGRAY);
        ctx.drawBorder(rx, py + 94, 162, 14, BOR);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Активен  v"), rx + 4, py + 97, WHITE);

        // Priority
        ctx.drawTextWithShadow(textRenderer, Text.literal("Приоритет:"), rx, py + 114, LGRAY);
        ctx.fill(rx, py + 126, rx + 80, py + 140, DGRAY);
        ctx.drawBorder(rx, py + 126, 80, 14, BOR);
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal(String.valueOf(editingNode.priority)), rx + 40, py + 129, WHITE);
        smallBtn(ctx, "^", rx + 84, py + 126, 16, 14, mx, my, DGRAY, BORL);
        smallBtn(ctx, "v", rx + 103, py + 126, 16, 14, mx, my, DGRAY, BORL);

        // Auto-craft toggle
        ctx.drawTextWithShadow(textRenderer, Text.literal("Повторить:"), rx, py + 146, LGRAY);
        ctx.fill(rx, py + 158, rx + 162, py + 172, DGRAY);
        ctx.drawBorder(rx, py + 158, 162, 14, BOR);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Всегда  v"), rx + 4, py + 161, WHITE);

        // Ignore missing checkbox
        ctx.fill(rx, py + 178, rx + 10, py + 188, DGRAY);
        ctx.drawBorder(rx, py + 178, 10, 10, BOR);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Игнорировать нехватку ресурсов"), rx + 14, py + 179, LGRAY);

        // Color
        ctx.drawTextWithShadow(textRenderer, Text.literal("Цвет узла:"), rx, py + 196, LGRAY);
        ctx.fill(rx, py + 208, rx + 162, py + 222, GREEN);
        ctx.drawBorder(rx, py + 208, 162, 14, BOR);

        // Auto-craft toggle button
        boolean aOn = editingNode.autoCraftOn;
        ctx.fill(rx, py + 228, rx + 162, py + 244, aOn ? GREEND : REDD);
        ctx.drawBorder(rx, py + 228, 162, 16, aOn ? GREEN : RED);
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal(aOn ? "Авто-крафт: ВКЛ" : "Авто-крафт: ВЫКЛ"),
            rx + 81, py + 235, WHITE);

        // Stats
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("Создано: " + editingNode.craftCount), rx, py + 250, GRAY);

        // Input field
        ctx.fill(rx, py + 264, rx + 162, py + 278, DGRAY);
        ctx.drawBorder(rx, py + 264, 162, 14, editingSlot >= 0 ? GREEN : BOR);
        ctx.drawTextWithShadow(textRenderer,
            Text.literal(inputBuffer.isEmpty() ? "Введи предмет..." : inputBuffer + "|"),
            rx + 4, py + 267, inputBuffer.isEmpty() ? GRAY : WHITE);

        // Кол-во
        ctx.fill(rx, py + 282, rx + 50, py + 296, DGRAY);
        ctx.drawBorder(rx, py + 282, 50, 14, BOR);
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal(String.valueOf(editingCount)), rx + 25, py + 285, WHITE);
        smallBtn(ctx, "+", rx + 54, py + 282, 16, 14, mx, my, DGRAY, BORL);
        smallBtn(ctx, "-", rx + 73, py + 282, 16, 14, mx, my, DGRAY, BORL);

        // Bottom buttons
        smallBtn(ctx, "Сохранить", px + 8, py + ph - 22, 110, 16, mx, my, GREEND, GREEN);
        smallBtn(ctx, "Отмена", px + 122, py + ph - 22, 80, 16, mx, my, PDARK, BORL);
        smallBtn(ctx, "Применить", rx, py + ph - 44, 78, 16, mx, my, GREEND, GREEN);
        smallBtn(ctx, "Очистить", rx + 82, py + ph - 44, 78, 16, mx, my, REDD, RED);
        smallBtn(ctx, "Список ->", rx, py + ph - 24, 162, 16, mx, my, BLUED, BLUE);
    }

    // ─── ITEM PICKER ─────────────────────────────────

    private void renderItemPicker(DrawContext ctx, int mx, int my) {
        int pw = 500, ph = 380;
        int px = (width - pw) / 2, py = (height - ph) / 2;

        ctx.fill(px, py, px + pw, py + ph, PBG);
        ctx.drawBorder(px, py, pw, ph, BLUE);
        ctx.fill(px, py, px + pw, py + 28, PDARK);
        ctx.fill(px, py + 28, px + pw, py + 29, BOR);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Выбор предмета/блока"), px + 8, py + 10, WHITE);
        smallBtn(ctx, "X", px + pw - 24, py + 6, 18, 16, mx, my, REDD, RED);

        // Categories
        String[] cats = {"Все","Блоки","Предметы","Природа","Еда","Инструменты","Броня","Зелья","Разное"};
        int catX = px + 4;
        for (String cat : cats) {
            int cw2 = textRenderer.getWidth(cat) + 10;
            boolean sel = cat.equals(pickerCategory);
            boolean hov = mx >= catX && mx <= catX + cw2 && my >= py + 30 && my <= py + 44;
            ctx.fill(catX, py + 30, catX + cw2, py + 44,
                sel ? BLUED : (hov ? PLIGHT : PDARK));
            ctx.drawBorder(catX, py + 30, cw2, 14, sel ? BLUE : BOR);
            ctx.drawTextWithShadow(textRenderer, Text.literal(cat), catX + 5, py + 33,
                sel ? WHITE : LGRAY);
            catX += cw2 + 2;
        }

        // Search
        ctx.fill(px + 4, py + 48, px + pw - 160, py + 62, DGRAY);
        ctx.drawBorder(px + 4, py + 48, pw - 164, 14, BLUE);
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("Поиск: " + (searchQuery.isEmpty() ? "..." : searchQuery + "|")),
            px + 8, py + 51, searchQuery.isEmpty() ? GRAY : WHITE);
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("Найдено: " + filteredItems.size()), px + pw - 155, py + 51, GRAY);

        // Item grid
        int rows = (ph - 120) / PSLOT;
        int startIdx = pickerScroll * PCOLS;
        ctx.enableScissor(px + 4, py + 66, px + pw - 160, py + 66 + rows * PSLOT);
        hoveredItem = null;

        for (int i = 0; i < filteredItems.size() - startIdx; i++) {
            int col = i % PCOLS, row = i / PCOLS;
            if (row >= rows) break;
            Item item = filteredItems.get(startIdx + i);
            int ix = px + 4 + col * PSLOT, iy = py + 66 + row * PSLOT;
            boolean hov = mx >= ix && mx <= ix + PSLOT - 2 && my >= iy && my <= iy + PSLOT - 2;
            if (hov) hoveredItem = item;
            ctx.fill(ix, iy, ix + PSLOT - 2, iy + PSLOT - 2, hov ? 0xFF0A1E3A : DGRAY);
            ctx.drawBorder(ix, iy, PSLOT - 2, PSLOT - 2, hov ? BLUE : BOR);
            ctx.drawItem(new ItemStack(item), ix + 4, iy + 4);
            String nm = item.toString().replace("minecraft:", "");
            if (nm.length() > 6) nm = nm.substring(0, 6);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(nm),
                ix + PSLOT / 2 - 1, iy + PSLOT - 13, GRAY);
        }
        ctx.disableScissor();

        // Tooltip
        if (hoveredItem != null)
            ctx.drawTooltip(textRenderer,
                Text.literal(hoveredItem.toString().replace("minecraft:", "")), mx, my);

        // Scrollbar
        int totalRows = (int)Math.ceil(filteredItems.size() / (double)PCOLS);
        if (totalRows > rows) {
            int trackH = rows * PSLOT;
            int thumbH = Math.max(10, (int)((float)rows / totalRows * trackH));
            int thumbY = py + 66 + (int)((float)pickerScroll / Math.max(1, totalRows - rows) * (trackH - thumbH));
            ctx.fill(px + pw - 160 + 2, py + 66, px + pw - 160 + 6, py + 66 + trackH, DGRAY);
            ctx.fill(px + pw - 160 + 2, thumbY, px + pw - 160 + 6, thumbY + thumbH, BORL);
        }

        // Right info panel
        int infoX = px + pw - 156;
        ctx.fill(infoX, py + 30, infoX + 152, py + ph - 4, DGRAY);
        ctx.drawBorder(infoX, py + 30, 152, ph - 34, BOR);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Информация"), infoX + 4, py + 34, GRAY);

        if (hoveredItem != null) {
            ctx.fill(infoX + 50, py + 46, infoX + 100, py + 96, PBG);
            ctx.drawBorder(infoX + 50, py + 46, 50, 50, BOR);
            ctx.drawItem(new ItemStack(hoveredItem), infoX + 54, py + 50);
            String fullName = hoveredItem.toString().replace("minecraft:", "");
            ctx.drawTextWithShadow(textRenderer, Text.literal(fullName), infoX + 4, py + 100, WHITE);
            ctx.drawTextWithShadow(textRenderer, Text.literal("minecraft:" + fullName), infoX + 4, py + 112, GRAY);
        }

        smallBtn(ctx, "Выбрать", infoX + 4, py + ph - 30, 144, 18, mx, my, GREEND, GREEN);
        smallBtn(ctx, "Отмена", infoX + 4, py + ph - 50, 144, 16, mx, my, REDD, RED);

        // Bottom hint
        ctx.fill(px, py + ph - 22, px + pw, py + ph, PDARK);
        ctx.fill(px, py + ph - 22, px + pw, py + ph - 21, BOR);
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("ЛКМ — выбрать   Скролл — листать   ESC — назад"),
            px + 8, py + ph - 14, GRAY);
    }

    // ─── SETTINGS ────────────────────────────────────

    private void renderSettings(DrawContext ctx, int mx, int my) {
        int pw = 380, ph = 310;
        int px = (width - pw) / 2, py = (height - ph) / 2;

        ctx.fill(px, py, px + pw, py + ph, PBG);
        ctx.drawBorder(px, py, pw, ph, BLUE);
        ctx.fill(px, py, px + pw, py + 28, PDARK);
        ctx.fill(px, py + 28, px + pw, py + 29, BOR);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Настройки редактора"), px + 8, py + 10, WHITE);
        smallBtn(ctx, "X", px + pw - 24, py + 6, 18, 16, mx, my, REDD, RED);

        // Tabs
        String[] tabs = {"Основные", "Отображение", "Управление", "Прочее"};
        int tx = px + 4;
        for (int i = 0; i < tabs.length; i++) {
            int tw = textRenderer.getWidth(tabs[i]) + 10;
            boolean act = settingsTab == i;
            boolean hov = mx >= tx && mx <= tx + tw && my >= py + 30 && my <= py + 44;
            ctx.fill(tx, py + 30, tx + tw, py + 44, act ? BLUED : (hov ? PLIGHT : PDARK));
            ctx.drawBorder(tx, py + 30, tw, 14, act ? BLUE : BOR);
            ctx.drawTextWithShadow(textRenderer, Text.literal(tabs[i]), tx + 5, py + 33,
                act ? WHITE : LGRAY);
            tx += tw + 2;
        }

        // Content
        int cy2 = py + 52;
        ctx.drawTextWithShadow(textRenderer, Text.literal("Сетка"), px + 8, cy2, LGRAY);
        checkBox(ctx, px + 8, cy2 + 14, "Показывать сетку", true, mx, my);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Размер сетки:  32"), px + 8, cy2 + 28, LGRAY);

        ctx.drawTextWithShadow(textRenderer, Text.literal("Привязка"), px + 8, cy2 + 44, LGRAY);
        checkBox(ctx, px + 8, cy2 + 58, "Привязка к сетке", true, mx, my);
        checkBox(ctx, px + 8, cy2 + 72, "Привязка к узлам", true, mx, my);

        ctx.drawTextWithShadow(textRenderer, Text.literal("Соединения"), px + 8, cy2 + 88, LGRAY);
        checkBox(ctx, px + 8, cy2 + 102, "Показывать соединения", true, mx, my);
        checkBox(ctx, px + 8, cy2 + 116, "Анимация потоков", true, mx, my);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Толщина линий:  2"), px + 8, cy2 + 130, LGRAY);

        // Right column
        int rx2 = px + pw / 2;
        ctx.drawTextWithShadow(textRenderer, Text.literal("Инструменты"), rx2, cy2, LGRAY);
        smallBtn(ctx, "Выделить все", rx2, cy2 + 14, 130, 14, mx, my, PDARK, BORL);
        smallBtn(ctx, "Очистить выделение", rx2, cy2 + 32, 130, 14, mx, my, PDARK, BORL);
        smallBtn(ctx, "Удалить выделенное", rx2, cy2 + 50, 130, 14, mx, my, REDD, RED);
        smallBtn(ctx, "Копировать", rx2, cy2 + 68, 130, 14, mx, my, PDARK, BORL);
        smallBtn(ctx, "Вставить", rx2, cy2 + 86, 130, 14, mx, my, PDARK, BORL);
        smallBtn(ctx, "Дублировать", rx2, cy2 + 104, 130, 14, mx, my, PDARK, BORL);

        // Bottom
        smallBtn(ctx, "Сбросить настройки", px + 8, py + ph - 22, 140, 16, mx, my, PDARK, BORL);
        smallBtn(ctx, "Закрыть", px + pw - 88, py + ph - 22, 80, 16, mx, my, REDD, RED);
    }

    // ─── AUTO PANEL ──────────────────────────────────

    private void renderAutoPanel(DrawContext ctx, int mx, int my) {
        int pw = 380, ph = 300;
        int px = (width - pw) / 2, py = (height - ph) / 2;

        ctx.fill(px, py, px + pw, py + ph, PBG);
        ctx.drawBorder(px, py, pw, ph, GREEN);
        ctx.fill(px, py, px + pw, py + 28, PDARK);
        ctx.fill(px, py + 28, px + pw, py + 29, BOR);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Авто-крафтинг — управление"), px + 8, py + 10, WHITE);
        smallBtn(ctx, "X", px + pw - 24, py + 6, 18, 16, mx, my, REDD, RED);

        // Big play button
        boolean anyActive = NodeGraph.getInstance().getNodes().stream().anyMatch(n -> n.autoCraftOn);
        int btnX = px + 8, btnY = py + 36, btnW = 90, btnH = 70;
        ctx.fill(btnX, btnY, btnX + btnW, btnY + btnH, anyActive ? GREEND : DGRAY);
        ctx.drawBorder(btnX, btnY, btnW, btnH, anyActive ? GREEN : BOR);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(">"), btnX + btnW / 2, btnY + 12, WHITE);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Авто-"), btnX + btnW / 2, btnY + 34, WHITE);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("крафтинг"), btnX + btnW / 2, btnY + 46, WHITE);
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal(anyActive ? "ВКЛЮЧЁН" : "ВЫКЛЮЧЕН"),
            btnX + btnW / 2, btnY + 58, anyActive ? GREEN : GRAY);

        // Stats right
        int sx2 = px + 108;
        ctx.drawTextWithShadow(textRenderer, Text.literal("Режим работы:"), sx2, py + 36, GRAY);
        ctx.fill(sx2, py + 48, sx2 + 200, py + 62, DGRAY);
        ctx.drawBorder(sx2, py + 48, 200, 14, BOR);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Авто-крафт в указанной ноде  v"), sx2 + 4, py + 51, WHITE);

        long total = NodeGraph.getInstance().getNodes().stream().mapToLong(n -> n.craftCount).sum();
        long noMat = NodeGraph.getInstance().getNodes().stream().filter(n -> n.autoCraftOn && !n.isActive).count();

        ctx.drawTextWithShadow(textRenderer, Text.literal("Статус:"), sx2 + 140, py + 36, GRAY);
        ctx.drawTextWithShadow(textRenderer,
            Text.literal(anyActive ? "Крафтинг активен" : "Остановлен"),
            sx2 + 140, py + 48, anyActive ? GREEN : GRAY);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Успешно: " + total), sx2, py + 70, GREEN);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Не хватает ресурсов: " + noMat), sx2, py + 82, noMat > 0 ? RED : GRAY);

        ctx.drawTextWithShadow(textRenderer, Text.literal("Проверка каждые"), sx2, py + 98, GRAY);
        ctx.fill(sx2, py + 110, sx2 + 50, py + 124, DGRAY);
        ctx.drawBorder(sx2, py + 110, 50, 14, BOR);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("1.0"), sx2 + 25, py + 113, WHITE);
        ctx.drawTextWithShadow(textRenderer, Text.literal("секунд"), sx2 + 55, py + 113, GRAY);

        // Node list
        ctx.fill(px + 4, py + 130, px + pw - 4, py + 131, BOR);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Узлы:"), px + 8, py + 136, GRAY);

        int rowY = py + 150;
        MinecraftClient mc = MinecraftClient.getInstance();
        for (RecipeNode n : NodeGraph.getInstance().getNodes()) {
            if (rowY > py + ph - 40) break;
            String st = n.autoCraftOn ? (n.isActive ? "OK" : "X") : "-";
            int stCol = n.autoCraftOn ? (n.isActive ? GREEN : RED) : GRAY;
            ctx.drawTextWithShadow(textRenderer,
                Text.literal(st + " " + n.id + " x" + n.craftCount), px + 8, rowY, stCol);
            if (n.autoCraftOn && !n.isActive && mc.player != null) {
                String miss = CraftExecutor.getMissingInfo(n, mc.player);
                if (!miss.isEmpty()) {
                    ctx.drawTextWithShadow(textRenderer,
                        Text.literal("  -> " + miss), px + 8, rowY + 10, RED);
                    rowY += 10;
                }
            }
            rowY += 14;
        }

        smallBtn(ctx, "Остановить всё", px + 8, py + ph - 22, 140, 16, mx, my, REDD, RED);
    }

    // ─── ADD NODE PANEL ──────────────────────────────

    private void renderAddNode(DrawContext ctx, int mx, int my) {
        int pw = 420, ph = 320;
        int px = (width - pw) / 2, py = (height - ph) / 2;

        ctx.fill(px, py, px + pw, py + ph, PBG);
        ctx.drawBorder(px, py, pw, ph, GREEN);
        ctx.fill(px, py, px + pw, py + 28, PDARK);
        ctx.fill(px, py + 28, px + pw, py + 29, BOR);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Добавление ноды"), px + 8, py + 10, WHITE);
        smallBtn(ctx, "X", px + pw - 24, py + 6, 18, 16, mx, my, REDD, RED);

        // Type list
        ctx.drawTextWithShadow(textRenderer, Text.literal("Тип узла"), px + 8, py + 36, GRAY);
        String[] types = {"Рецепт крафта", "Плавка", "Разложение", "Сбор", "Пользовательская"};
        int[] typeIcons = {0, 1, 2, 3, 4};
        for (int i = 0; i < types.length; i++) {
            int ty = py + 50 + i * 26;
            boolean hov = mx >= px + 4 && mx <= px + 120 && my >= ty && my <= ty + 22;
            ctx.fill(px + 4, ty, px + 120, ty + 22, hov ? BLUED : DGRAY);
            ctx.drawBorder(px + 4, ty, 116, 22, hov ? BLUE : BOR);
            ctx.drawTextWithShadow(textRenderer, Text.literal(types[i]), px + 10, ty + 8, WHITE);
        }

        // Recipe list
        ctx.drawTextWithShadow(textRenderer, Text.literal("Доступные рецепты"), px + 128, py + 36, GRAY);
        ctx.fill(px + 128, py + 50, px + 270, py + 64, DGRAY);
        ctx.drawBorder(px + 128, py + 50, 142, 14, BOR);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Поиск рецепта..."), px + 132, py + 53, GRAY);

        String[] recipes = {"Верстак","Палка","Факел","Печь","Сундук","Каменный меч"};
        for (int i = 0; i < recipes.length; i++) {
            int ry = py + 68 + i * 24;
            boolean hov = mx >= px + 128 && mx <= px + 270 && my >= ry && my <= ry + 20;
            ctx.fill(px + 128, ry, px + 270, ry + 20, hov ? PLIGHT : DGRAY);
            ctx.drawBorder(px + 128, ry, 142, 20, hov ? BORL : BOR);
            ctx.drawTextWithShadow(textRenderer, Text.literal(recipes[i]), px + 132, ry + 7, WHITE);
        }

        // Preview
        ctx.drawTextWithShadow(textRenderer, Text.literal("Предпросмотр"), px + 280, py + 36, GRAY);
        ctx.fill(px + 280, py + 50, px + pw - 8, py + ph - 60, DGRAY);
        ctx.drawBorder(px + 280, py + 50, pw - 288 - 8, ph - 110, BOR);

        // Requirements
        ctx.drawTextWithShadow(textRenderer, Text.literal("Требования:"), px + 128, py + 230, GRAY);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Дубовые доски  4/4"), px + 128, py + 244, GREEN);

        smallBtn(ctx, "Добавить ноду", px + 128, py + ph - 26, 142, 18, mx, my, GREEND, GREEN);
        smallBtn(ctx, "Отмена", px + pw - 88, py + ph - 26, 80, 16, mx, my, REDD, RED);
    }

    // ─── HELPERS ─────────────────────────────────────

    private void checkBox(DrawContext ctx, int x, int y, String label, boolean checked, int mx, int my) {
        ctx.fill(x, y, x + 10, y + 10, checked ? GREEND : DGRAY);
        ctx.drawBorder(x, y, 10, 10, checked ? GREEN : BOR);
        if (checked) ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("v"), x + 5, y + 1, GREEN);
        ctx.drawTextWithShadow(textRenderer, Text.literal(label), x + 14, y + 1, LGRAY);
    }

    private void smallBtn(DrawContext ctx, String lbl, int x, int y, int w, int h,
                           int mx, int my, int col, int bc) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        ctx.fill(x, y, x + w, y + h, hov ? lighter(col, 25) : col);
        ctx.drawBorder(x, y, w, h, bc);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(lbl), x + w / 2, y + (h - 7) / 2, WHITE);
    }

    private int sx(int nx) { return LW + canvasOffX + (int)(nx * zoom); }
    private int sy(int ny) { return TH + canvasOffY + (int)(ny * zoom); }

    private String fit(String s, int maxPx) {
        if (textRenderer.getWidth(s) <= maxPx) return s;
        while (s.length() > 1 && textRenderer.getWidth(s + ".") > maxPx)
            s = s.substring(0, s.length() - 1);
        return s + ".";
    }

    private void bezier(DrawContext ctx, int x1, int y1, int x2, int y2, int col) {
        int steps = Math.max(20, Math.abs(x2 - x1) / 2);
        int cx1 = x1 + (x2 - x1) / 3, cx2 = x2 - (x2 - x1) / 3;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float)steps, u = 1 - t;
            int px = (int)(u*u*u*x1 + 3*u*u*t*cx1 + 3*u*t*t*cx2 + t*t*t*x2);
            int py = (int)(u*u*u*y1 + 3*u*u*t*y1 + 3*u*t*t*y2 + t*t*t*y2);
            ctx.fill(px, py, px + 2, py + 2, col);
        }
    }

    private int lighter(int c, int a) {
        return (c & 0xFF000000) |
            (Math.min(255, ((c >> 16) & 0xFF) + a) << 16) |
            (Math.min(255, ((c >> 8) & 0xFF) + a) << 8) |
            Math.min(255, (c & 0xFF) + a);
    }

    // ═══════════════════════════════════════════════════
    //  MOUSE / KEY
    // ═══════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mx0, double my0, int btn) {
        int mx = (int)mx0, my = (int)my0;

        if (activePanel == Panel.ITEM_PICKER) return pickerClick(mx, my, btn);
        if (activePanel == Panel.EDIT_NODE)   return editClick(mx, my, btn);
        if (activePanel == Panel.SETTINGS)    return settingsClick(mx, my, btn);
        if (activePanel == Panel.AUTO_PANEL)  return autoClick(mx, my, btn);
        if (activePanel == Panel.ADD_NODE)    return addNodeClick(mx, my, btn);

        if (my < TH)          return topClick(mx, my, btn);
        if (mx < LW)          return leftClick(mx, my, btn);
        if (my >= height - BH) return bottomClick(mx, my, btn);

        return canvasClick(mx, my, btn);
    }

    private boolean topClick(int mx, int my, int btn) {
        int x = LW + 8;
        int w = textRenderer.getWidth("+ Добавить ноду") + 14;
        if (mx >= x && mx <= x + w && my >= 5 && my <= 27) {
            activePanel = Panel.ADD_NODE; return true;
        }
        return true;
    }

    private boolean leftClick(int mx, int my, int btn) {
        for (int i = 0; i < LEFT_TOOLS.length; i++) {
            int ty = TH + 8 + i * 24;
            if (mx >= 4 && mx <= LW - 4 && my >= ty && my <= ty + 20) {
                activeTool = i; return true;
            }
        }
        return true;
    }

    private boolean bottomClick(int mx, int my, int btn) {
        int bx = width - 180;
        if (mx >= bx && mx <= bx + 80) { activePanel = Panel.SETTINGS; return true; }
        if (mx >= bx + 84 && mx <= bx + 174) { activePanel = Panel.AUTO_PANEL; return true; }
        return true;
    }

    private boolean canvasClick(int mx, int my, int btn) {
        List<RecipeNode> nodes = NodeGraph.getInstance().getNodes();
        for (int i = nodes.size() - 1; i >= 0; i--) {
            RecipeNode n = nodes.get(i);
            int x = sx(n.x), y = sy(n.y);
            int w = (int)(n.w * zoom), h = (int)(n.h * zoom);
            int HH = (int)(20 * zoom);
            if (mx < x - 6 || mx > x + w + 6 || my < y || my > y + h) continue;

            if (btn == 1) { NodeGraph.getInstance().removeNode(n.id); selected.remove(n); return true; }

            // Port out
            if (mx >= x + w - 6 && mx <= x + w + 6 && my >= y + h/2 - 6 && my <= y + h/2 + 6) {
                connectFrom = n; return true;
            }
            // Port in
            if (mx >= x - 6 && mx <= x + 6 && my >= y + h/2 - 6 && my <= y + h/2 + 6) {
                if (connectFrom != null && !connectFrom.id.equals(n.id)) {
                    NodeGraph.getInstance().addConnection(connectFrom.id, n.id);
                    connectFrom = null; return true;
                }
            }
            // Auto-craft button
            int by = y + h - 12;
            if (mx >= x + 4 && mx <= x + w - 4 && my >= by && my <= by + 10) {
                n.autoCraftOn = !n.autoCraftOn; return true;
            }
            // Resize
            if (mx >= x + w - 8 && my >= y + h - 8) {
                resizeNode = n; resizeStartW = n.w; resizeStartH = n.h;
                resizeMouseX = mx; resizeMouseY = my; return true;
            }
            // Header drag
            if (my >= y && my <= y + HH) {
                dragNode = n; dragOffX = mx - x; dragOffY = my - y;
                if (!selected.contains(n)) { selected.clear(); selected.add(n); }
                return true;
            }
            // Body - open editor
            if (my > y + HH && my < by) {
                editingNode = n; editingSlot = -1; inputBuffer = ""; editingCount = 1;
                editingName = n.id; editingNameActive = false;
                activePanel = Panel.EDIT_NODE; return true;
            }
        }

        connectFrom = null;
        selected.clear();
        draggingCanvas = true;
        canvasDragStartX = mx - canvasOffX;
        canvasDragStartY = my - canvasOffY;
        return true;
    }

    private boolean editClick(int mx, int my, int btn) {
        int pw = 420, ph = 340;
        int px = (width - pw) / 2, py = (height - ph) / 2;
        int rx = px + 246;

        // Close
        if (mx >= px + pw - 24 && mx <= px + pw - 6 && my >= py + 6 && my <= py + 22) {
            activePanel = Panel.NONE; return true;
        }
        // Save / Cancel
        if (mx >= px + 8 && mx <= px + 118 && my >= py + ph - 22 && my <= py + ph - 6) {
            activePanel = Panel.NONE; return true;
        }
        if (mx >= px + 122 && mx <= px + 202 && my >= py + ph - 22 && my <= py + ph - 6) {
            activePanel = Panel.NONE; return true;
        }
        // Slots 3x3
        for (int i = 0; i < 9; i++) {
            int col = i % 3, row = i / 3;
            int slx = px + 8 + col * 48, sly = py + 48 + row * 48;
            if (mx >= slx && mx <= slx + 44 && my >= sly && my <= sly + 44) {
                editingSlot = i; inputBuffer = ""; return true;
            }
        }
        // Result slot
        if (mx >= px + 176 && mx <= px + 220 && my >= py + 80 && my <= py + 124) {
            editingSlot = 9; inputBuffer = ""; return true;
        }
        // Auto-craft toggle
        if (mx >= rx && mx <= rx + 162 && my >= py + 228 && my <= py + 244) {
            editingNode.autoCraftOn = !editingNode.autoCraftOn; return true;
        }
        // Count +/-
        if (mx >= rx + 54 && mx <= rx + 70 && my >= py + 282 && my <= py + 296) {
            editingCount = Math.min(64, editingCount + 1); return true;
        }
        if (mx >= rx + 73 && mx <= rx + 89 && my >= py + 282 && my <= py + 296) {
            editingCount = Math.max(1, editingCount - 1); return true;
        }
        // Priority
        if (mx >= rx + 84 && mx <= rx + 100 && my >= py + 126 && my <= py + 140) {
            editingNode.priority++; return true;
        }
        if (mx >= rx + 103 && mx <= rx + 119 && my >= py + 126 && my <= py + 140) {
            editingNode.priority--; return true;
        }
        // Apply
        if (mx >= rx && mx <= rx + 78 && my >= py + ph - 44 && my <= py + ph - 28) {
            applyInput(); return true;
        }
        // Clear
        if (mx >= rx + 82 && mx <= rx + 160 && my >= py + ph - 44 && my <= py + ph - 28) {
            clearSlot(); return true;
        }
        // Picker
        if (mx >= rx && mx <= rx + 162 && my >= py + ph - 24 && my <= py + ph - 8) {
            activePanel = Panel.ITEM_PICKER; rebuildFilter(); return true;
        }
        // Name field
        if (mx >= rx && mx <= rx + 162 && my >= py + 62 && my <= py + 76) {
            editingNameActive = true; editingName = editingNode.id; return true;
        }
        return true;
    }

    private boolean pickerClick(int mx, int my, int btn) {
        int pw = 500, ph = 380;
        int px = (width - pw) / 2, py = (height - ph) / 2;
        int infoX = px + pw - 156;

        if (mx >= px + pw - 24 && mx <= px + pw - 6 && my >= py + 6 && my <= py + 22) {
            activePanel = Panel.EDIT_NODE; return true;
        }
        // Category
        String[] cats = {"Все","Блоки","Предметы","Природа","Еда","Инструменты","Броня","Зелья","Разное"};
        int catX = px + 4;
        for (String cat : cats) {
            int cw2 = textRenderer.getWidth(cat) + 10;
            if (mx >= catX && mx <= catX + cw2 && my >= py + 30 && my <= py + 44) {
                pickerCategory = cat; pickerScroll = 0; rebuildFilter(); return true;
            }
            catX += cw2 + 2;
        }
        // Items
        int rows = (ph - 120) / PSLOT;
        int startIdx = pickerScroll * PCOLS;
        for (int i = 0; i < filteredItems.size() - startIdx; i++) {
            int col = i % PCOLS, row = i / PCOLS;
            if (row >= rows) break;
            int ix = px + 4 + col * PSLOT, iy = py + 66 + row * PSLOT;
            if (mx >= ix && mx <= ix + PSLOT - 2 && my >= iy && my <= iy + PSLOT - 2) {
                applyItem(filteredItems.get(startIdx + i));
                activePanel = Panel.EDIT_NODE; return true;
            }
        }
        // Select button
        if (hoveredItem != null && mx >= infoX + 4 && mx <= infoX + 148 && my >= py + ph - 30 && my <= py + ph - 12) {
            applyItem(hoveredItem);
            activePanel = Panel.EDIT_NODE; return true;
        }
        // Cancel
        if (mx >= infoX + 4 && mx <= infoX + 148 && my >= py + ph - 50 && my <= py + ph - 34) {
            activePanel = Panel.EDIT_NODE; return true;
        }
        return true;
    }

    private boolean settingsClick(int mx, int my, int btn) {
        int pw = 380, ph = 310;
        int px = (width - pw) / 2, py = (height - ph) / 2;
        if (mx >= px + pw - 24 && mx <= px + pw - 6 && my >= py + 6 && my <= py + 22) {
            activePanel = Panel.NONE; return true;
        }
        if (mx >= px + pw - 88 && mx <= px + pw - 8 && my >= py + ph - 22 && my <= py + ph - 6) {
            activePanel = Panel.NONE; return true;
        }
        // Tabs
        String[] tabs = {"Основные", "Отображение", "Управление", "Прочее"};
        int tx = px + 4;
        for (int i = 0; i < tabs.length; i++) {
            int tw = textRenderer.getWidth(tabs[i]) + 10;
            if (mx >= tx && mx <= tx + tw && my >= py + 30 && my <= py + 44) {
                settingsTab = i; return true;
            }
            tx += tw + 2;
        }
        return true;
    }

    private boolean autoClick(int mx, int my, int btn) {
        int pw = 380, ph = 300;
        int px = (width - pw) / 2, py = (height - ph) / 2;
        if (mx >= px + pw - 24 && mx <= px + pw - 6 && my >= py + 6 && my <= py + 22) {
            activePanel = Panel.NONE; return true;
        }
        // Stop all
        if (mx >= px + 8 && mx <= px + 148 && my >= py + ph - 22 && my <= py + ph - 6) {
            NodeGraph.getInstance().getNodes().forEach(n -> n.autoCraftOn = false);
            return true;
        }
        // Big button
        if (mx >= px + 8 && mx <= px + 98 && my >= py + 36 && my <= py + 106) {
            boolean any = NodeGraph.getInstance().getNodes().stream().anyMatch(n -> n.autoCraftOn);
            NodeGraph.getInstance().getNodes().forEach(n -> n.autoCraftOn = !any);
            return true;
        }
        return true;
    }

    private boolean addNodeClick(int mx, int my, int btn) {
        int pw = 420, ph = 320;
        int px = (width - pw) / 2, py = (height - ph) / 2;
        if (mx >= px + pw - 24 && mx <= px + pw - 6 && my >= py + 6 && my <= py + 22) {
            activePanel = Panel.NONE; return true;
        }
        if (mx >= px + 128 && mx <= px + 270 && my >= py + ph - 26 && my <= py + ph - 8) {
            addNodeDirect(); activePanel = Panel.NONE; return true;
        }
        if (mx >= px + pw - 88 && mx <= px + pw - 8 && my >= py + ph - 26 && my <= py + ph - 10) {
            activePanel = Panel.NONE; return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx0, double my0, int btn, double dx, double dy) {
        int mx = (int)mx0, my = (int)my0;
        if (dragNode != null) {
            dragNode.x = (int)((mx - LW - canvasOffX - dragOffX) / zoom);
            dragNode.y = (int)((my - TH - canvasOffY - dragOffY) / zoom);
            return true;
        }
        if (resizeNode != null) {
            resizeNode.w = Math.max(160, resizeStartW + (int)((mx - resizeMouseX) / zoom));
            resizeNode.h = Math.max(90,  resizeStartH + (int)((my - resizeMouseY) / zoom));
            return true;
        }
        if (draggingCanvas) {
            canvasOffX = mx - canvasDragStartX;
            canvasOffY = my - canvasDragStartY;
            return true;
        }
        return super.mouseDragged(mx0, my0, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        dragNode = null; resizeNode = null; draggingCanvas = false;
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (activePanel == Panel.ITEM_PICKER) {
            int pw = 500, ph = 380;
            int px = (width - pw) / 2, py = (height - ph) / 2;
            if (mx >= px && mx <= px + pw - 156 && my >= py && my <= py + ph) {
                int rows = (ph - 120) / PSLOT;
                int maxS = (int)Math.ceil(filteredItems.size() / (double)PCOLS) - rows;
                pickerScroll = Math.max(0, Math.min(maxS, pickerScroll - (int)v));
                return true;
            }
        }
        if (activePanel == Panel.NONE && mx > LW && my > TH && my < height - BH) {
            zoom = Math.max(0.3f, Math.min(2.5f, zoom + (float)v * 0.1f));
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (activePanel != Panel.NONE) {
            if (key == 259) { // Backspace
                if (editingNameActive && !editingName.isEmpty()) {
                    editingName = editingName.substring(0, editingName.length() - 1);
                } else if (activePanel == Panel.ITEM_PICKER && !searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                    rebuildFilter();
                } else if (!inputBuffer.isEmpty()) {
                    inputBuffer = inputBuffer.substring(0, inputBuffer.length() - 1);
                }
                return true;
            }
            if (key == 257 || key == 335) { // Enter
                if (editingNameActive) {
                    editingNode.id = editingName;
                    editingNameActive = false;
                } else {
                    applyInput();
                }
                return true;
            }
            if (key == 256) { // ESC
                if (editingNameActive) { editingNameActive = false; return true; }
                if (activePanel == Panel.ITEM_PICKER) { activePanel = Panel.EDIT_NODE; return true; }
                activePanel = Panel.NONE; return true;
            }
            return true;
        }
        if (key == 256) { this.close(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (editingNameActive) { editingName += c; return true; }
        if (activePanel == Panel.ITEM_PICKER) { searchQuery += c; rebuildFilter(); return true; }
        if (activePanel == Panel.EDIT_NODE && editingSlot >= 0) { inputBuffer += c; return true; }
        return false;
    }

    // ═══════════════════════════════════════════════════
    //  LOGIC
    // ═══════════════════════════════════════════════════

    private void applyInput() {
        if (editingNode == null || editingSlot < 0 || inputBuffer.isEmpty()) return;
        String raw = inputBuffer.trim().toLowerCase();
        String id = null;
        for (Map.Entry<String, String> e : RU.entrySet())
            if (raw.contains(e.getKey())) { id = "minecraft:" + e.getValue(); break; }
        if (id == null) id = raw.contains(":") ? raw : "minecraft:" + raw;
        try {
            Item item = Registries.ITEM.get(Identifier.of(id));
            if (item == Items.AIR)
                for (Item it : allItems) if (it.toString().contains(raw)) { item = it; break; }
            if (item == Items.AIR) return;
            applyItem(item);
            inputBuffer = ""; editingSlot = -1;
        } catch (Exception ignored) {}
    }

    private void applyItem(Item item) {
        if (editingNode == null || editingSlot < 0) return;
        ItemStack s = new ItemStack(item, editingCount);
        if (editingSlot == 9) editingNode.result = s;
        else editingNode.inputs[editingSlot] = s;
    }

    private void clearSlot() {
        if (editingNode == null || editingSlot < 0) return;
        if (editingSlot == 9) editingNode.result = ItemStack.EMPTY;
        else editingNode.inputs[editingSlot] = ItemStack.EMPTY;
        inputBuffer = "";
    }

    private void addNodeDirect() {
        int c = NodeGraph.getInstance().getNodes().size();
        RecipeNode n = new RecipeNode("Рецепт " + (c + 1));
        n.x = 60 + (c * 200) % 600;
        n.y = 60 + (c / 4) * 140;
        n.w = 180; n.h = 110;
        NodeGraph.getInstance().addNode(n);
    }

    private void rebuildFilter() {
        filteredItems.clear();
        String q = searchQuery.toLowerCase();
        String eng = null;
        if (!q.isEmpty())
            for (Map.Entry<String, String> e : RU.entrySet())
                if (q.contains(e.getKey())) { eng = e.getValue(); break; }
        final String fq = eng != null ? eng : q;
        for (Item it : allItems)
            if (fq.isEmpty() || it.toString().contains(fq)) filteredItems.add(it);
        pickerScroll = 0;
    }

    @Override public boolean shouldPause() { return false; }
    @Override public boolean shouldCloseOnEsc() { return true; }
}
