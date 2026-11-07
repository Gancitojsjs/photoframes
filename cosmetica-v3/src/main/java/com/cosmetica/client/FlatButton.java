package com.cosmetica.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

/** Flat, dark-themed button/card used by the cosmetics menu. */
public class FlatButton extends ClickableWidget {
    private final Runnable action;
    public String line1, line2 = "";
    public int accent = 0x5C6BC0, line2Color = 0x8A8FA8, swatch = -1;
    public boolean selected = false;
    public int baseColor = 0xFF1D2033;

    public FlatButton(int x, int y, int w, int h, String line1, Runnable action) {
        super(x, y, w, h, Text.literal(line1));
        this.line1 = line1; this.action = action;
    }

    @Override
    protected void renderWidget(DrawContext ctx, int mx, int my, float delta) {
        var tr = MinecraftClient.getInstance().textRenderer;
        int bg = !active ? 0xFF15161F : selected ? 0xFF2E3350 : isHovered() ? 0xFF2A2E47 : baseColor;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        ctx.fill(x, y, x + w, y + h, bg);
        ctx.fill(x, y, x + 3, y + h, 0xFF000000 | accent);
        if (selected) ctx.fill(x + 3, y + h - 1, x + w, y + h, 0xFF000000 | accent);
        int tx = x + 8;
        int maxW = w - 12 - (swatch >= 0 ? 10 : 0);
        String l1 = tr.trimToWidth(line1, maxW);
        int col = !active ? 0x6A6D80 : selected ? 0xFFE08A : 0xFFFFFF;
        if (line2.isEmpty()) {
            ctx.drawText(tr, l1, tx, y + (h - 8) / 2, col, false);
        } else {
            ctx.drawText(tr, l1, tx, y + 6, col, false);
            ctx.drawText(tr, tr.trimToWidth(line2, w - 12), tx, y + h - 14, line2Color, false);
        }
        if (swatch >= 0) {
            ctx.fill(x + w - 11, y + 5, x + w - 5, y + 11, 0xFF000000);
            ctx.fill(x + w - 10, y + 6, x + w - 6, y + 10, 0xFF000000 | swatch);
        }
    }

    @Override public void onClick(double mx, double my) { if (action != null) action.run(); }
    @Override protected void appendClickableNarrations(NarrationMessageBuilder b) { appendDefaultNarrations(b); }
}
