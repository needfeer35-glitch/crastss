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

    private enum Panel { NONE, EDIT_NODE, ITEM_PICKER }
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

    // Редактор
    private RecipeNode editingNode = null;
    private int editingSlot = -1;
    private String inputBuffer = "";

    // Пикер предметов
    private String searchQuery = "";
    private List<Item> allItems = new ArrayList<>();
    private List<Item> filteredItems = new ArrayList<>();
    private int itemPickerScroll = 0;
    private Item hoveredItem = null;
    private static final int COLS = 8;
    private static final int SLOT = 36;

    // Русские → английские названия предметов (частичный словарь)
    private static final Map<String, String> RU_MAP = new HashMap<>();
    static {
        RU_MAP.put("дерево", "log"); RU_MAP.put("доски", "planks");
        RU_MAP.put("камень", "stone"); RU_MAP.put("земля", "dirt");
        RU_MAP.put("песок", "sand"); RU_MAP.put("гравий", "gravel");
        RU_MAP.put("железо", "iron"); RU_MAP.put("золото", "gold");
        RU_MAP.put("алмаз", "diamond"); RU_MAP.put("уголь", "coal");
        RU_MAP.put("факел", "torch"); RU_MAP.put("палка", "stick");
        RU_MAP.put("верстак", "crafting_table"); RU_MAP.put("печь", "furnace");
        RU_MAP.put("сундук", "chest"); RU_MAP.put("лестница", "ladder");
        RU_MAP.put("стекло", "glass"); RU_MAP.put("шерсть", "wool");
        RU_MAP.put("кирпич", "brick"); RU_MAP.put("книга", "book");
        RU_MAP.put("лук", "bow"); RU_MAP.put("стрела", "arrow");
        RU_MAP.put("меч", "sword"); RU_MAP.put("кирка", "pickaxe");
        RU_MAP.put("лопата", "shovel"); RU_MAP.put("топор", "axe");
        RU_MAP.put("яблоко", "apple"); RU_MAP.put("хлеб", "bread");
        RU_MAP.put("мясо", "beef"); RU_MAP.put("рыба", "cod");
        RU_MAP.put("яйцо", "egg"); RU_MAP.put("молоко", "milk_bucket");
        RU_MAP.put("ведро", "bucket"); RU_MAP.put("ножницы", "shears");
        RU_MAP.put("компас", "compass"); RU_MAP.put("часы", "clock");
        RU_MAP.put("карта", "map"); RU_MAP.put("седло", "saddle");
        RU_MAP.put("нить", "string"); RU_MAP.put("перо", "feather");
        RU_MAP.put("кожа", "leather"); RU_MAP.put("кость", "bone");
        RU_MAP.put("порох", "gunpowder"); RU_MAP.put("слиток", "ingot");
        RU_MAP.put("дубовые доски", "oak_planks");
        RU_MAP.put("берёзовые доски", "birch_planks");
        RU_MAP.put("дубовое бревно", "oak_log");
        RU_MAP.put("каменный меч", "stone_sword");
        RU_MAP.put("железный меч", "iron_sword");
        RU_MAP.put("алмазный меч", "diamond_sword");
    }

    // Цвета — контрастные и читаемые
    private static final int BG          = 0xFF1A1A2E;
    private static final int CANVAS_BG   = 0xFF12121F;
    private static final int PANEL_BG    = 0xFF1E2030;
    private static final int PANEL_DARK  = 0xFF161622;
    private static final int BORDER      = 0xFF3A3D5C;
    private static final int BORDER_LT   = 0xFF5A5E8A;
    private static final int ACCENT_G    = 0xFF00C853;
    private static final int ACCENT_B    = 0xFF2979FF;
    private static final int ACCENT_Y    = 0xFFFFD600;
    private static final int RED         = 0xFFE53935;
    private static final int NODE_BG     = 0xFF1E2235;
    private static final int NODE_HOV    = 0xFF252840;
    private static final int HDR_ON      = 0xFF003320;
    private static final int HDR_OFF     = 0xFF3A0808;
    private static final int PORT_C      = 0xFFFFD600;
    private static final int LINE_C      = 0xBB00C853;
    private static final int WHITE       = 0xFFFFFFFF;
    private static final int GRAY        = 0xFF8888AA;
    private static final int LTGRAY      = 0xFFCCCCDD;
    private static final int GREEN       = 0xFF00E676;
    private static final int DKRED       = 0xFFFF5252;

    private static final int LEFT_W = 110;
    private static final int TOP_H  = 28;
    private static final int BOT_H  = 20;
    private static final String[] TOOLS = {"✦ Выделить","✥ Переместить","⟷ Соединить","✕ Удалить"};
    private int activeTool = 0;

    public CraftsiScreen() {
        super(Text.literal("Craftsi"));
    }

    @Override
    protected void init() {
        super.init();
        allItems.clear();
        for (Item item : Registries.ITEM) allItems.add(item);
        rebuildFilter();
    }

    // ══════════════════════════════════════════════════
    //  RENDER
    // ══════════════════════════════════════════════════

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        // Фон
        ctx.fill(0, 0, width, height, BG);

        renderCanvas(ctx, mx, my);
        renderTopBar(ctx, mx, my);
        renderLeftPanel(ctx, mx, my);
        renderStatusBar(ctx, mx, my);

        if (activePanel == Panel.EDIT_NODE && editingNode != null)
            renderEditPanel(ctx, mx, my);
        if (activePanel == Panel.ITEM_PICKER)
            renderItemPicker(ctx, mx, my);

        super.render(ctx, mx, my, delta);
    }

    // ─── TOP BAR ───────────────────────────────────────

    private void renderTopBar(DrawContext ctx, int mx, int my) {
        ctx.fill(0, 0, width, TOP_H, PANEL_DARK);
        ctx.fill(0, TOP_H-1, width, TOP_H, BORDER);

        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal("✦ CRAFTSI ✦"), LEFT_W/2, 10, ACCENT_Y);

        int x = LEFT_W + 8;
        x = topBtn(ctx,"+ Нода", x, mx, my, ACCENT_G) + 6;
        x = topBtn(ctx,"⚙ Настройки", x, mx, my, PANEL_BG) + 6;

        // Зум справа
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("⊕ " + (int)(zoom*100) + "%"), width-55, 10, GRAY);
    }

    private int topBtn(DrawContext ctx, String label, int x, int mx, int my, int col) {
        int w = textRenderer.getWidth(label) + 12, h = 20, y = 4;
        boolean hov = mx>=x && mx<=x+w && my>=y && my<=y+h;
        ctx.fill(x, y, x+w, y+h, hov ? lighten(col,30) : col);
        ctx.drawBorder(x, y, w, h, hov ? BORDER_LT : BORDER);
        ctx.drawTextWithShadow(textRenderer, Text.literal(label), x+6, y+6, WHITE);
        return x+w;
    }

    // ─── LEFT PANEL ────────────────────────────────────

    private void renderLeftPanel(DrawContext ctx, int mx, int my) {
        ctx.fill(0, TOP_H, LEFT_W, height-BOT_H, PANEL_DARK);
        ctx.fill(LEFT_W-1, TOP_H, LEFT_W, height-BOT_H, BORDER);

        for (int i = 0; i < TOOLS.length; i++) {
            int ty = TOP_H + 8 + i*24;
            boolean hov = mx>=4 && mx<=LEFT_W-4 && my>=ty && my<=ty+20;
            boolean act = activeTool == i;
            ctx.fill(4, ty, LEFT_W-4, ty+20,
                act ? 0xFF1A2A4A : (hov ? NODE_HOV : PANEL_DARK));
            ctx.drawBorder(4, ty, LEFT_W-8, 20, act ? ACCENT_B : BORDER);
            ctx.drawTextWithShadow(textRenderer, Text.literal(TOOLS[i]), 8, ty+6,
                act ? WHITE : LTGRAY);
        }

        // Кол-во нод
        int statsY = TOP_H + 8 + TOOLS.length*24 + 10;
        ctx.fill(4, statsY, LEFT_W-4, statsY+1, BORDER);
        int nodes = NodeGraph.getInstance().getNodes().size();
        long auto = NodeGraph.getInstance().getNodes().stream().filter(n->n.autoCraftOn).count();
        ctx.drawTextWithShadow(textRenderer, Text.literal("Нод: "+nodes), 6, statsY+6, GRAY);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Авто: "+auto), 6, statsY+18, auto>0?GREEN:GRAY);
    }

    // ─── STATUS BAR ────────────────────────────────────

    private void renderStatusBar(DrawContext ctx, int mx, int my) {
        int y = height-BOT_H;
        ctx.fill(0, y, width, height, PANEL_DARK);
        ctx.fill(0, y, width, y+1, BORDER);
        long total = NodeGraph.getInstance().getNodes().stream().mapToLong(n->n.craftCount).sum();
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("  Нод: " + NodeGraph.getInstance().getNodes().size()
                + "   Связей: " + NodeGraph.getInstance().getConnections().size()
                + "   Создано предметов: " + total
                + "   Масштаб: " + (int)(zoom*100) + "%"),
            LEFT_W, y+6, GRAY);
    }

    // ─── CANVAS ────────────────────────────────────────

    private void renderCanvas(DrawContext ctx, int mx, int my) {
        int cx=LEFT_W, cy=TOP_H, cw=width-LEFT_W, ch=height-TOP_H-BOT_H;
        ctx.fill(cx, cy, cx+cw, cy+ch, CANVAS_BG);
        ctx.enableScissor(cx, cy, cx+cw, cy+ch);

        // Сетка
        int gs = Math.max(8,(int)(32*zoom));
        int ox = ((canvasOffX % gs) + gs) % gs;
        int oy = ((canvasOffY % gs) + gs) % gs;
        for (int gx=ox; gx<cw; gx+=gs) ctx.fill(cx+gx, cy, cx+gx+1, cy+ch, 0xFF1C1C30);
        for (int gy=oy; gy<ch; gy+=gs) ctx.fill(cx, cy+gy, cx+cw, cy+gy+1, 0xFF1C1C30);

        // Соединения
        for (String[] conn : NodeGraph.getInstance().getConnections()) {
            RecipeNode a = NodeGraph.getInstance().findById(conn[0]);
            RecipeNode b = NodeGraph.getInstance().findById(conn[1]);
            if (a==null||b==null) continue;
            int ax = toSX(a.x+a.w), ay = toSY(a.y+a.h/2);
            int bx = toSX(b.x),     by = toSY(b.y+b.h/2);
            drawBezier(ctx, ax, ay, bx, by, LINE_C);
        }
        if (connectFrom != null) {
            int ax = toSX(connectFrom.x+connectFrom.w);
            int ay = toSY(connectFrom.y+connectFrom.h/2);
            drawBezier(ctx, ax, ay, mx, my, ACCENT_Y);
        }

        // Ноды
        for (RecipeNode node : NodeGraph.getInstance().getNodes())
            drawNode(ctx, node, mx, my);

        ctx.disableScissor();
    }

    private void drawNode(DrawContext ctx, RecipeNode node, int mx, int my) {
        int x=toSX(node.x), y=toSY(node.y);
        int w=(int)(node.w*zoom), h=(int)(node.h*zoom);
        int HH = Math.max(18,(int)(18*zoom));
        boolean hov = mx>=x&&mx<=x+w&&my>=y&&my<=y+h;

        // Тень
        ctx.fill(x+3,y+4,x+w+3,y+h+4,0x44000000);
        // Фон
        ctx.fill(x, y, x+w, y+h, hov ? NODE_HOV : NODE_BG);
        // Рамка
        int borderCol = node.autoCraftOn ? (node.isActive ? ACCENT_G : RED) : BORDER_LT;
        ctx.drawBorder(x, y, w, h, borderCol);
        // Заголовок
        ctx.fill(x, y, x+w, y+HH, node.autoCraftOn ? (node.isActive ? HDR_ON : HDR_OFF) : 0xFF1A1A30);
        ctx.fill(x, y+HH, x+w, y+HH+1, BORDER);

        // Текст заголовка
        String title = fitText(node.id, w-60);
        ctx.drawTextWithShadow(textRenderer, Text.literal(title), x+6, y+5, WHITE);

        // Статус справа в заголовке
        String badge = node.autoCraftOn ? (node.isActive ? "●ВКЛ" : "●СТОП") : "○ВЫКЛ";
        int badgeCol = node.autoCraftOn ? (node.isActive ? GREEN : DKRED) : GRAY;
        ctx.drawTextWithShadow(textRenderer, Text.literal(badge), x+w-textRenderer.getWidth(badge)-4, y+5, badgeCol);

        if (zoom >= 0.6f) {
            // Результат (иконка + название)
            if (!node.result.isEmpty()) {
                ctx.drawItem(node.result, x+6, y+HH+3);
                String rn = node.result.getItem().toString().replace("minecraft:","");
                ctx.drawTextWithShadow(textRenderer, Text.literal("→ "+fitText(rn, w-36)), x+24, y+HH+7, LTGRAY);
            } else {
                ctx.drawTextWithShadow(textRenderer, Text.literal("→ (нет рецепта)"), x+6, y+HH+7, GRAY);
            }

            // Ингредиенты — иконки
            int shown = 0;
            for (int i=0; i<9 && shown<5; i++) {
                ItemStack s = node.inputs[i];
                if (s!=null && !s.isEmpty()) {
                    ctx.drawItem(s, x+6+shown*18, y+HH+22);
                    shown++;
                }
            }
            if (shown==0)
                ctx.drawTextWithShadow(textRenderer, Text.literal("Нет ингредиентов"), x+6, y+HH+26, GRAY);

            // Счётчик
            if (node.craftCount > 0)
                ctx.drawTextWithShadow(textRenderer, Text.literal("✔ "+node.craftCount), x+6, y+h-26, GREEN);

            // Недостающие
            if (node.autoCraftOn && !node.isActive) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player != null) {
                    String miss = CraftExecutor.getMissingInfo(node, mc.player);
                    if (!miss.isEmpty())
                        ctx.drawTextWithShadow(textRenderer,
                            Text.literal("✘ "+fitText(miss, w-10)), x+6, y+h-16, DKRED);
                }
            }

            // Кнопка авто-крафт
            int btnY = y+h-12, btnX=x+4, btnW=w-8;
            boolean btnHov = mx>=btnX&&mx<=btnX+btnW&&my>=btnY&&my<=btnY+10;
            ctx.fill(btnX, btnY, btnX+btnW, btnY+10,
                node.autoCraftOn ? (btnHov?0xFF005522:0xFF003311) : (btnHov?0xFF222244:0xFF1A1A33));
            ctx.drawBorder(btnX, btnY, btnW, 10, node.autoCraftOn ? ACCENT_G : BORDER);
            String autotxt = node.autoCraftOn ? "⚙ Авто-крафт: ВКЛ" : "⚙ Авто-крафт: ВЫКЛ";
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(autotxt), btnX+btnW/2, btnY+1, WHITE);
        }

        // Порты
        ctx.fill(x-4,y+h/2-4,x+4,y+h/2+4, PORT_C);
        ctx.fill(x+w-4,y+h/2-4,x+w+4,y+h/2+4, PORT_C);

        // Ресайз маркер
        ctx.fill(x+w-6,y+h-6,x+w,y+h,BORDER_LT);
    }

    // ─── EDIT PANEL ────────────────────────────────────

    private void renderEditPanel(DrawContext ctx, int mx, int my) {
        int pw=400, ph=320;
        int px=(width-pw)/2, py=(height-ph)/2;

        // Фон с рамкой
        ctx.fill(px-2,py-2,px+pw+2,py+ph+2, ACCENT_B);
        ctx.fill(px,py,px+pw,py+ph, PANEL_BG);

        // Заголовок
        ctx.fill(px,py,px+pw,py+22, PANEL_DARK);
        ctx.fill(px,py+22,px+pw,py+23, BORDER);
        ctx.drawTextWithShadow(textRenderer, Text.literal("✦ Узел: "+editingNode.id), px+8, py+7, WHITE);

        // Кнопка закрыть
        btn(ctx,"✕",px+pw-20,py+4,16,14,mx,my,RED,RED);

        // ── Левая: рецепт ──
        ctx.drawTextWithShadow(textRenderer, Text.literal("Рецепт крафта:"), px+8, py+28, GRAY);

        // Сетка 3x3
        for (int i=0; i<9; i++) {
            int col=i%3, row=i/3;
            int sx=px+8+col*46, sy=py+40+row*46;
            boolean sel = editingSlot==i;
            ctx.fill(sx,sy,sx+42,sy+42, sel?0xFF1E3020:PANEL_DARK);
            ctx.drawBorder(sx,sy,42,42, sel?ACCENT_G:BORDER);
            ItemStack stack = editingNode.inputs[i];
            if (stack!=null && !stack.isEmpty()) {
                ctx.drawItem(stack, sx+13, sy+13);
                // Кол-во
                ctx.drawTextWithShadow(textRenderer,
                    Text.literal("×"+stack.getCount()), sx+28, sy+30, WHITE);
            } else {
                ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(String.valueOf(i+1)), sx+21, sy+17, GRAY);
            }
        }

        // Стрелка + результат
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("→"), px+155, py+90, ACCENT_Y);
        int rx2=px+166, ry2=py+72;
        boolean rSel = editingSlot==9;
        ctx.fill(rx2,ry2,rx2+42,ry2+42, rSel?0xFF1E3020:PANEL_DARK);
        ctx.drawBorder(rx2,ry2,42,42, rSel?ACCENT_G:BORDER);
        if (!editingNode.result.isEmpty()) {
            ctx.drawItem(editingNode.result, rx2+13, ry2+13);
            ctx.drawTextWithShadow(textRenderer,
                Text.literal("×"+editingNode.result.getCount()), rx2+28, ry2+30, WHITE);
        } else {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("?"), rx2+21, ry2+17, GRAY);
        }

        // Авто-крафт тоггл
        boolean autOn = editingNode.autoCraftOn;
        ctx.fill(px+8,py+180,px+200,py+196, autOn?HDR_ON:HDR_OFF);
        ctx.drawBorder(px+8,py+180,192,16, autOn?ACCENT_G:RED);
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal(autOn?"⚙ Авто-крафт: ВКЛ ✔":"⚙ Авто-крафт: ВЫКЛ ✘"),
            px+104, py+183, WHITE);

        // Статистика
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("Создано: "+editingNode.craftCount+"  Приоритет: "+editingNode.priority),
            px+8, py+202, GRAY);

        // ── Разделитель ──
        ctx.fill(px+215,py+22,px+216,py+ph, BORDER);

        // ── Правая: ввод ──
        int rx=px+222;
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("Выбранный слот: "+(editingSlot<0?"—":editingSlot==9?"Результат":"Слот "+(editingSlot+1))),
            rx, py+28, GRAY);

        // Поле ввода
        ctx.fill(rx,py+44,rx+164,py+58, PANEL_DARK);
        ctx.drawBorder(rx,py+44,164,14, editingSlot>=0?ACCENT_G:BORDER);
        ctx.drawTextWithShadow(textRenderer,
            Text.literal(inputBuffer.isEmpty()?"Введи название...":inputBuffer+"|"),
            rx+4, py+47, inputBuffer.isEmpty()?GRAY:WHITE);

        ctx.drawTextWithShadow(textRenderer,
            Text.literal("(рус. или англ., напр: палка / stick)"),
            rx, py+62, GRAY);

        // Кнопки
        btn(ctx,"✔ Применить",rx,py+76,160,16,mx,my,0xFF004D20,ACCENT_G);
        btn(ctx,"✕ Очистить слот",rx,py+96,160,16,mx,my,0xFF4D0000,RED);
        btn(ctx,"🔍 Выбрать из списка",rx,py+116,160,16,mx,my,0xFF001A4D,ACCENT_B);

        // Инвентарь игрока
        ctx.fill(rx,py+140,rx+164,py+141,BORDER);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Инвентарь:"), rx, py+146, GRAY);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            int shown=0;
            for (int i=0; i<mc.player.getInventory().size()&&shown<8; i++) {
                ItemStack inv = mc.player.getInventory().getStack(i);
                if (!inv.isEmpty()) {
                    int ix2=rx+shown*20, iy2=py+158;
                    ctx.fill(ix2,iy2,ix2+18,iy2+18,PANEL_DARK);
                    ctx.drawBorder(ix2,iy2,18,18,BORDER);
                    ctx.drawItem(inv, ix2+1, iy2+1);
                    shown++;
                }
            }
        }

        // Нижние кнопки
        btn(ctx,"Сохранить и закрыть",px+8,py+ph-22,180,16,mx,my,0xFF004D20,ACCENT_G);
        btn(ctx,"Отмена",px+194,py+ph-22,80,16,mx,my,PANEL_DARK,BORDER_LT);
    }

    // ─── ITEM PICKER ───────────────────────────────────

    private void renderItemPicker(DrawContext ctx, int mx, int my) {
        int pw=420, ph=340;
        int px=(width-pw)/2, py=(height-ph)/2;

        ctx.fill(px-2,py-2,px+pw+2,py+ph+2, ACCENT_B);
        ctx.fill(px,py,px+pw,py+ph, PANEL_BG);
        ctx.fill(px,py,px+pw,py+22, PANEL_DARK);
        ctx.fill(px,py+22,px+pw,py+23, BORDER);
        ctx.drawTextWithShadow(textRenderer, Text.literal("🔍 Выбор предмета"), px+8, py+7, WHITE);
        btn(ctx,"✕",px+pw-20,py+4,16,14,mx,my,RED,RED);

        // Поле поиска
        ctx.fill(px+4,py+26,px+pw-4,py+40, PANEL_DARK);
        ctx.drawBorder(px+4,py+26,pw-8,14, ACCENT_B);
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("🔍 " + (searchQuery.isEmpty()?"Поиск (рус/англ)...":searchQuery+"|")),
            px+8, py+29, searchQuery.isEmpty()?GRAY:WHITE);

        // Количество найдено
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("Найдено: "+filteredItems.size()),
            px+pw-80, py+29, GRAY);

        // Сетка предметов
        int startIdx = itemPickerScroll * COLS;
        int rows = (ph - 80) / SLOT;
        ctx.enableScissor(px+4, py+44, px+pw-4, py+44+rows*SLOT);

        for (int i=0; i<filteredItems.size()-startIdx; i++) {
            int col=i%COLS, row=i/COLS;
            if (row >= rows) break;
            Item item = filteredItems.get(startIdx+i);
            int ix=px+4+col*SLOT, iy=py+44+row*SLOT;
            boolean hov = mx>=ix&&mx<=ix+SLOT-2&&my>=iy&&my<=iy+SLOT-2;
            ctx.fill(ix,iy,ix+SLOT-2,iy+SLOT-2, hov?0xFF1E3050:PANEL_DARK);
            ctx.drawBorder(ix,iy,SLOT-2,SLOT-2, hov?ACCENT_B:BORDER);
            // Иконка предмета
            ctx.drawItem(new ItemStack(item), ix+2, iy+2);
            // Название под иконкой
            String nm = item.toString().replace("minecraft:","");
            if (nm.length()>6) nm=nm.substring(0,6);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(nm), ix+SLOT/2-1, iy+SLOT-14, GRAY);

            if (hov) hoveredItem = item;
        }
        ctx.disableScissor();

        // Тултип для hovered
        if (hoveredItem != null && mx>=px+4 && mx<=px+pw-4 && my>=py+44 && my<=py+ph-30) {
            String fullName = hoveredItem.toString().replace("minecraft:","");
            ctx.drawTooltip(textRenderer, Text.literal(fullName), mx, my);
        }

        // Полоса прокрутки
        int totalRows = (int)Math.ceil(filteredItems.size()/(double)COLS);
        int visRows = rows;
        if (totalRows > visRows) {
            int scrollH = (int)((float)visRows/totalRows*(ph-80));
            int scrollY = py+44 + (int)((float)itemPickerScroll/totalRows*(ph-80));
            ctx.fill(px+pw-6, py+44, px+pw-2, py+44+ph-80, PANEL_DARK);
            ctx.fill(px+pw-6, scrollY, px+pw-2, scrollY+scrollH, BORDER_LT);
        }

        // Подсказка снизу
        ctx.fill(px,py+ph-26,px+pw,py+ph, PANEL_DARK);
        ctx.fill(px,py+ph-26,px+pw,py+ph-25, BORDER);
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("ЛКМ — выбрать предмет   Скролл — прокрутить   ESC — закрыть"),
            px+8, py+ph-18, GRAY);
    }

    // ══════════════════════════════════════════════════
    //  MOUSE / KEY
    // ══════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx=(int)mouseX, my=(int)mouseY;
        hoveredItem = null;

        if (activePanel==Panel.ITEM_PICKER) return handlePickerClick(mx,my,button);
        if (activePanel==Panel.EDIT_NODE)   return handleEditClick(mx,my,button);

        if (my<TOP_H)     return handleTopClick(mx,my,button);
        if (mx<LEFT_W)    return handleLeftClick(mx,my,button);
        if (my>=height-BOT_H) return true;

        return handleCanvasClick(mx,my,button);
    }

    private boolean handleTopClick(int mx, int my, int button) {
        int x = LEFT_W+8;
        int w = textRenderer.getWidth("+ Нода")+12;
        if (mx>=x && mx<=x+w && my>=4 && my<=24) { addNode(); return true; }
        return true;
    }

    private boolean handleLeftClick(int mx, int my, int button) {
        for (int i=0; i<TOOLS.length; i++) {
            int ty=TOP_H+8+i*24;
            if (mx>=4&&mx<=LEFT_W-4&&my>=ty&&my<=ty+20) { activeTool=i; return true; }
        }
        return true;
    }

    private boolean handleCanvasClick(int mx, int my, int button) {
        List<RecipeNode> nodes = NodeGraph.getInstance().getNodes();
        for (int i=nodes.size()-1; i>=0; i--) {
            RecipeNode node = nodes.get(i);
            int x=toSX(node.x), y=toSY(node.y);
            int w=(int)(node.w*zoom), h=(int)(node.h*zoom);
            int HH=Math.max(18,(int)(18*zoom));

            if (mx<x-6||mx>x+w+6||my<y||my>y+h) continue;

            if (button==1) { NodeGraph.getInstance().removeNode(node.id); return true; }

            // Порт выход
            if (mx>=x+w-6&&mx<=x+w+6&&my>=y+h/2-6&&my<=y+h/2+6) {
                connectFrom=node; return true;
            }
            // Порт вход
            if (mx>=x-6&&mx<=x+6&&my>=y+h/2-6&&my<=y+h/2+6) {
                if (connectFrom!=null&&!connectFrom.id.equals(node.id)) {
                    NodeGraph.getInstance().addConnection(connectFrom.id,node.id);
                    connectFrom=null; return true;
                }
            }
            // Авто-крафт кнопка
            int btnY=y+h-12;
            if (mx>=x+4&&mx<=x+w-4&&my>=btnY&&my<=btnY+10) {
                node.autoCraftOn=!node.autoCraftOn; return true;
            }
            // Ресайз
            if (mx>=x+w-8&&my>=y+h-8) {
                resizeNode=node; resizeStartW=node.w; resizeStartH=node.h;
                resizeMouseX=mx; resizeMouseY=my; return true;
            }
            // Заголовок — перетащить
            if (my>=y&&my<=y+HH) {
                dragNode=node; dragOffX=mx-x; dragOffY=my-y; return true;
            }
            // Тело — редактор
            if (my>y+HH&&my<btnY) {
                editingNode=node; editingSlot=-1; inputBuffer="";
                activePanel=Panel.EDIT_NODE; return true;
            }
        }

        connectFrom=null;
        draggingCanvas=true;
        canvasDragStartX=mx-canvasOffX;
        canvasDragStartY=my-canvasOffY;
        return true;
    }

    private boolean handleEditClick(int mx, int my, int button) {
        int pw=400, ph=320;
        int px=(width-pw)/2, py=(height-ph)/2;
        int rx=px+222;

        // Закрыть / Отмена
        if ((mx>=px+pw-20&&mx<=px+pw-4&&my>=py+4&&my<=py+18) ||
            (mx>=px+194&&mx<=px+274&&my>=py+ph-22&&my<=py+ph-6)) {
            activePanel=Panel.NONE; return true;
        }
        // Сохранить
        if (mx>=px+8&&mx<=px+188&&my>=py+ph-22&&my<=py+ph-6) {
            activePanel=Panel.NONE; return true;
        }
        // Слоты 3x3
        for (int i=0; i<9; i++) {
            int col=i%3, row=i/3;
            int sx=px+8+col*46, sy=py+40+row*46;
            if (mx>=sx&&mx<=sx+42&&my>=sy&&my<=sy+42) {
                editingSlot=i; inputBuffer=""; return true;
            }
        }
        // Результат
        if (mx>=px+166&&mx<=px+208&&my>=py+72&&my<=py+114) {
            editingSlot=9; inputBuffer=""; return true;
        }
        // Авто-крафт
        if (mx>=px+8&&mx<=px+200&&my>=py+180&&my<=py+196) {
            editingNode.autoCraftOn=!editingNode.autoCraftOn; return true;
        }
        // Применить
        if (mx>=rx&&mx<=rx+160&&my>=py+76&&my<=py+92) { applyInput(); return true; }
        // Очистить
        if (mx>=rx&&mx<=rx+160&&my>=py+96&&my<=py+112) { clearSlot(); return true; }
        // Выбрать из списка
        if (mx>=rx&&mx<=rx+160&&my>=py+116&&my<=py+132) {
            activePanel=Panel.ITEM_PICKER; rebuildFilter(); return true;
        }
        return true;
    }

    private boolean handlePickerClick(int mx, int my, int button) {
        int pw=420, ph=340;
        int px=(width-pw)/2, py=(height-ph)/2;

        // Закрыть
        if (mx>=px+pw-20&&mx<=px+pw-4&&my>=py+4&&my<=py+18) {
            activePanel=Panel.EDIT_NODE; return true;
        }
        // Поле поиска — клик (ввод через charTyped)
        if (mx>=px+4&&mx<=px+pw-4&&my>=py+26&&my<=py+40) return true;

        // Клик по предмету
        int startIdx=itemPickerScroll*COLS;
        int rows=(ph-80)/SLOT;
        for (int i=0; i<filteredItems.size()-startIdx; i++) {
            int col=i%COLS, row=i/COLS;
            if (row>=rows) break;
            Item item=filteredItems.get(startIdx+i);
            int ix=px+4+col*SLOT, iy=py+44+row*SLOT;
            if (mx>=ix&&mx<=ix+SLOT-2&&my>=iy&&my<=iy+SLOT-2) {
                applyItemToSlot(item);
                activePanel=Panel.EDIT_NODE;
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        int mx=(int)mouseX, my=(int)mouseY;
        if (dragNode!=null) {
            dragNode.x=(int)((mx-LEFT_W-canvasOffX-dragOffX)/zoom);
            dragNode.y=(int)((my-TOP_H-canvasOffY-dragOffY)/zoom);
            return true;
        }
        if (resizeNode!=null) {
            resizeNode.w=Math.max(160,resizeStartW+(int)((mx-resizeMouseX)/zoom));
            resizeNode.h=Math.max(90, resizeStartH+(int)((my-resizeMouseY)/zoom));
            return true;
        }
        if (draggingCanvas) {
            canvasOffX=mx-canvasDragStartX;
            canvasOffY=my-canvasDragStartY;
            return true;
        }
        return super.mouseDragged(mouseX,mouseY,button,dx,dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragNode=null; resizeNode=null; draggingCanvas=false;
        return super.mouseReleased(mouseX,mouseY,button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (activePanel==Panel.ITEM_PICKER) {
            int pw=420, ph=340;
            int px=(width-pw)/2, py=(height-ph)/2;
            if (mouseX>=px&&mouseX<=px+pw&&mouseY>=py&&mouseY<=py+ph) {
                int rows=(ph-80)/SLOT;
                int maxScroll=(int)Math.ceil(filteredItems.size()/(double)COLS)-rows;
                itemPickerScroll=Math.max(0,Math.min(maxScroll,itemPickerScroll-(int)v));
                return true;
            }
        }
        if (activePanel==Panel.NONE && mouseX>LEFT_W && mouseY>TOP_H && mouseY<height-BOT_H) {
            zoom=Math.max(0.3f,Math.min(2.0f,zoom+(float)v*0.1f));
            return true;
        }
        return super.mouseScrolled(mouseX,mouseY,h,v);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (activePanel!=Panel.NONE) {
            if (keyCode==259 && !inputBuffer.isEmpty()) {
                inputBuffer=inputBuffer.substring(0,inputBuffer.length()-1);
                if (activePanel==Panel.ITEM_PICKER) {
                    searchQuery=inputBuffer; rebuildFilter();
                }
                return true;
            }
            if (keyCode==259 && activePanel==Panel.ITEM_PICKER && !searchQuery.isEmpty()) {
                searchQuery=searchQuery.substring(0,searchQuery.length()-1);
                rebuildFilter(); return true;
            }
            if (keyCode==257||keyCode==335) { applyInput(); return true; }
            if (keyCode==256) {
                if (activePanel==Panel.ITEM_PICKER) activePanel=Panel.EDIT_NODE;
                else activePanel=Panel.NONE;
                return true;
            }
            return true;
        }
        if (keyCode==256) { this.close(); return true; }
        return super.keyPressed(keyCode,scanCode,modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (activePanel==Panel.ITEM_PICKER) {
            searchQuery+=chr; inputBuffer+=chr;
            rebuildFilter(); return true;
        }
        if (activePanel==Panel.EDIT_NODE && editingSlot>=0) {
            inputBuffer+=chr; return true;
        }
        return false;
    }

    // ══════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════

    private void applyInput() {
        if (editingNode==null||editingSlot<0||inputBuffer.isEmpty()) return;
        String raw = inputBuffer.trim().toLowerCase();

        // Попробуем русское название
        String eng = null;
        for (Map.Entry<String,String> e : RU_MAP.entrySet()) {
            if (raw.contains(e.getKey())) { eng = e.getValue(); break; }
        }
        String itemId = eng != null ? "minecraft:"+eng : (raw.contains(":")?raw:"minecraft:"+raw);

        try {
            Item item = Registries.ITEM.get(Identifier.of(itemId));
            if (item==Items.AIR) {
                // Попробуем поиск по подстроке
                for (Item it : allItems) {
                    if (it.toString().contains(raw)) { item=it; break; }
                }
            }
            if (item==Items.AIR) return;
            applyItemToSlot(item);
            inputBuffer=""; editingSlot=-1;
        } catch (Exception ignored) {}
    }

    private void applyItemToSlot(Item item) {
        if (editingNode==null||editingSlot<0) return;
        ItemStack stack=new ItemStack(item,1);
        if (editingSlot==9) editingNode.result=stack;
        else editingNode.inputs[editingSlot]=stack;
    }

    private void clearSlot() {
        if (editingNode==null||editingSlot<0) return;
        if (editingSlot==9) editingNode.result=ItemStack.EMPTY;
        else editingNode.inputs[editingSlot]=ItemStack.EMPTY;
        inputBuffer="";
    }

    private void addNode() {
        int count=NodeGraph.getInstance().getNodes().size();
        RecipeNode node=new RecipeNode("Рецепт "+(count+1));
        node.x=60+(count*200)%600; node.y=60+(count/4)*140;
        node.w=180; node.h=110;
        NodeGraph.getInstance().addNode(node);
    }

    private void rebuildFilter() {
        filteredItems.clear();
        String q=searchQuery.toLowerCase();
        // Если русский запрос — конвертируем
        String engQ = null;
        if (!q.isEmpty()) {
            for (Map.Entry<String,String> e : RU_MAP.entrySet()) {
                if (q.contains(e.getKey())) { engQ=e.getValue(); break; }
            }
        }
        final String fq = engQ!=null ? engQ : q;
        for (Item item : allItems) {
            if (fq.isEmpty() || item.toString().contains(fq)) filteredItems.add(item);
        }
        itemPickerScroll=0;
    }

    private void drawBezier(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        int steps=Math.max(20,Math.abs(x2-x1)/2);
        int c1x=x1+(x2-x1)/3, c2x=x2-(x2-x1)/3;
        for (int i=0; i<=steps; i++) {
            float t=i/(float)steps, u=1-t;
            int px=(int)(u*u*u*x1+3*u*u*t*c1x+3*u*t*t*c2x+t*t*t*x2);
            int py2=(int)(u*u*u*y1+3*u*u*t*y1+3*u*t*t*y2+t*t*t*y2);
            ctx.fill(px,py2,px+2,py2+2,color);
        }
    }

    private void btn(DrawContext ctx, String label, int x, int y, int w, int h,
                     int mx, int my, int col, int borderCol) {
        boolean hov=mx>=x&&mx<=x+w&&my>=y&&my<=y+h;
        ctx.fill(x,y,x+w,y+h, hov?lighten(col,25):col);
        ctx.drawBorder(x,y,w,h,borderCol);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(label), x+w/2, y+(h-7)/2, WHITE);
    }

    private int toSX(int nodeX) { return LEFT_W+canvasOffX+(int)(nodeX*zoom); }
    private int toSY(int nodeY) { return TOP_H+canvasOffY+(int)(nodeY*zoom); }

    private String fitText(String s, int maxPx) {
        if (textRenderer.getWidth(s)<=maxPx) return s;
        while (s.length()>1 && textRenderer.getWidth(s+"…")>maxPx) s=s.substring(0,s.length()-1);
        return s+"…";
    }

    private int lighten(int c, int amt) {
        int r=Math.min(255,((c>>16)&0xFF)+amt);
        int g=Math.min(255,((c>>8)&0xFF)+amt);
        int b=Math.min(255,(c&0xFF)+amt);
        return (c&0xFF000000)|(r<<16)|(g<<8)|b;
    }

    @Override public boolean shouldPause() { return false; }
    @Override public boolean shouldCloseOnEsc() { return true; }
}
