package com.cosmetica.client;

import com.cosmetica.client.CosmeticDef.ParticleSpec;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** The 3D-ish preview box shared by the cosmetics screen and the model browser: player + pet + 2D aura sprites. */
public final class GuiPreview {
    private static final class P { float x, y, vx, vy, size, alpha, base; int age, life; Identifier tex; }
    private static final List<P> PARTS = new ArrayList<>();
    private static final Random RND = new Random();
    private static int ticks = 0;
    private static float cx, feetY, ppb = 52f;

    public static void clear() { PARTS.clear(); }

    /** call from Screen.tick() */
    public static void tick() {
        ticks++;
        for (Iterator<P> it = PARTS.iterator(); it.hasNext(); ) {
            P p = it.next();
            if (p.age++ >= p.life) { it.remove(); continue; }
            p.x += p.vx; p.y += p.vy;
            float t = (float) p.age / p.life;
            p.alpha = p.base * (1f - t * t);
        }
        for (CosmeticDef d : EquippedCosmetics.effective()) {
            ParticleSpec s = d.particles();
            if (s == null || cx == 0f || ticks % s.interval() != 0) continue;
            for (int i = 0; i < s.count() && PARTS.size() < 600; i++) spawn(s);
        }
    }

    private static void spawn(ParticleSpec s) {
        double a = RND.nextDouble() * Math.PI * 2, r = s.radius() * ppb, sq = Math.sqrt(RND.nextDouble());
        float px = cx, py = feetY - 0.9f * ppb;
        switch (s.shape()) {
            case "sphere" -> {
                double dx = RND.nextGaussian(), dy = RND.nextGaussian();
                double l = Math.sqrt(dx * dx + dy * dy) + 1e-4, rr = r * Math.sqrt(RND.nextDouble());
                px = cx + (float) (dx / l * rr); py = feetY - 0.9f * ppb + (float) (dy / l * rr);
            }
            case "ring" -> { px = cx + (float) (Math.cos(a) * r); py = feetY - (0.1f + RND.nextFloat() * 1.6f) * ppb; }
            case "feet" -> { px = cx + (float) (Math.cos(a) * r * sq); py = feetY; }
            case "head", "above" -> { px = cx + (float) (Math.cos(a) * r * sq); py = feetY - (s.shape().equals("head") ? 1.9f : 2.5f) * ppb; }
            case "chest" -> { px = cx + (RND.nextFloat() - 0.5f) * 0.3f * ppb; py = feetY - 1.15f * ppb; }
            case "back" -> { px = cx + (RND.nextBoolean() ? 1 : -1) * 0.17f * ppb; py = feetY - 0.5f * ppb; }
            case "hand", "staff" -> { px = cx + 0.4f * ppb; py = feetY - (s.shape().equals("staff") ? 2.5f : 0.8f) * ppb; }
            default -> {}
        }
        P p = new P();
        p.x = px; p.y = py;
        p.vx = (float) ((RND.nextDouble() - 0.5) * s.vel() * ppb);
        p.vy = -(float) (s.rise() + (RND.nextDouble() - 0.5) * s.vel()) * ppb;
        p.size = s.size() * 2f * ppb;
        p.life = s.life() + RND.nextInt(5);
        p.base = p.alpha = s.alpha();
        p.tex = ParticleEmitter.texOf(s.sprites().get(RND.nextInt(s.sprites().size())));
        PARTS.add(p);
    }

    /** Draws the preview inside the box x1,y1,x2,y2. */
    public static void render(DrawContext ctx, int x1, int y1, int x2, int y2, int mx, int my, LivingEntity player) {
        render(ctx, x1, y1, x2, y2, mx, my, player, 52);
    }

    public static void render(DrawContext ctx, int x1, int y1, int x2, int y2, int mx, int my, LivingEntity player, int size) {
        InventoryScreen.drawEntity(ctx, x1, y1, x2, y2, size, 0.0625f, mx, my, player);
        cx = (x1 + x2) / 2f;
        feetY = (y1 + y2) / 2f + (player.getHeight() / 2f + 0.0625f) * size;
        ppb = size;

        // pet
        CosmeticDef pet = PetManager.current();
        if (pet != null) {
            try {
                float px = x2 - 38f;
                float tilt = (float) Math.atan((px - mx) / 40.0) * 20f;
                float time = System.currentTimeMillis() / 50f;
                MatrixStack ms = ctx.getMatrices();
                ms.push();
                ms.translate(px, feetY, 100.0);
                ms.scale(size, size, -size);
                ms.multiply(new Quaternionf().rotateZ((float) Math.PI));
                float lift = PetManager.isFloating(pet) ? 1.1f + (float) Math.sin(time * 0.12) * 0.1f : 0f;
                ms.translate(0f, lift, 0f);
                ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-tilt));
                PetManager.draw(ms, ctx.getVertexConsumers(), pet, 0xF000F0, 0f, 0f, time);
                ctx.draw();
                ms.pop();
            } catch (Exception ignored) {}
        }

        // aura sprites (2D)
        if (!PARTS.isEmpty()) {
            ctx.enableScissor(x1, y1, x2, y2);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            for (P p : PARTS) {
                int sz = Math.max(2, Math.round(p.size));
                ctx.setShaderColor(1f, 1f, 1f, Math.max(0f, p.alpha));
                ctx.drawTexture(p.tex, Math.round(p.x - sz / 2f), Math.round(p.y - sz / 2f), sz, sz, 0f, 0f, 16, 16, 16, 16);
            }
            ctx.setShaderColor(1f, 1f, 1f, 1f);
            ctx.disableScissor();
        }
    }
    private GuiPreview() {}
}
