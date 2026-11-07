package com.cosmetica.client;

import com.cosmetica.client.CosmeticDef.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class CosmeticLayer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    public CosmeticLayer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> ctx) { super(ctx); }

    private ModelPart anchor(String a) {
        PlayerEntityModel<AbstractClientPlayerEntity> m = getContextModel();
        return switch (a) {
            case "head" -> m.head;
            case "right_arm" -> m.rightArm;
            case "left_arm" -> m.leftArm;
            case "right_leg" -> m.rightLeg;
            case "left_leg" -> m.leftLeg;
            default -> m.body;
        };
    }

    @Override
    public void render(MatrixStack m, VertexConsumerProvider vcp, int light, AbstractClientPlayerEntity player,
                       float limbAngle, float limbDistance, float tickDelta, float animProgress, float headYaw, float headPitch) {
        // TODO multiplayer: sync equipped state via a payload and look up by UUID. For now: only yourself.
        if (player != MinecraftClient.getInstance().player || player.isInvisible()) return;

        float sway = Math.min(limbDistance, 1f) * 35f + (player.isSneaking() ? 12f : 0f);
        for (CosmeticDef d : EquippedCosmetics.effective()) {
            if (d.slot().equals("pet")) continue; // pets are drawn by PetManager
            if (d.mesh() != null) renderMesh(m, vcp, light, d.mesh());
            if (d.texture() == null) continue;
            VertexConsumer buf = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(d.texture()));
            for (PartDef part : d.parts()) {
                ModelPart a = anchor(part.anchor());
                for (BoxDef box : part.boxes()) {
                    m.push();
                    a.rotate(m);
                    if (box.sway()) {
                        m.translate(0, 0, 2f / 16f);
                        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sway));
                        m.translate(0, 0, -2f / 16f);
                    }
                    box.model().render(m, buf, box.glow() ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light, OverlayTexture.DEFAULT_UV);
                    m.pop();
                }
            }
        }
    }

    private void renderMesh(MatrixStack m, VertexConsumerProvider vcp, int light, MeshDef mesh) {
        VertexConsumer buf = vcp.getBuffer(RenderLayer.getEntityTranslucent(mesh.texture()));
        m.push();
        anchor(mesh.anchor()).rotate(m);
        drawQuads(m, buf, light, mesh);
        m.pop();
    }

    /** Draws the mesh quads in the current matrix space (shared with pets). */
    static void drawQuads(MatrixStack m, VertexConsumer buf, int light, MeshDef mesh) {
        MatrixStack.Entry e = m.peek();
        Matrix4f pm = e.getPositionMatrix();
        Matrix3f nm = e.getNormalMatrix();
        for (float[] q : mesh.quads()) {
            for (int i = 0; i < 4; i++) {
                buf.vertex(pm, q[i * 3] / 16f, q[i * 3 + 1] / 16f, q[i * 3 + 2] / 16f)
                    .color(255, 255, 255, 255)
                    .texture(q[12 + i * 2], q[13 + i * 2])
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(light)
                    .normal(nm, q[20], q[21], q[22])
                    .next();
            }
        }
    }
}
