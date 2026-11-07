package com.cosmetica.client;

import com.cosmetica.client.CosmeticDef.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * Client-side pets. A pet is a cosmetic in the "pet" slot: it is not an entity, the mod simulates its
 * position (it follows you) and draws it after entities. Only you see it (like the rest of the mod).
 */
public final class PetManager {
    private static CosmeticDef def;
    private static boolean init;
    private static double x, y, z, px, py, pz;
    private static float yaw, pyaw, walk, pwalk, mv, pmv;
    private static int age;

    public static boolean isFloating(CosmeticDef d) { return d.pet() != null && "float".equals(d.pet().mode()); }

    public static CosmeticDef current() {
        for (CosmeticDef d : EquippedCosmetics.effective()) if (d.slot().equals("pet")) return d;
        return null;
    }

    public static void tick(MinecraftClient mc) {
        ClientPlayerEntity pl = mc.player;
        CosmeticDef d = current();
        if (pl == null || mc.world == null || d == null || pl.isSpectator()) { def = null; init = false; return; }
        if (mc.isPaused()) return;
        def = d;
        boolean fl = isFloating(d);
        double yawR = Math.toRadians(pl.bodyYaw);
        double fx = -Math.sin(yawR), fz = Math.cos(yawR), rx = -Math.cos(yawR), rz = -Math.sin(yawR);
        double tx = pl.getX() - fx * 1.1 + rx * 0.9, tz = pl.getZ() - fz * 1.1 + rz * 0.9;
        double ty = pl.getY() + (fl ? 1.1 : 0.0);
        if (!init || pl.squaredDistanceTo(x, y, z) > 256.0) {
            x = px = tx; y = py = ty; z = pz = tz; yaw = pyaw = pl.bodyYaw; walk = pwalk = 0; mv = pmv = 0; init = true;
        }
        px = x; py = y; pz = z; pyaw = yaw; pwalk = walk; pmv = mv; age++;

        double dx = tx - x, dz = tz - z, dist = Math.hypot(dx, dz);
        double step = dist > 0.35 ? Math.min((dist - 0.25) * 0.22, 0.5) : 0.0;
        if (step > 0) { x += dx / dist * step; z += dz / dist * step; }
        y += (ty - y) * (fl ? 0.15 : 0.35);

        mv += (Math.min(1f, (float) step * 9f) - mv) * 0.3f;
        if (mv > 0.03f) walk += 0.25f + (float) step * 4f;
        float want = step > 0.02 ? (float) Math.toDegrees(Math.atan2(-dx, dz)) : pl.bodyYaw;
        yaw = MathHelper.lerpAngleDegrees(0.25f, yaw, want);
    }

    public static void render(WorldRenderContext ctx) {
        CosmeticDef d = def;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (d == null || !init || mc.world == null || mc.player == null) return;
        if (mc.player.isInvisible() || mc.player.isSpectator()) return;
        float td = ctx.tickDelta();
        Vec3d cam = ctx.camera().getPos();
        double ix = MathHelper.lerp(td, px, x), iy = MathHelper.lerp(td, py, y), iz = MathHelper.lerp(td, pz, z);
        float iyaw = MathHelper.lerpAngleDegrees(td, pyaw, yaw);
        float iwalk = MathHelper.lerp(td, pwalk, walk), imv = MathHelper.lerp(td, pmv, mv);
        float time = age + td;

        float bob = isFloating(d) ? MathHelper.sin(time * 0.12f) * 0.1f
            : imv > 0.05f ? Math.abs(MathHelper.sin(iwalk)) * 0.1f * imv : 0f;
        int light = WorldRenderer.getLightmapCoordinates(mc.world, BlockPos.ofFloored(ix, iy + 0.5, iz));

        VertexConsumerProvider.Immediate imm = mc.getBufferBuilders().getEntityVertexConsumers();
        MatrixStack m = ctx.matrixStack();
        m.push();
        m.translate(ix - cam.x, iy - cam.y + bob, iz - cam.z);
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - iyaw));
        draw(m, imm, d, light, iwalk, imv, time);
        m.pop();
        imm.draw();
    }

    /**
     * Draws the pet. The matrix must already be at the pet's feet and rotated like an entity
     * (this applies the same flips as LivingEntityRenderer, so box coordinates are vanilla model space: feet at y = 24).
     */
    public static void draw(MatrixStack m, VertexConsumerProvider vcp, CosmeticDef d, int light, float walk, float mv, float time) {
        float sc = d.pet() != null ? d.pet().scale() : 1f;
        m.push();
        m.scale(sc, sc, sc);
        m.scale(-1f, -1f, 1f);
        m.translate(0f, -1.501f, 0f);
        if (d.mesh() != null) {
            VertexConsumer buf = vcp.getBuffer(RenderLayer.getEntityTranslucent(d.mesh().texture()));
            CosmeticLayer.drawQuads(m, buf, light, d.mesh());
        }
        if (d.texture() != null) {
            VertexConsumer buf = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(d.texture()));
            for (PartDef part : d.parts()) {
                for (BoxDef box : part.boxes()) {
                    ModelPart b = box.model().getChild("b");
                    if (box.swing() != 0f) {
                        boolean still = box.idle() && mv < 0.1f;
                        float t = still ? time * 0.15f : walk;
                        float amp = box.idle() ? Math.max(mv, 0.25f) : mv;
                        b.pitch = box.basePitch() + (float) Math.toRadians(box.swing()) * amp * MathHelper.sin(t + box.phase());
                    }
                    box.model().render(m, buf, box.glow() ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light,
                        OverlayTexture.DEFAULT_UV);
                }
            }
        }
        m.pop();
    }

    public static void reset() { def = null; init = false; }
    private PetManager() {}
}
