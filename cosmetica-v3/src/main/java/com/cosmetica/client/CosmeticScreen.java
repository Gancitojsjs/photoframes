package com.cosmetica.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.List;

public class CosmeticScreen extends Screen {
    private static final int W = 470, H = 268, COLS = 2, ROWS = 5, CW = 96, CH = 34;
    private static int tab = 0, page = 0;
    private static String selected = null, query = "";
    private int x0, y0, pages = 1;
    private boolean refocus = false;
    private TextFieldWidget search;

    public CosmeticScreen() { super(Text.literal("Cosm\u00e9ticos")); }

    /** Opens the menu on a given slot with a cosmetic selected (used after adding a model). */
    public static void open(String slot, String key) {
        int i = CosmeticRegistry.SLOTS.indexOf(slot);
        if (i >= 0) tab = i;
        page = 0; query = ""; selected = key;
        MinecraftClient.getInstance().setScreen(new CosmeticScreen());
    }

    private void updatePreview() {
        EquippedCosmetics.PREVIEW.clear();
        CosmeticDef d = selected == null ? null : CosmeticRegistry.get(selected);
        if (d != null) EquippedCosmetics.PREVIEW.put(d.slot(), d.key());
    }

    private static String rarityLabel(String r) {
        return switch (r) {
            case "rare" -> "Raro"; case "epic" -> "\u00c9pico"; case "legendary" -> "Legendario";
            case "import" -> "Importado"; default -> "Com\u00fan";
        };
    }

    private List<CosmeticDef> filtered() {
        String q = query.toLowerCase();
        return CosmeticRegistry.bySlot(CosmeticRegistry.SLOTS.get(tab)).stream()
            .filter(d -> q.isEmpty() || d.name().toLowerCase().contains(q)).toList();
    }

