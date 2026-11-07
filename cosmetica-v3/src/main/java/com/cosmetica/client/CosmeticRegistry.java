package com.cosmetica.client;

import com.cosmetica.client.CosmeticDef.*;
import com.google.gson.*;
import net.minecraft.client.model.*;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.Reader;
import java.util.*;

public final class CosmeticRegistry {
    public static final List<String> SLOTS = List.of("head", "suit", "back", "hand", "aura", "pet");
    public static final List<String> LABELS = List.of("Cabeza", "Traje", "Espalda", "Mano", "Aura", "Mascota");
    public static final Map<String, CosmeticDef> ALL = new LinkedHashMap<>();
    /** Temporary (not saved) cosmetics, e.g. the model being previewed in the model browser. */
    public static final Map<String, CosmeticDef> TEMP = new HashMap<>();

    public static CosmeticDef get(String key) {
        CosmeticDef d = ALL.get(key);
        return d != null ? d : TEMP.get(key);
    }

    public static List<CosmeticDef> bySlot(String slot) {
        return ALL.values().stream().filter(d -> d.slot().equals(slot))
            .sorted(Comparator.comparing(CosmeticDef::name)).toList();
    }

    public static int rarityColor(String r) {
        return switch (r) {
            case "rare" -> 0x4FA3FF;
            case "epic" -> 0xB45CFF;
            case "legendary" -> 0xFFB02E;
            case "import" -> 0x55FF99;
            default -> 0x9AA0AA;
        };
    }

    private static float[] arr(JsonObject o, String k) {
        JsonArray a = o.getAsJsonArray(k);
        float[] r = new float[a.size()];
        for (int i = 0; i < r.length; i++) r[i] = a.get(i).getAsFloat();
        return r;
    }
    private static float rad(float deg) { return (float) Math.toRadians(deg); }

    /** Re-reads config/cosmetica/models (Blockbench imports). */
    public static void reloadImports() {
        ALL.keySet().removeIf(k -> k.startsWith("import:"));
        ALL.putAll(ImportedModels.scan());
    }

    public static void load(ResourceManager rm) {
        TreeMap<String, CosmeticDef> tmp = new TreeMap<>();
        rm.findResources("cosmetics", id -> id.getPath().endsWith(".json")).forEach((id, res) -> {
            try (Reader r = res.getReader()) {
                JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                List<PartDef> parts = new ArrayList<>();
                Identifier tex = null;
                if (o.has("parts")) {
                    int tw = o.getAsJsonArray("texture_size").get(0).getAsInt();
                    int th = o.getAsJsonArray("texture_size").get(1).getAsInt();
                    tex = new Identifier(o.get("texture").getAsString());
                    for (JsonElement pe : o.getAsJsonArray("parts")) {
                        JsonObject po = pe.getAsJsonObject();
                        List<BoxDef> boxes = new ArrayList<>();
                        for (JsonElement be : po.getAsJsonArray("boxes")) {
                            JsonObject bo = be.getAsJsonObject();
                            float[] f = arr(bo, "from"), s = arr(bo, "size"), uv = arr(bo, "uv");
                            float[] pv = bo.has("pivot") ? arr(bo, "pivot") : new float[3];
                            float[] rt = bo.has("rotate") ? arr(bo, "rotate") : new float[3];
                            float grow = bo.has("grow") ? bo.get("grow").getAsFloat() : 0f;
                            ModelData md = new ModelData();
                            md.getRoot().addChild("b",
                                ModelPartBuilder.create().uv((int) uv[0], (int) uv[1])
                                    .cuboid(f[0], f[1], f[2], s[0], s[1], s[2], new Dilation(grow)),
                                ModelTransform.of(pv[0], pv[1], pv[2], rad(rt[0]), rad(rt[1]), rad(rt[2])));
                            boxes.add(new BoxDef(TexturedModelData.of(md, tw, th).createModel(),
                                bo.has("sway") && bo.get("sway").getAsBoolean(),
                                bo.has("glow") && bo.get("glow").getAsBoolean(),
                                bo.has("swing") ? bo.get("swing").getAsFloat() : 0f,
                                bo.has("phase") ? bo.get("phase").getAsFloat() : 0f,
                                bo.has("idle") && bo.get("idle").getAsBoolean(),
                                rad(rt[0])));
                        }
                        parts.add(new PartDef(po.get("anchor").getAsString(), boxes));
                    }
                }
                ParticleSpec ps = null;
                if (o.has("particles")) {
                    JsonObject p = o.getAsJsonObject("particles");
                    List<Identifier> sprites = new ArrayList<>();
                    for (JsonElement e : p.getAsJsonArray("sprites")) sprites.add(new Identifier(e.getAsString()));
                    ps = new ParticleSpec(sprites, p.get("shape").getAsString(), p.get("radius").getAsFloat(),
                        Math.max(1, p.get("interval").getAsInt()), p.get("count").getAsInt(), p.get("rise").getAsFloat(),
                        p.get("size").getAsFloat(), p.get("life").getAsInt(), p.get("alpha").getAsFloat(),
                        p.get("vel").getAsFloat(), p.get("gravity").getAsFloat());
                }
                String slot = o.get("slot").getAsString();
                PetSpec pet = null;
                if (slot.equals("pet")) {
                    JsonObject po = o.has("pet") ? o.getAsJsonObject("pet") : new JsonObject();
                    pet = new PetSpec(po.has("mode") ? po.get("mode").getAsString() : "ground",
                        po.has("scale") ? po.get("scale").getAsFloat() : 1f);
                }
                String path = id.getPath();
                String key = id.getNamespace() + ":" + path.substring(path.lastIndexOf('/') + 1, path.length() - 5);
                String rarity = o.has("rarity") ? o.get("rarity").getAsString() : "common";
                int rgb = o.has("color") ? Integer.parseInt(o.get("color").getAsString().substring(1), 16) : 0xFFFFFF;
                tmp.put(key, new CosmeticDef(key, o.get("name").getAsString(), slot,
                    o.has("desc") ? o.get("desc").getAsString() : "", tex, parts, ps, null, rgb, rarity, pet));
            } catch (Exception ex) {
                System.err.println("[Cosmetica] Failed to load " + id + ": " + ex);
            }
        });
        tmp.putAll(ImportedModels.scan());
        ALL.clear();
        ALL.putAll(tmp);
    }
    private CosmeticRegistry() {}
}
