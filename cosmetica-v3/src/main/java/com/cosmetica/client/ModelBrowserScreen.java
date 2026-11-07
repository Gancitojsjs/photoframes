package com.cosmetica.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * "Add model" page: lists .bbmodel / Java-block models found in Downloads, Desktop and the game folder
 * (or dragged onto the window), previews them live on your character, lets you pick the slot / size / height
 * and installs + equips the model with one click.
 */
public class ModelBrowserScreen extends Screen {
    private static final int W = 470, H = 268, ROWS = 6, RH = 30;
    private static final String[] SLOT_KEYS = {"head", "back", "hand", "pet"};
    private static final String[] SLOT_NAMES = {"Cabeza", "Espalda", "Mano", "Mascota"};

    private record Cand(Path path, String where) {}

    private final Screen parent;
    private final List<Cand> cands = new ArrayList<>();
    private int x0, y0, page = 0, pages = 1;
    private Cand sel;
    private String slot = "head", message = "", messageColor = 0x8A8FA8;
    private double scale = 0.5, offY = 0;
    private boolean flip = false;

    public ModelBrowserScreen(Screen parent) {
        super(Text.literal("A\u00f1adir modelo"));
        this.parent = parent;
        scan();
    }

    // ---------- scanning ----------
    private void scan() {
        cands.clear();
        List<Path> dirs = new ArrayList<>();
        Path home = Path.of(System.getProperty("user.home", "."));
        for (String n : new String[]{"Downloads", "Descargas", "Desktop", "Escritorio"}) dirs.add(home.resolve(n));
        dirs.add(FabricLoader.getInstance().getGameDir().resolve("cosmetica_import"));
        try { Files.createDirectories(FabricLoader.getInstance().getGameDir().resolve("cosmetica_import")); } catch (Exception ignored) {}

        List<Cand> found = new ArrayList<>();
        Set<Path> seen = new HashSet<>();
        for (Path dir : dirs) {
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> s = Files.list(dir)) {
                s.filter(Files::isRegularFile).filter(ImportedModels::looksImportable).forEach(p -> {
                    if (seen.add(p.toAbsolutePath().normalize())) found.add(new Cand(p, dir.getFileName().toString()));
                });
            } catch (Exception ignored) {}
        }
        found.sort((a, b) -> Long.compare(mod(b.path()), mod(a.path())));
        for (int i = 0; i < found.size() && i < 120; i++) cands.add(found.get(i));
    }
    private static long mod(Path p) { try { return Files.getLastModifiedTime(p).toMillis(); } catch (Exception e) { return 0; } }

    /** Drag & drop files onto the window (called by vanilla; no @Override on purpose so a mapping rename can't break the build). */
    public void filesDragged(List<Path> paths) {
        Cand first = null;
        for (Path p : paths) {
            if (!ImportedModels.looksImportable(p)) continue;
            Cand c = new Cand(p, "arrastrado");
            cands.removeIf(o -> o.path().equals(p));
            cands.add(0, c);
            if (first == null) first = c;
        }
        if (first == null) { setMsg("Ese archivo no es un .bbmodel (o .json con su .png).", 0xFF6B6B); }
        else { page = 0; choose(first); }
        clearAndInit();
    }

    // ---------- state ----------
    private void setMsg(String m, int c) { message = m; messageColor = c; }

    private void choose(Cand c) {
        sel = c;
        String n = c.path().getFileName().toString().toLowerCase(Locale.ROOT);
        if (n.contains("pet") || n.contains("mascota")) slot = "pet";
        else if (n.contains("wing") || n.contains("alas") || n.contains("cape") || n.contains("capa") || n.contains("back") || n.contains("jetpack")) slot = "back";
        else if (n.contains("sword") || n.contains("espada") || n.contains("staff") || n.contains("baston") || n.contains("hand") || n.contains("lantern")) slot = "hand";
        else slot = "head";
        for (String s : SLOT_KEYS) if (n.startsWith(s + "_")) slot = s;
        scale = ImportedModels.Opts.defaultScale(slot);
        offY = 0; flip = false;
        rebuild();
    }

    private String niceName() {
        String n = sel.path().getFileName().toString();
        n = n.substring(0, n.lastIndexOf('.'));
        for (String s : SLOT_KEYS) if (n.toLowerCase(Locale.ROOT).startsWith(s + "_")) { n = n.substring(s.length() + 1); break; }
        return n.replace('_', ' ');
    }

    private ImportedModels.Opts opts() {
        return new ImportedModels.Opts(slot, niceName(), scale, new double[]{0, offY, 0}, flip);
    }

    private void rebuild() {
        EquippedCosmetics.PREVIEW.clear();
        CosmeticRegistry.TEMP.clear();
        if (sel == null) return;
        try {
            CosmeticDef d = ImportedModels.build(sel.path(), "_preview", opts());
            CosmeticRegistry.TEMP.put(d.key(), d);
            EquippedCosmetics.PREVIEW.put(d.slot(), d.key());
            setMsg("Vista previa en tu personaje. Ajusta y pulsa Guardar.", 0x8A8FA8);
        } catch (Exception e) {
            setMsg("No se pudo leer: " + e.getMessage(), 0xFF6B6B);
        }
    }

    private void save() {
        if (sel == null) return;
        try {
            String key = ImportedModels.install(sel.path(), opts());
            CosmeticRegistry.reloadImports();
            EquippedCosmetics.BY_SLOT.put(slot, key);
            EquippedCosmetics.save();
            CosmeticScreen.open(slot, key);
        } catch (Exception e) {
            setMsg("No se pudo guardar: " + e.getMessage(), 0xFF6B6B);
        }
    }

    // ---------- UI ----------
    @Override
    protected void init() {
        x0 = (width - W) / 2; y0 = (height - H) / 2;

        FlatButton back = new FlatButton(x0 + W - 74, y0 + 5, 66, 14, "Volver", () -> client.setScreen(new CosmeticScreen()));
        back.accent = 0xFFB02E;
        FlatButton refresh = new FlatButton(x0 + W - 150, y0 + 5, 70, 14, "Actualizar", () -> { scan(); page = 0; clearAndInit(); });
        refresh.accent = 0x55FF99;
        FlatButton folder = new FlatButton(x0 + W - 246, y0 + 5, 92, 14, "Abrir Descargas", () -> {
            try { Util.getOperatingSystem().open(Path.of(System.getProperty("user.home", "."), "Downloads").toFile()); } catch (Exception ignored) {}
        });
        folder.accent = 0x5C6BC0;
        addDrawableChild(back); addDrawableChild(refresh); addDrawableChild(folder);

        // list
        pages = Math.max(1, (cands.size() + ROWS - 1) / ROWS);
        if (page >= pages) page = pages - 1;
        for (int i = 0; i < ROWS && page * ROWS + i < cands.size(); i++) {
            final Cand c = cands.get(page * ROWS + i);
            FlatButton b = new FlatButton(x0 + 8, y0 + 48 + i * (RH + 2), 270, RH, c.path().getFileName().toString(), () -> { choose(c); clearAndInit(); });
            b.line2 = c.where(); b.accent = 0x55FF99; b.selected = sel != null && sel.path().equals(c.path());
            addDrawableChild(b);
        }
        FlatButton prev = new FlatButton(x0 + 8, y0 + 246, 30, 14, "<", () -> { page--; clearAndInit(); });
        FlatButton next = new FlatButton(x0 + 248, y0 + 246, 30, 14, ">", () -> { page++; clearAndInit(); });
        prev.active = page > 0; next.active = page < pages - 1;
        addDrawableChild(prev); addDrawableChild(next);

        // right panel
        int rx = x0 + 288;
        for (int i = 0; i < SLOT_KEYS.length; i++) {
            final String k = SLOT_KEYS[i];
            FlatButton sb = new FlatButton(rx + (i % 2) * 88, y0 + 126 + (i / 2) * 18, 86, 16, SLOT_NAMES[i], () -> {
                slot = k; scale = ImportedModels.Opts.defaultScale(k); rebuild(); clearAndInit();
            });
            sb.selected = k.equals(slot); sb.accent = k.equals(slot) ? 0xFFB02E : 0x5C6BC0; sb.active = sel != null;
            addDrawableChild(sb);
        }
        FlatButton sm = new FlatButton(rx, y0 + 166, 20, 16, "-", () -> { scale = Math.max(0.05, scale - step()); rebuild(); clearAndInit(); });
        FlatButton sp = new FlatButton(rx + 154, y0 + 166, 20, 16, "+", () -> { scale = Math.min(4.0, scale + step()); rebuild(); clearAndInit(); });
        FlatButton om = new FlatButton(rx, y0 + 184, 20, 16, "-", () -> { offY -= 1; rebuild(); clearAndInit(); });
        FlatButton op = new FlatButton(rx + 154, y0 + 184, 20, 16, "+", () -> { offY += 1; rebuild(); clearAndInit(); });
        FlatButton fl = new FlatButton(rx, y0 + 202, 174, 16, flip ? "Girar 180\u00b0: SI" : "Girar 180\u00b0: NO", () -> { flip = !flip; rebuild(); clearAndInit(); });
        for (FlatButton b : new FlatButton[]{sm, sp, om, op, fl}) { b.active = sel != null; addDrawableChild(b); }
        FlatButton save = new FlatButton(rx, y0 + 224, 174, 24, sel == null ? "Elige un modelo" : "Guardar y equipar", this::save);
        save.accent = 0x43A047; save.baseColor = 0xFF22283A; save.active = sel != null;
        addDrawableChild(save);
    }

    private double step() { return scale < 0.5 ? 0.05 : 0.1; }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        super.renderBackground(ctx, mx, my, delta);
        ctx.fill(x0 - 2, y0 - 2, x0 + W + 2, y0 + H + 2, 0xFF05060C);
        ctx.fill(x0, y0, x0 + W, y0 + H, 0xF0121422);
        ctx.fill(x0, y0, x0 + W, y0 + 24, 0xFF1A1D33);
        ctx.fill(x0, y0 + 24, x0 + W, y0 + 25, 0xFF55FF99);
        ctx.fill(x0 + 284, y0 + 30, x0 + 285, y0 + H - 8, 0xFF2A2E47);
        ctx.fill(x0 + 288, y0 + 30, x0 + 462, y0 + 120, 0xFF0A0B14);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        ctx.drawText(textRenderer, "\u00a7a\u00a7lA\u00d1ADIR MODELO", x0 + 10, y0 + 8, 0xFFFFFF, true);
        ctx.drawText(textRenderer, "Arrastra un .bbmodel a la ventana o elige uno:", x0 + 8, y0 + 31, 0xC4C8DA, false);
        ctx.drawText(textRenderer, "Descargas / Escritorio / carpeta cosmetica_import", x0 + 8, y0 + 40, 0x6A6D80, false);
        if (cands.isEmpty()) {
            ctx.drawText(textRenderer, "No encontre modelos. Descarga uno o", x0 + 14, y0 + 110, 0x8A8FA8, false);
            ctx.drawText(textRenderer, "arrastralo aqui (.bbmodel).", x0 + 14, y0 + 121, 0x8A8FA8, false);
        }
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal((page + 1) + " / " + pages), x0 + 143, y0 + 249, 0xB0B4C8);

        if (client != null && client.player != null)
            GuiPreview.render(ctx, x0 + 289, y0 + 31, x0 + 461, y0 + 119, mx, my, client.player, 38);

        int rx = x0 + 288;
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Tama\u00f1o: " + String.format(Locale.ROOT, "%.2f", scale)), rx + 87, y0 + 170, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Altura: " + (int) offY + " px"), rx + 87, y0 + 188, 0xFFFFFF);
        ctx.drawText(textRenderer, textRenderer.trimToWidth(message, 174), rx, y0 + 253, messageColor, false);
    }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (v < 0 && page < pages - 1) { page++; clearAndInit(); return true; }
        if (v > 0 && page > 0) { page--; clearAndInit(); return true; }
        return super.mouseScrolled(mx, my, h, v);
    }

    @Override public void tick() { GuiPreview.tick(); }
    @Override public void removed() {
        EquippedCosmetics.PREVIEW.clear();
        CosmeticRegistry.TEMP.clear();
        GuiPreview.clear();
    }
    @Override public boolean shouldPause() { return false; }
}