    @Override
    protected void init() {
        x0 = (width - W) / 2; y0 = (height - H) / 2;
        updatePreview();

        // top-right tools
        FlatButton add = new FlatButton(x0 + W - 262, y0 + 5, 100, 14, "+ A\u00f1adir modelo", () -> client.setScreen(new ModelBrowserScreen(this)));
        add.accent = 0x43A047;
        addDrawableChild(add);
        FlatButton folder = new FlatButton(x0 + W - 158, y0 + 5, 82, 14, "Carpeta modelos", () -> {
            try { Util.getOperatingSystem().open(ImportedModels.DIR.toFile()); } catch (Exception ignored) {}
        });
        folder.accent = 0x55FF99;
        FlatButton reload = new FlatButton(x0 + W - 74, y0 + 5, 66, 14, "Recargar", () -> { CosmeticRegistry.reloadImports(); clearAndInit(); });
        reload.accent = 0x55FF99;
        addDrawableChild(folder); addDrawableChild(reload);

        // sidebar tabs
        for (int i = 0; i < CosmeticRegistry.SLOTS.size(); i++) {
            final int idx = i;
            int n = CosmeticRegistry.bySlot(CosmeticRegistry.SLOTS.get(i)).size();
            FlatButton t = new FlatButton(x0 + 8, y0 + 30 + i * 28, 68, 26, CosmeticRegistry.LABELS.get(i), () -> {
                tab = idx; page = 0; selected = null; clearAndInit();
            });
            t.line2 = n + " items"; t.selected = i == tab; t.accent = i == tab ? 0xFFB02E : 0x5C6BC0;
            addDrawableChild(t);
        }
        FlatButton clear = new FlatButton(x0 + 8, y0 + 30 + CosmeticRegistry.SLOTS.size() * 28 + 4, 68, 20, "Quitar todo", () -> { EquippedCosmetics.clearAll(); clearAndInit(); });
        clear.accent = 0xE53935;
        addDrawableChild(clear);

        // search
        search = new TextFieldWidget(textRenderer, x0 + 84, y0 + 30, 196, 16, Text.literal("Buscar"));
        search.setText(query);
        search.setSuggestion(query.isEmpty() ? "Buscar..." : null);
        search.setChangedListener(s -> { query = s; page = 0; refocus = true; clearAndInit(); });
        addDrawableChild(search);
        if (refocus) { setFocused(search); search.setFocused(true); refocus = false; }

        // cards
        List<CosmeticDef> list = filtered();
        pages = Math.max(1, (list.size() + COLS * ROWS - 1) / (COLS * ROWS));
        if (page >= pages) page = pages - 1;
        for (int i = 0; i < COLS * ROWS && page * COLS * ROWS + i < list.size(); i++) {
            final CosmeticDef d = list.get(page * COLS * ROWS + i);
            int cx = x0 + 84 + (i % COLS) * (CW + 4), cy = y0 + 52 + (i / COLS) * (CH + 4);
            FlatButton c = new FlatButton(cx, cy, CW, CH, d.name(), () -> { selected = d.key(); clearAndInit(); });
            c.accent = CosmeticRegistry.rarityColor(d.rarity());
            c.swatch = d.rgb();
            c.selected = d.key().equals(selected);
            c.line2 = EquippedCosmetics.isEquipped(d) ? "\u00a7a\u25cf Equipado" : rarityLabel(d.rarity());
            c.line2Color = EquippedCosmetics.isEquipped(d) ? 0x55FF55 : c.accent;
            addDrawableChild(c);
        }

        // pager
        FlatButton prev = new FlatButton(x0 + 84, y0 + 248, 30, 14, "<", () -> { page--; clearAndInit(); });
        FlatButton next = new FlatButton(x0 + 250, y0 + 248, 30, 14, ">", () -> { page++; clearAndInit(); });
        prev.active = page > 0; next.active = page < pages - 1;
        addDrawableChild(prev); addDrawableChild(next);

        // equip button
        final CosmeticDef sel = selected == null ? null : CosmeticRegistry.get(selected);
        boolean eqd = sel != null && EquippedCosmetics.isEquipped(sel);
        FlatButton eq = new FlatButton(x0 + 288, y0 + 226, 174, 24, sel == null ? "Elige un cosm\u00e9tico" : (eqd ? "Quitar" : "Equipar"),
            () -> { if (sel != null) { EquippedCosmetics.toggle(sel); clearAndInit(); } });
        eq.accent = eqd ? 0xE53935 : 0x43A047; eq.baseColor = 0xFF22283A; eq.active = sel != null;
        addDrawableChild(eq);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        super.renderBackground(ctx, mx, my, delta);
        ctx.fill(x0 - 2, y0 - 2, x0 + W + 2, y0 + H + 2, 0xFF05060C);
        ctx.fill(x0, y0, x0 + W, y0 + H, 0xF0121422);
        ctx.fill(x0, y0, x0 + W, y0 + 24, 0xFF1A1D33);
        ctx.fill(x0, y0 + 24, x0 + W, y0 + 25, 0xFFFFB02E);
        ctx.fill(x0 + 284, y0 + 30, x0 + 285, y0 + H - 8, 0xFF2A2E47);
        ctx.fill(x0 + 288, y0 + 30, x0 + 462, y0 + 150, 0xFF0A0B14);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        ctx.drawText(textRenderer, "\u00a76\u00a7lCOSM\u00c9TICOS", x0 + 10, y0 + 8, 0xFFFFFF, true);
        int eqCount = EquippedCosmetics.BY_SLOT.size();
        ctx.drawText(textRenderer, "Equipados: " + eqCount, x0 + 100, y0 + 8, 0x8A8FA8, false);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal((page + 1) + " / " + pages), x0 + 182, y0 + 251, 0xB0B4C8);

        if (client != null && client.player != null)
            GuiPreview.render(ctx, x0 + 289, y0 + 31, x0 + 461, y0 + 149, mx, my, client.player);

        CosmeticDef d = selected == null ? null : CosmeticRegistry.get(selected);
        if (d != null) {
            int col = CosmeticRegistry.rarityColor(d.rarity());
            ctx.drawText(textRenderer, d.name(), x0 + 290, y0 + 156, 0xFF000000 | col, true);
            ctx.drawText(textRenderer, rarityLabel(d.rarity()) + " \u2022 " + CosmeticRegistry.LABELS.get(CosmeticRegistry.SLOTS.indexOf(d.slot())),
                x0 + 290, y0 + 167, 0x8A8FA8, false);
            int ty = y0 + 182;
            for (OrderedText line : textRenderer.wrapLines(Text.literal(d.desc()), 170)) {
                if (ty > y0 + 216) break;
                ctx.drawText(textRenderer, line, x0 + 290, ty, 0xC4C8DA, false);
                ty += 10;
            }
        } else {
            ctx.drawText(textRenderer, "Selecciona un cosm\u00e9tico para", x0 + 290, y0 + 160, 0x8A8FA8, false);
            ctx.drawText(textRenderer, "verlo en la vista previa.", x0 + 290, y0 + 171, 0x8A8FA8, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (v < 0 && page < pages - 1) { page++; clearAndInit(); return true; }
        if (v > 0 && page > 0) { page--; clearAndInit(); return true; }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override public void tick() { GuiPreview.tick(); }
    @Override public void removed() { EquippedCosmetics.PREVIEW.clear(); GuiPreview.clear(); }
    @Override public boolean shouldPause() { return false; }
}
