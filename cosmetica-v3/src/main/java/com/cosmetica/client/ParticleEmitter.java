package com.cosmetica.client;

import com.cosmetica.client.CosmeticDef.ParticleSpec;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

/** Spawns aura particles around the local player for equipped cosmetics. */
public final class ParticleEmitter {
    private static int ticks = 0;

    public static Identifier texOf(Identifier sprite) {
        return new Identifier(sprite.getNamespace(), "textures/particle/" + sprite.getPath() + ".png");
    }

    public static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) { AuraParticles.clear(); return; }
        if (mc.isPaused()) return;
        AuraParticles.tick();
        ticks++;
        // Hidden while in first person? No: feet/body auras are still visible. Only hide when invisible/spectating.
        if (mc.player.isInvisible() || mc.player.isSpectator()) return;
        for (CosmeticDef d : EquippedCosmetics.effective()) {
            ParticleSpec s = d.particles();
            if (s == null || ticks % s.interval() != 0) continue;
            for (int i = 0; i < s.count(); i++) {
                try {
                    spawn(mc, mc.player, s);
                } catch (Exception ex) {
                    if (ticks % 200 == 0) System.err.println("[Cosmetica] aura spawn error: " + ex);
                }
            }
        }
    }

    private static void spawn(MinecraftClient mc, ClientPlayerEntity p, ParticleSpec s) {
        Random rnd = mc.world.random;
        double x = p.getX(), y = p.getY(), z = p.getZ();
        double yaw = Math.toRadians(p.bodyYaw);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);      // forward
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);     // right
        double px = x, py = y + 1, pz = z;
        double a = rnd.nextDouble() * Math.PI * 2, r = s.radius();
        double sq = Math.sqrt(rnd.nextDouble());
        switch (s.shape()) {
            case "sphere" -> {
                double dx = rnd.nextGaussian(), dy = rnd.nextGaussian(), dz = rnd.nextGaussian();
                double l = Math.sqrt(dx * dx + dy * dy + dz * dz) + 1e-4, rr = r * Math.cbrt(rnd.nextDouble());
                px = x + dx / l * rr; py = y + 0.9 + dy / l * rr; pz = z + dz / l * rr;
            }
            case "ring" -> { px = x + Math.cos(a) * r; py = y + 0.1 + rnd.nextDouble() * 1.6; pz = z + Math.sin(a) * r; }
            case "feet" -> { px = x + Math.cos(a) * r * sq; py = y + 0.05; pz = z + Math.sin(a) * r * sq; }
            case "head" -> { px = x + Math.cos(a) * r; py = y + p.getHeight() + 0.1; pz = z + Math.sin(a) * r; }
            case "above" -> { px = x + Math.cos(a) * r * sq; py = y + 2.6 + rnd.nextDouble() * 0.4; pz = z + Math.sin(a) * r * sq; }
            case "chest" -> {
                px = x + fx * 0.3 + (rnd.nextDouble() - 0.5) * 0.2; py = y + 1.15 + (rnd.nextDouble() - 0.5) * 0.2;
                pz = z + fz * 0.3 + (rnd.nextDouble() - 0.5) * 0.2;
            }
            case "back" -> {
                double side = (rnd.nextBoolean() ? 1 : -1) * 0.17;
                px = x - fx * 0.38 + rx * side; py = y + 0.45; pz = z - fz * 0.38 + rz * side;
            }
            case "hand", "staff" -> {
                px = x + rx * 0.4 + fx * 0.35 + (rnd.nextDouble() - 0.5) * r;
                py = y + (s.shape().equals("staff") ? 2.7 : 0.8) + (rnd.nextDouble() - 0.5) * r;
                pz = z + rz * 0.4 + fz * 0.35 + (rnd.nextDouble() - 0.5) * r;
            }
            default -> {}
        }
        Identifier sp = s.sprites().get(rnd.nextInt(s.sprites().size()));
        double v = s.vel();
        AuraParticles.P q = new AuraParticles.P();
        q.x = q.px = px; q.y = q.py = py; q.z = q.pz = pz;
        q.vx = (rnd.nextDouble() - 0.5) * v;
        q.vy = s.rise() + (rnd.nextDouble() - 0.5) * v;
        q.vz = (rnd.nextDouble() - 0.5) * v;
        q.tex = texOf(sp);
        q.size = s.size();
        q.life = s.life() + rnd.nextInt(5);
        q.alpha = s.alpha();
        q.gravity = s.gravity();
        AuraParticles.add(q);
    }
    private ParticleEmitter() {}
}
