package com.cosmetica.client;

import net.minecraft.client.model.ModelPart;
import net.minecraft.util.Identifier;

import java.util.List;

/** One cosmetic: built from JSON (box models) or imported from Blockbench (mesh). */
public record CosmeticDef(String key, String name, String slot, String desc, Identifier texture,
                          List<PartDef> parts, ParticleSpec particles, MeshDef mesh, int rgb, String rarity,
                          PetSpec pet) {
    public record PartDef(String anchor, List<BoxDef> boxes) {}
    /** swing/phase/idle/basePitch are only used by pets (leg and tail animation). */
    public record BoxDef(ModelPart model, boolean sway, boolean glow, float swing, float phase, boolean idle, float basePitch) {}
    /** quad layout: [0..11] 4 vertices xyz (pixels), [12..19] 4 uv, [20..22] normal */
    public record MeshDef(String anchor, Identifier texture, List<float[]> quads) {}
    public record ParticleSpec(List<Identifier> sprites, String shape, float radius, int interval, int count,
                               float rise, float size, int life, float alpha, float vel, float gravity) {}
    /** mode: "ground" (walks behind you) or "float" (hovers next to you). scale: size multiplier. */
    public record PetSpec(String mode, float scale) {}
}
