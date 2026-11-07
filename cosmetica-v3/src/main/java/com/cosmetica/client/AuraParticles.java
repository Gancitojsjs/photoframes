package com.cosmetica.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/**
 * Own tiny particle system for auras. It does NOT use the vanilla ParticleManager (custom texture sheets
 * are not reliably rendered there), it simply draws camera-facing quads after the world is rendered.
 */
public final class AuraParticles {
    public static final class P {
        public double x, y, z, px, py, pz, vx, vy, vz;
        public int age, life;
        public float size, alpha, gravity;
        public Identifier tex;
    }

    private static final List<P> LIST = new ArrayList<>();
    private static final int MAX = 3000;

    public static void add(P p) {
        if (LIST.size() < MAX) LIST.add(p);
    }

    public static void clear() { LIST.clear(); }

    public static void tick() {
        for (Iterator<P> it = LIST.iterator(); it.hasNext(); ) {
            P p = it.next();
            p.px = p.x; p.py = p.y; p.pz = p.z;
            if (p.age++ >= p.life) { it.remove(); continue; }
            p.vy -= 0.04 * p.gravity;
            p.x += p.vx; p.y += p.vy; p.z += p.vz;
            p.vx *= 0.98; p.vy *= 0.98; p.vz *= 0.98;
        }
    }

    public static void render(WorldRenderContext ctx) {
        if (LIST.isEmpty()) return;
        Camera cam = ctx.camera();
        Vec3d cp = cam.getPos();
        float td = ctx.tickDelta();
        Quaternionf rot = cam.getRotation();
        Matrix4f mat = ctx.matrixStack().peek().getPositionMatrix();

        Map<Identifier, List<P>> groups = new LinkedHashMap<>();
        for (P p : LIST) groups.computeIfAbsent(p.tex, k -> new ArrayList<>()).add(p);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);

        Tessellator tess = Tessellator.getInstance();
        Vector3f v = new Vector3f();
        float[][] corners = {{-1, -1}, {-1, 1}, {1, 1}, {1, -1}};
        float[][] uvs = {{1, 1}, {1, 0}, {0, 0}, {0, 1}};
        for (Map.Entry<Identifier, List<P>> g : groups.entrySet()) {
            RenderSystem.setShaderTexture(0, g.getKey());
            BufferBuilder b = tess.getBuffer();
            b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            for (P p : g.getValue()) {
                float t = Math.min(1f, (p.age + td) / (float) Math.max(1, p.life));
                float a = p.alpha * (1f - t * t);
                float sz = p.size * (1f - 0.5f * t);
                float cx = (float) (MathHelper.lerp(td, p.px, p.x) - cp.x);
                float cy = (float) (MathHelper.lerp(td, p.py, p.y) - cp.y);
                float cz = (float) (MathHelper.lerp(td, p.pz, p.z) - cp.z);
                for (int i = 0; i < 4; i++) {
                    v.set(corners[i][0], corners[i][1], 0f).rotate(rot).mul(sz).add(cx, cy, cz);
                    b.vertex(mat, v.x, v.y, v.z).texture(uvs[i][0], uvs[i][1]).color(1f, 1f, 1f, a).next();
                }
            }
            tess.draw();
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private AuraParticles() {}
}
