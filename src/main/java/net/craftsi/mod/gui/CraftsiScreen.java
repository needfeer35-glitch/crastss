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

    private RecipeNode dragNode = null;
    private int dragOffX, dragOffY;
    private RecipeNode resizeNode = null;
    private int resizeStartW, resizeStartH, resizeMouseX, resizeMouseY;
    private RecipeNode connectFrom = null;
    private int canvasOffX = 0, canvasOffY = 0;
    private boolean draggingCanvas = false;
    private int canvasDragStartX, canvasDragStartY;
    private float zoom = 1.0f;

    private RecipeNode editingNode = null;
    private int editingSlot = -1;
    private String inputBuffer = "";
    private int editingCount = 1;

    private String searchQuery = "";
    private List<Item> allItems = new ArrayList<>();
    private List<Item> filteredItems = new ArrayList<>();
    private int pickerScroll = 0;
    private Item hoveredPickerItem = null;
    private static final int PCOLS = 8;
    private static final int PSLOT = 38;

    private static final Map<String, String> RU = new HashMap<>();
    static {
        RU.put("палка","stick"); RU.put("дерево","log"); RU.put("доски","planks");
        RU.put("камень","stone"); RU.put("земля","dirt"); RU.put("песок","sand");
        RU.put("железо","iron_ingot"); RU.put("золото","gold_ingot");
        RU.put("алмаз","diamond"); RU.put("уголь","coal"); RU.put("факел","torch");
        RU.put("верстак","crafting_table"); RU.put("печь","furnace");
        RU.put("сундук","chest"); RU.put("стекло","glass"); RU.put("шерсть","white_wool");
        RU.put("кирпич","brick"); RU.put("книга","book"); RU.put("лук","bow");
        RU.put("стрела","arrow"); RU.put("меч","wooden_sword"); RU.put("кирка","wooden_pickaxe");
        RU.put("яблоко","apple"); RU.put("хлеб","bread"); RU.put("ведро","bucket");
        RU.put("нить","string"); RU.put("перо","feather"); RU.put("кожа","leather");
        RU.put("кость","bone"); RU.put("порох","gunpowder");
        RU.put("дубовые доски","oak_planks"); RU.put("дубовое бревно","oak_log");
        RU.put("берёзовые доски","birch_planks"); RU.put("железная кирка","iron_pickaxe");
        RU.put("алмазный меч","diamond_sword"); RU.put("железный меч","iron_sword");
        RU.put("каменный меч","stone_sword"); RU.put("деревянная палка","stick");
    }

    private static final int BG       = 0xFF0D0F1A;
    private static final int CANVAS   = 0xFF0A0C14;
    private static final int PANELBG  = 0xFF141620;
    private static final int PANELDARK= 0xFF0D0F18;
    private static final int BORDER   = 0xFF2A2D45;
    private static final int BORDERL  = 0xFF4A4E72;
    private static final int GREEN    = 0xFF00C853;
    private static final int GREEND   = 0xFF003820;
    private static final int BLUE     = 0xFF2979FF;
    private static final int BLUED    = 0xFF001A50;
    private static final int YELLOW   = 0xFFFFD600;
    private static final int RED      = 0xFFE53935;
    private static final int REDD     = 0xFF3A0808;
    private static final int NODEBG   = 0xFF141828;
    private static final int NODEHOV  = 0xFF1A1E32;
    private static final int HDRON    = 0xFF003820;
    private static final int HDROFF   = 0xFF380808;
    private static final int PORT     = 0xFFFFD600;
    private static final int LINE     = 0xCC00C853;
    private static final int WHITE    = 0xFFFFFFFF;
    private static final int GRAY     = 0xFF6B7099;
    private static final int LGRAY    = 0xFFB0B4CC;

    private static final int LW = 112;
    private static final int TH = 28;
    private static final int BH = 20;
    private static final String[] TOOLS = {"✦ Выделить","✥ Переместить","⟷ Соединить","✕ Удалить"};
    private int tool = 0;

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

    // ══ УБИРАЕМ БЛЮР ПОЛНОСТЬЮ ══
    @Override
    public boolean blursBackground() {
        return false;
    }

    // В 1.21.5 этот метод принимает именно такие параметры

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, BG);
        renderCanvas(ctx, mx, my);
        renderTopBar(ctx, mx, my);
        renderLeftPanel(ctx, mx, my);
        renderStatusBar(ctx, mx, my);
        if (activePanel == Panel.EDIT_NODE && editingNode != null) renderEditPanel(ctx, mx, my);
        if (activePanel == Panel.ITEM_PICKER) renderItemPicker(ctx, mx, my);
        super.render(ctx, mx, my, delta);
    }

    private void renderTopBar(DrawContext ctx, int mx, int my) {
        ctx.fill(0, 0, width, TH, PANELDARK);
        ctx.fill(0, TH-1, width, TH, BORDER);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("✦ CRAFTSI ✦"), LW/2, 10, YELLOW);
        int x = LW + 8;
        x = tBtn(ctx, "+ Нода", x, mx, my, GREEND, GREEN) + 6;
        tBtn(ctx, "⚙ Настройки", x, mx, my, PANELDARK, BORDERL);
        ctx.drawTextWithShadow(textRenderer, Text.literal("⊕ "+(int)(zoom*100)+"%"), width-55, 10, GRAY);
    }

    private int tBtn(DrawContext ctx, String lbl, int x, int mx, int my, int col, int bcol) {
        int w = textRenderer.getWidth(lbl)+12, h=20, y=4;
        boolean hov = mx>=x&&mx<=x+w&&my>=y&&my<=y+h;
        ctx.fill(x, y, x+w, y+h, hov ? lighter(col,30):col);
        ctx.drawBorder(x, y, w, h, bcol);
        ctx.drawTextWithShadow(textRenderer, Text.literal(lbl), x+6, y+7, WHITE);
        return x+w;
    }

    private void renderLeftPanel(DrawContext ctx, int mx, int my) {
        ctx.fill(0, TH, LW, height-BH, PANELDARK);
        ctx.fill(LW-1, TH, LW, height-BH, BORDER);
        for (int i=0; i<TOOLS.length; i++) {
            int ty=TH+8+i*24;
            boolean hov=mx>=4&&mx<=LW-4&&my>=ty&&my<=ty+20;
            boolean act=tool==i;
            ctx.fill(4,ty,LW-4,ty+20, act?BLUED:(hov?NODEHOV:PANELDARK));
            ctx.drawBorder(4,ty,LW-8,20, act?BLUE:BORDER);
            ctx.drawTextWithShadow(textRenderer, Text.literal(TOOLS[i]), 8, ty+7, act?WHITE:LGRAY);
        }
        int sy=TH+8+TOOLS.length*24+12;
        ctx.fill(4,sy,LW-4,sy+1,BORDER);
        int nodes=NodeGraph.getInstance().getNodes().size();
        long auto=NodeGraph.getInstance().getNodes().stream().filter(n->n.autoCraftOn).count();
        ctx.drawTextWithShadow(textRenderer,Text.literal("Нод: "+nodes),6,sy+6,GRAY);
        ctx.drawTextWithShadow(textRenderer,Text.literal("Авто: "+auto),6,sy+18,auto>0?GREEN:GRAY);
    }

    private void renderStatusBar(DrawContext ctx, int mx, int my) {
        int y=height-BH;
        ctx.fill(0,y,width,height,PANELDARK);
        ctx.fill(0,y,width,y+1,BORDER);
        long total=NodeGraph.getInstance().getNodes().stream().mapToLong(n->n.craftCount).sum();
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("  Нод: "+NodeGraph.getInstance().getNodes().size()
                +"   Связей: "+NodeGraph.getInstance().getConnections().size()
                +"   Создано: "+total
                +"   Масштаб: "+(int)(zoom*100)+"%"),
            LW+5, y+6, GRAY);
    }

    private void renderCanvas(DrawContext ctx, int mx, int my) {
        int cx=LW, cy=TH, cw=width-LW, ch=height-TH-BH;
        ctx.fill(cx,cy,cx+cw,cy+ch,CANVAS);
        ctx.enableScissor(cx,cy,cx+cw,cy+ch);
        int gs=Math.max(8,(int)(32*zoom));
        int ox=((canvasOffX%gs)+gs)%gs, oy=((canvasOffY%gs)+gs)%gs;
        for (int gx=ox; gx<cw; gx+=gs) ctx.fill(cx+gx,cy,cx+gx+1,cy+ch,0xFF111320);
        for (int gy=oy; gy<ch; gy+=gs) ctx.fill(cx,cy+gy,cx+cw,cy+gy+1,0xFF111320);
        for (String[] c : NodeGraph.getInstance().getConnections()) {
            RecipeNode a=NodeGraph.getInstance().findById(c[0]);
            RecipeNode b=NodeGraph.getInstance().findById(c[1]);
            if (a==null||b==null) continue;
            bezier(ctx,sx(a.x+a.w),sy(a.y+a.h/2),sx(b.x),sy(b.y+b.h/2),LINE);
        }
        if (connectFrom!=null)
            bezier(ctx,sx(connectFrom.x+connectFrom.w),sy(connectFrom.y+connectFrom.h/2),mx,my,YELLOW);
        for (RecipeNode n : NodeGraph.getInstance().getNodes()) drawNode(ctx,n,mx,my);
        ctx.disableScissor();
    }

    private void drawNode(DrawContext ctx, RecipeNode n, int mx, int my) {
        int x=sx(n.x),y=sy(n.y),w=(int)(n.w*zoom),h=(int)(n.h*zoom);
        int HH=Math.max(18,(int)(18*zoom));
        boolean hov=mx>=x&&mx<=x+w&&my>=y&&my<=y+h;
        ctx.fill(x+3,y+4,x+w+3,y+h+4,0x44000000);
        ctx.fill(x,y,x+w,y+h, hov?NODEHOV:NODEBG);
        ctx.drawBorder(x,y,w,h, n.autoCraftOn?(n.isActive?GREEN:RED):BORDERL);
        ctx.fill(x,y,x+w,y+HH, n.autoCraftOn?(n.isActive?HDRON:HDROFF):0xFF101220);
        ctx.fill(x,y+HH,x+w,y+HH+1,BORDER);
        String title=fit(n.id,w-60);
        ctx.drawTextWithShadow(textRenderer,Text.literal(title),x+5,y+5,WHITE);
        String badge=n.autoCraftOn?(n.isActive?"●ВКЛ":"●СТОП"):"○ВЫКЛ";
        int bc=n.autoCraftOn?(n.isActive?GREEN:RED):GRAY;
        ctx.drawTextWithShadow(textRenderer,Text.literal(badge),x+w-textRenderer.getWidth(badge)-4,y+5,bc);
        if (zoom>=0.55f) {
            int iy=y+HH+3;
            if (!n.result.isEmpty()) {
                ctx.drawItem(n.result,x+4,iy);
                ctx.drawTextWithShadow(textRenderer,
                    Text.literal("→ "+fit(n.result.getItem().toString().replace("minecraft:",""),w-30)),
                    x+22,iy+5,LGRAY);
            } else {
                ctx.drawTextWithShadow(textRenderer,Text.literal("→ нет рецепта"),x+4,iy+5,GRAY);
            }
            int shown=0;
            for (int i=0;i<9&&shown<6;i++) {
                ItemStack s=n.inputs[i];
                if (s!=null&&!s.isEmpty()) { ctx.drawItem(s,x+4+shown*18,y+HH+22); shown++; }
            }
            if (n.craftCount>0)
                ctx.drawTextWithShadow(textRenderer,Text.literal("✔ "+n.craftCount),x+4,y+h-24,GREEN);
            if (n.autoCraftOn&&!n.isActive) {
                MinecraftClient mc=MinecraftClient.getInstance();
                if (mc.player!=null) {
                    String miss=CraftExecutor.getMissingInfo(n,mc.player);
                    if (!miss.isEmpty())
                        ctx.drawTextWithShadow(textRenderer,Text.literal("✘ "+fit(miss,w-8)),x+4,y+h-14,RED);
                }
            }
            int by=y+h-12,bx=x+4,bw=w-8;
            boolean bh=mx>=bx&&mx<=bx+bw&&my>=by&&my<=by+10;
            ctx.fill(bx,by,bx+bw,by+10, n.autoCraftOn?(bh?0xFF005522:GREEND):(bh?0xFF1A1A33:0xFF101020));
            ctx.drawBorder(bx,by,bw,10, n.autoCraftOn?GREEN:BORDER);
            ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal(n.autoCraftOn?"⚙ Авто-крафт: ВКЛ":"⚙ Авто-крафт: ВЫКЛ"),
                bx+bw/2,by+2,WHITE);
        }
        ctx.fill(x-4,y+h/2-4,x+4,y+h/2+4,PORT);
        ctx.fill(x+w-4,y+h/2-4,x+w+4,y+h/2+4,PORT);
        ctx.fill(x+w-6,y+h-6,x+w,y+h,BORDERL);
    }

    private void renderEditPanel(DrawContext ctx, int mx, int my) {
        int pw=410,ph=330,px=(width-pw)/2,py=(height-ph)/2;
        ctx.fill(px,py,px+pw,py+ph,PANELBG);
        ctx.drawBorder(px,py,pw,ph,BLUE);
        ctx.fill(px,py,px+pw,py+22,PANELDARK);
        ctx.fill(px,py+22,px+pw,py+23,BORDER);
        ctx.drawTextWithShadow(textRenderer,Text.literal("✦ Узел: "+editingNode.id),px+8,py+7,WHITE);
        sBtn(ctx,"✕",px+pw-20,py+4,16,14,mx,my,RED,RED);
        ctx.drawTextWithShadow(textRenderer,Text.literal("Рецепт крафта:"),px+8,py+26,GRAY);
        for (int i=0;i<9;i++) {
            int col=i%3,row=i/3,slx=px+8+col*46,sly=py+38+row*46;
            boolean sel=editingSlot==i;
            ctx.fill(slx,sly,slx+42,sly+42, sel?0xFF0A2010:PANELDARK);
            ctx.drawBorder(slx,sly,42,42, sel?GREEN:BORDER);
            ItemStack s=editingNode.inputs[i];
            if (s!=null&&!s.isEmpty()) {
                ctx.drawItem(s,slx+4,sly+4);
                ctx.drawTextWithShadow(textRenderer,Text.literal("×"+s.getCount()),slx+28,sly+30,YELLOW);
            } else {
                ctx.drawCenteredTextWithShadow(textRenderer,Text.literal(String.valueOf(i+1)),slx+21,sly+17,GRAY);
            }
        }
        ctx.drawCenteredTextWithShadow(textRenderer,Text.literal("→"),px+152,py+84,YELLOW);
        int rx2=px+162,ry2=py+70;
        boolean rSel=editingSlot==9;
        ctx.fill(rx2,ry2,rx2+42,ry2+42, rSel?0xFF0A2010:PANELDARK);
        ctx.drawBorder(rx2,ry2,42,42, rSel?GREEN:BORDER);
        if (!editingNode.result.isEmpty()) {
            ctx.drawItem(editingNode.result,rx2+4,ry2+4);
            ctx.drawTextWithShadow(textRenderer,Text.literal("×"+editingNode.result.getCount()),rx2+28,ry2+30,YELLOW);
        } else {
            ctx.drawCenteredTextWithShadow(textRenderer,Text.literal("?"),rx2+21,ry2+17,GRAY);
        }
        boolean aOn=editingNode.autoCraftOn;
        ctx.fill(px+8,py+182,px+202,py+196, aOn?GREEND:REDD);
        ctx.drawBorder(px+8,py+182,194,14, aOn?GREEN:RED);
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal(aOn?"⚙ Авто-крафт: ВКЛ ✔":"⚙ Авто-крафт: ВЫКЛ ✘"),px+105,py+185,WHITE);
        ctx.drawTextWithShadow(textRenderer,Text.literal("Создано: "+editingNode.craftCount),px+8,py+202,GRAY);
        ctx.fill(px+215,py+22,px+216,py+ph,BORDER);
        int rx=px+222;
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("Слот: "+(editingSlot<0?"—":editingSlot==9?"Результат":"#"+(editingSlot+1))),
            rx,py+26,GRAY);
        ctx.fill(rx,py+40,rx+166,py+54,PANELDARK);
        ctx.drawBorder(rx,py+40,166,14, editingSlot>=0?GREEN:BORDER);
        ctx.drawTextWithShadow(textRenderer,
            Text.literal(inputBuffer.isEmpty()?"Введи (рус/англ)...":inputBuffer+"|"),
            rx+4,py+43, inputBuffer.isEmpty()?GRAY:WHITE);
        ctx.drawTextWithShadow(textRenderer,Text.literal("напр: палка / stick / oak_log"),rx,py+58,GRAY);
        ctx.drawTextWithShadow(textRenderer,Text.literal("Кол-во:"),rx,py+74,LGRAY);
        ctx.fill(rx,py+84,rx+40,py+98,PANELDARK);
        ctx.drawBorder(rx,py+84,40,14,BORDER);
        ctx.drawCenteredTextWithShadow(textRenderer,Text.literal(String.valueOf(editingCount)),rx+20,py+87,WHITE);
        sBtn(ctx,"▲",rx+44,py+84,16,14,mx,my,PANELDARK,BORDERL);
        sBtn(ctx,"▼",rx+64,py+84,16,14,mx,my,PANELDARK,BORDERL);
        sBtn(ctx,"✔ Применить",rx,py+106,166,16,mx,my,GREEND,GREEN);
        sBtn(ctx,"✕ Очистить слот",rx,py+126,166,16,mx,my,REDD,RED);
        sBtn(ctx,"🔍 Выбрать из списка",rx,py+146,166,16,mx,my,BLUED,BLUE);
        ctx.fill(rx,py+168,rx+166,py+169,BORDER);
        ctx.drawTextWithShadow(textRenderer,Text.literal("Инвентарь:"),rx,py+174,GRAY);
        MinecraftClient mc=MinecraftClient.getInstance();
        if (mc.player!=null) {
            int shown=0;
            for (int i=0;i<mc.player.getInventory().size()&&shown<8;i++) {
                ItemStack inv=mc.player.getInventory().getStack(i);
                if (!inv.isEmpty()) {
                    int ix2=rx+shown*20,iy2=py+184;
                    ctx.fill(ix2,iy2,ix2+18,iy2+18,PANELDARK);
                    ctx.drawBorder(ix2,iy2,18,18,BORDER);
                    ctx.drawItem(inv,ix2+1,iy2+1);
                    shown++;
                }
            }
        }
        sBtn(ctx,"Сохранить",px+8,py+ph-22,100,16,mx,my,GREEND,GREEN);
        sBtn(ctx,"Отмена",px+114,py+ph-22,80,16,mx,my,PANELDARK,BORDERL);
    }

    private void renderItemPicker(DrawContext ctx, int mx, int my) {
        int pw=430,ph=330,px=(width-pw)/2,py=(height-ph)/2;
        hoveredPickerItem=null;
        ctx.fill(px,py,px+pw,py+ph,PANELBG);
        ctx.drawBorder(px,py,pw,ph,BLUE);
        ctx.fill(px,py,px+pw,py+22,PANELDARK);
        ctx.fill(px,py+22,px+pw,py+23,BORDER);
        ctx.drawTextWithShadow(textRenderer,Text.literal("🔍 Выбор предмета"),px+8,py+7,WHITE);
        sBtn(ctx,"✕",px+pw-20,py+4,16,14,mx,my,RED,RED);
        ctx.fill(px+4,py+26,px+pw-4,py+40,PANELDARK);
        ctx.drawBorder(px+4,py+26,pw-8,14,BLUE);
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("🔍 "+(searchQuery.isEmpty()?"Поиск рус/англ...":searchQuery+"|")),
            px+8,py+29, searchQuery.isEmpty()?GRAY:WHITE);
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("Найдено: "+filteredItems.size()),px+pw-70,py+29,GRAY);
        int rows=(ph-80)/PSLOT;
        int startIdx=pickerScroll*PCOLS;
        ctx.enableScissor(px+4,py+44,px+pw-4,py+44+rows*PSLOT);
        for (int i=0;i<filteredItems.size()-startIdx;i++) {
            int col=i%PCOLS,row=i/PCOLS;
            if (row>=rows) break;
            Item item=filteredItems.get(startIdx+i);
            int ix=px+4+col*PSLOT,iy=py+44+row*PSLOT;
            boolean hov=mx>=ix&&mx<=ix+PSLOT-2&&my>=iy&&my<=iy+PSLOT-2;
            if (hov) hoveredPickerItem=item;
            ctx.fill(ix,iy,ix+PSLOT-2,iy+PSLOT-2, hov?0xFF0A1E3A:PANELDARK);
            ctx.drawBorder(ix,iy,PSLOT-2,PSLOT-2, hov?BLUE:BORDER);
            ctx.drawItem(new ItemStack(item),ix+3,iy+3);
            String nm=item.toString().replace("minecraft:","");
            if (nm.length()>5) nm=nm.substring(0,5);
            ctx.drawCenteredTextWithShadow(textRenderer,Text.literal(nm),ix+PSLOT/2-1,iy+PSLOT-13,GRAY);
        }
        ctx.disableScissor();
        if (hoveredPickerItem!=null)
            ctx.drawTooltip(textRenderer,
                Text.literal(hoveredPickerItem.toString().replace("minecraft:","")),mx,my);
        int totalRows=(int)Math.ceil(filteredItems.size()/(double)PCOLS);
        if (totalRows>rows) {
            int trackH=rows*PSLOT;
            int thumbH=Math.max(10,(int)((float)rows/totalRows*trackH));
            int thumbY=py+44+(int)((float)pickerScroll/Math.max(1,totalRows-rows)*(trackH-thumbH));
            ctx.fill(px+pw-6,py+44,px+pw-2,py+44+trackH,PANELDARK);
            ctx.fill(px+pw-6,thumbY,px+pw-2,thumbY+thumbH,BORDERL);
        }
        ctx.fill(px,py+ph-22,px+pw,py+ph,PANELDARK);
        ctx.fill(px,py+ph-22,px+pw,py+ph-21,BORDER);
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("ЛКМ — выбрать   Скролл — листать   ESC — назад"),
            px+8,py+ph-14,GRAY);
    }

    @Override
    public boolean mouseClicked(double mx0, double my0, int btn) {
        int mx=(int)mx0,my=(int)my0;
        if (activePanel==Panel.ITEM_PICKER) return pickerClick(mx,my,btn);
        if (activePanel==Panel.EDIT_NODE)   return editClick(mx,my,btn);
        if (my<TH)         return topClick(mx,my,btn);
        if (mx<LW)         return leftClick(mx,my,btn);
        if (my>=height-BH) return true;
        return canvasClick(mx,my,btn);
    }

    private boolean topClick(int mx,int my,int btn) {
        int x=LW+8, w=textRenderer.getWidth("+ Нода")+12;
        if (mx>=x&&mx<=x+w&&my>=4&&my<=24) { addNode(); return true; }
        return true;
    }

    private boolean leftClick(int mx,int my,int btn) {
        for (int i=0;i<TOOLS.length;i++) {
            int ty=TH+8+i*24;
            if (mx>=4&&mx<=LW-4&&my>=ty&&my<=ty+20) { tool=i; return true; }
        }
        return true;
    }

    private boolean canvasClick(int mx,int my,int btn) {
        List<RecipeNode> nodes=NodeGraph.getInstance().getNodes();
        for (int i=nodes.size()-1;i>=0;i--) {
            RecipeNode n=nodes.get(i);
            int x=sx(n.x),y=sy(n.y),w=(int)(n.w*zoom),h=(int)(n.h*zoom);
            int HH=Math.max(18,(int)(18*zoom));
            if (mx<x-6||mx>x+w+6||my<y||my>y+h) continue;
            if (btn==1) { NodeGraph.getInstance().removeNode(n.id); return true; }
            if (mx>=x+w-6&&mx<=x+w+6&&my>=y+h/2-6&&my<=y+h/2+6) { connectFrom=n; return true; }
            if (mx>=x-6&&mx<=x+6&&my>=y+h/2-6&&my<=y+h/2+6) {
                if (connectFrom!=null&&!connectFrom.id.equals(n.id)) {
                    NodeGraph.getInstance().addConnection(connectFrom.id,n.id);
                    connectFrom=null; return true;
                }
            }
            int by=y+h-12;
            if (mx>=x+4&&mx<=x+w-4&&my>=by&&my<=by+10) { n.autoCraftOn=!n.autoCraftOn; return true; }
            if (mx>=x+w-8&&my>=y+h-8) {
                resizeNode=n;resizeStartW=n.w;resizeStartH=n.h;
                resizeMouseX=mx;resizeMouseY=my; return true;
            }
            if (my>=y&&my<=y+HH) { dragNode=n;dragOffX=mx-x;dragOffY=my-y; return true; }
            if (my>y+HH&&my<by) {
                editingNode=n;editingSlot=-1;inputBuffer="";editingCount=1;
                activePanel=Panel.EDIT_NODE; return true;
            }
        }
        connectFrom=null;
        draggingCanvas=true;
        canvasDragStartX=mx-canvasOffX;
        canvasDragStartY=my-canvasOffY;
        return true;
    }

    private boolean editClick(int mx,int my,int btn) {
        int pw=410,ph=330,px=(width-pw)/2,py=(height-ph)/2;
        int rx=px+222;
        if ((mx>=px+pw-20&&mx<=px+pw-4&&my>=py+4&&my<=py+18)||
            (mx>=px+114&&mx<=px+194&&my>=py+ph-22&&my<=py+ph-6)) {
            activePanel=Panel.NONE; return true;
        }
        if (mx>=px+8&&mx<=px+108&&my>=py+ph-22&&my<=py+ph-6) { activePanel=Panel.NONE; return true; }
        for (int i=0;i<9;i++) {
            int col=i%3,row=i/3,slx=px+8+col*46,sly=py+38+row*46;
            if (mx>=slx&&mx<=slx+42&&my>=sly&&my<=sly+42) { editingSlot=i;inputBuffer=""; return true; }
        }
        if (mx>=px+162&&mx<=px+204&&my>=py+70&&my<=py+112) { editingSlot=9;inputBuffer=""; return true; }
        if (mx>=px+8&&mx<=px+202&&my>=py+182&&my<=py+196) { editingNode.autoCraftOn=!editingNode.autoCraftOn; return true; }
        if (mx>=rx+44&&mx<=rx+60&&my>=py+84&&my<=py+98) { editingCount=Math.min(64,editingCount+1); return true; }
        if (mx>=rx+64&&mx<=rx+80&&my>=py+84&&my<=py+98) { editingCount=Math.max(1,editingCount-1); return true; }
        if (mx>=rx&&mx<=rx+166&&my>=py+106&&my<=py+122) { applyInput(); return true; }
        if (mx>=rx&&mx<=rx+166&&my>=py+126&&my<=py+142) { clearSlot(); return true; }
        if (mx>=rx&&mx<=rx+166&&my>=py+146&&my<=py+162) { activePanel=Panel.ITEM_PICKER;rebuildFilter(); return true; }
        return true;
    }

    private boolean pickerClick(int mx,int my,int btn) {
        int pw=430,ph=330,px=(width-pw)/2,py=(height-ph)/2;
        if (mx>=px+pw-20&&mx<=px+pw-4&&my>=py+4&&my<=py+18) { activePanel=Panel.EDIT_NODE; return true; }
        int rows=(ph-80)/PSLOT, startIdx=pickerScroll*PCOLS;
        for (int i=0;i<filteredItems.size()-startIdx;i++) {
            int col=i%PCOLS,row=i/PCOLS;
            if (row>=rows) break;
            int ix=px+4+col*PSLOT,iy=py+44+row*PSLOT;
            if (mx>=ix&&mx<=ix+PSLOT-2&&my>=iy&&my<=iy+PSLOT-2) {
                applyItem(filteredItems.get(startIdx+i));
                activePanel=Panel.EDIT_NODE; return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx0,double my0,int btn,double dx,double dy) {
        int mx=(int)mx0,my=(int)my0;
        if (dragNode!=null) {
            dragNode.x=(int)((mx-LW-canvasOffX-dragOffX)/zoom);
            dragNode.y=(int)((my-TH-canvasOffY-dragOffY)/zoom);
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
        return super.mouseDragged(mx0,my0,btn,dx,dy);
    }

    @Override
    public boolean mouseReleased(double mx,double my,int btn) {
        dragNode=null;resizeNode=null;draggingCanvas=false;
        return super.mouseReleased(mx,my,btn);
    }

    @Override
    public boolean mouseScrolled(double mx,double my,double h,double v) {
        if (activePanel==Panel.ITEM_PICKER) {
            int pw=430,ph=330,px=(width-pw)/2,py=(height-ph)/2;
            if (mx>=px&&mx<=px+pw&&my>=py&&my<=py+ph) {
                int rows=(ph-80)/PSLOT;
                int maxS=(int)Math.ceil(filteredItems.size()/(double)PCOLS)-rows;
                pickerScroll=Math.max(0,Math.min(maxS,pickerScroll-(int)v));
                return true;
            }
        }
        if (activePanel==Panel.NONE&&mx>LW&&my>TH&&my<height-BH) {
            zoom=Math.max(0.3f,Math.min(2.5f,zoom+(float)v*0.1f));
            return true;
        }
        return super.mouseScrolled(mx,my,h,v);
    }

    @Override
    public boolean keyPressed(int key,int scan,int mods) {
        if (activePanel!=Panel.NONE) {
            if (key==259) {
                if (activePanel==Panel.ITEM_PICKER&&!searchQuery.isEmpty()) {
                    searchQuery=searchQuery.substring(0,searchQuery.length()-1); rebuildFilter();
                } else if (!inputBuffer.isEmpty()) {
                    inputBuffer=inputBuffer.substring(0,inputBuffer.length()-1);
                }
                return true;
            }
            if (key==257||key==335) { applyInput(); return true; }
            if (key==256) {
                if (activePanel==Panel.ITEM_PICKER) activePanel=Panel.EDIT_NODE;
                else activePanel=Panel.NONE;
                return true;
            }
            return true;
        }
        if (key==256) { this.close(); return true; }
        return super.keyPressed(key,scan,mods);
    }

    @Override
    public boolean charTyped(char c,int mods) {
        if (activePanel==Panel.ITEM_PICKER) { searchQuery+=c; rebuildFilter(); return true; }
        if (activePanel==Panel.EDIT_NODE&&editingSlot>=0) { inputBuffer+=c; return true; }
        return false;
    }

    private void applyInput() {
        if (editingNode==null||editingSlot<0||inputBuffer.isEmpty()) return;
        String raw=inputBuffer.trim().toLowerCase();
        String id=null;
        for (Map.Entry<String,String> e:RU.entrySet())
            if (raw.equals(e.getKey())||raw.contains(e.getKey())) { id="minecraft:"+e.getValue(); break; }
        if (id==null) id=raw.contains(":")?raw:"minecraft:"+raw;
        try {
            Item item=Registries.ITEM.get(Identifier.of(id));
            if (item==Items.AIR) {
                for (Item it:allItems) if (it.toString().contains(raw)) { item=it; break; }
            }
            if (item==Items.AIR) return;
            applyItem(item);
            inputBuffer=""; editingSlot=-1;
        } catch(Exception ignored){}
    }

    private void applyItem(Item item) {
        if (editingNode==null||editingSlot<0) return;
        ItemStack s=new ItemStack(item,editingCount);
        if (editingSlot==9) editingNode.result=s;
        else editingNode.inputs[editingSlot]=s;
    }

    private void clearSlot() {
        if (editingNode==null||editingSlot<0) return;
        if (editingSlot==9) editingNode.result=ItemStack.EMPTY;
        else editingNode.inputs[editingSlot]=ItemStack.EMPTY;
        inputBuffer="";
    }

    private void addNode() {
        int c=NodeGraph.getInstance().getNodes().size();
        RecipeNode n=new RecipeNode("Рецепт "+(c+1));
        n.x=60+(c*200)%600; n.y=60+(c/4)*140;
        n.w=180; n.h=110;
        NodeGraph.getInstance().addNode(n);
    }

    private void rebuildFilter() {
        filteredItems.clear();
        String q=searchQuery.toLowerCase();
        String eng=null;
        if (!q.isEmpty()) for (Map.Entry<String,String> e:RU.entrySet())
            if (q.contains(e.getKey())) { eng=e.getValue(); break; }
        final String fq=eng!=null?eng:q;
        for (Item it:allItems) if (fq.isEmpty()||it.toString().contains(fq)) filteredItems.add(it);
        pickerScroll=0;
    }

    private void bezier(DrawContext ctx,int x1,int y1,int x2,int y2,int col) {
        int steps=Math.max(20,Math.abs(x2-x1)/2);
        int cx1=x1+(x2-x1)/3,cx2=x2-(x2-x1)/3;
        for (int i=0;i<=steps;i++) {
            float t=i/(float)steps,u=1-t;
            int px=(int)(u*u*u*x1+3*u*u*t*cx1+3*u*t*t*cx2+t*t*t*x2);
            int py=(int)(u*u*u*y1+3*u*u*t*y1+3*u*t*t*y2+t*t*t*y2);
            ctx.fill(px,py,px+2,py+2,col);
        }
    }

    private void sBtn(DrawContext ctx,String lbl,int x,int y,int w,int h,
                      int mx,int my,int col,int bc) {
        boolean hov=mx>=x&&mx<=x+w&&my>=y&&my<=y+h;
        ctx.fill(x,y,x+w,y+h, hov?lighter(col,25):col);
        ctx.drawBorder(x,y,w,h,bc);
        ctx.drawCenteredTextWithShadow(textRenderer,Text.literal(lbl),x+w/2,y+(h-7)/2,WHITE);
    }

    private int sx(int nx){return LW+canvasOffX+(int)(nx*zoom);}
    private int sy(int ny){return TH+canvasOffY+(int)(ny*zoom);}

    private String fit(String s,int px2) {
        if (textRenderer.getWidth(s)<=px2) return s;
        while (s.length()>1&&textRenderer.getWidth(s+"…")>px2) s=s.substring(0,s.length()-1);
        return s+"…";
    }

    private int lighter(int c,int a) {
        return (c&0xFF000000)|
            (Math.min(255,((c>>16)&0xFF)+a)<<16)|
            (Math.min(255,((c>>8)&0xFF)+a)<<8)|
            Math.min(255,(c&0xFF)+a);
    }

    @Override public boolean shouldPause(){return false;}
    @Override public boolean shouldCloseOnEsc(){return true;}
}
