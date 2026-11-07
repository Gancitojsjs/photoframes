package com.cosmetica.client;

import com.cosmetica.client.CosmeticDef.MeshDef;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Loads Blockbench models dropped into config/cosmetica/models:
 *  - NAME.bbmodel (texture embedded), or
 *  - NAME.json (Java Block/Item) + NAME.png
 * File name prefix picks the slot: head_*, back_*, hand_*, pet_*.
 * Optional NAME.cosmetic.json: {"slot","name","scale","offset":[x,y,z],"yaw":180}
 */
public final class ImportedModels {
    public static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("cosmetica").resolve("models");
    private static final Set<String> SLOTS = Set.of("head", "back", "hand", "pet");
    private static final String README = """
        COSMETICA - modelos de Blockbench
        ---------------------------------
        Suelta aqui archivos .bbmodel (con la textura incluida) o un .json (Java Block/Item) con su .png del mismo nombre.
        El prefijo del nombre elige el slot:  head_bruja.bbmodel   back_alas.bbmodel   hand_espada.bbmodel   pet_zorro.bbmodel
        Luego abre el menu (K) y pulsa "Recargar".

        Ajustes opcionales: crea NOMBRE.cosmetic.json, por ejemplo:
        {"name":"Mi sombrero","scale":0.5,"offset":[0,0,0],"yaw":0}
        scale = tamano, offset = desplazamiento en pixeles [x,y,z], yaw = 180 para girar el modelo.
        Uso personal: respeta la licencia de cada modelo que descargues.
        """;

    public static Map<String, CosmeticDef> scan() {
        Map<String, CosmeticDef> out = new TreeMap<>();
        try {
            Files.createDirectories(DIR);
            Path readme = DIR.resolve("LEEME.txt");
            if (!Files.exists(readme)) Files.writeString(readme, README);
            List<Path> files;
            try (Stream<Path> s = Files.list(DIR)) { files = s.sorted().toList(); }
            for (Path p : files) {
                String n = p.getFileName().toString();
                if (!(n.endsWith(".bbmodel") || (n.endsWith(".json") && !n.endsWith(".cosmetic.json")))) continue;
                try {
                    CosmeticDef d = load(p);
                    out.put(d.key(), d);
                } catch (Exception e) {
                    System.err.println("[Cosmetica] No se pudo importar " + n + ": " + e);
                }
            }
        } catch (Exception e) {
            System.err.println("[Cosmetica] Error leyendo modelos: " + e);
        }
        return out;
    }

    private static double[] doubles(JsonElement e, double[] def) {
        if (e == null || !e.isJsonArray()) return def;
        JsonArray a = e.getAsJsonArray();
        double[] r = new double[a.size()];
        for (int i = 0; i < r.length; i++) r[i] = a.get(i).getAsDouble();
        return r;
    }
    private static String sanitize(String s) { return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_"); }

    /** Per-model settings (what NAME.cosmetic.json stores). */
    public record Opts(String slot, String name, double scale, double[] off, boolean yaw) {
        public static double defaultScale(String slot) { return slot.equals("back") ? 0.6 : 0.5; }
    }

    private static CosmeticDef load(Path p) throws Exception {
        String file = p.getFileName().toString();
        String base = file.substring(0, file.lastIndexOf('.'));
        JsonObject meta = new JsonObject();
        Path mp = DIR.resolve(base + ".cosmetic.json");
        if (Files.exists(mp)) meta = JsonParser.parseString(Files.readString(mp)).getAsJsonObject();

        String prefix = base.contains("_") ? base.substring(0, base.indexOf('_')) : "";
        String slot = meta.has("slot") ? meta.get("slot").getAsString() : prefix;
        if (!SLOTS.contains(slot)) slot = "head";
        String nice = meta.has("name") ? meta.get("name").getAsString()
            : (SLOTS.contains(prefix) ? base.substring(prefix.length() + 1) : base).replace('_', ' ');
        double scale = meta.has("scale") ? meta.get("scale").getAsDouble() : Opts.defaultScale(slot);
        double[] off = doubles(meta.get("offset"), new double[3]);
        boolean yaw = meta.has("yaw") && meta.get("yaw").getAsInt() == 180;
        return build(p, base, new Opts(slot, nice, scale, off, yaw));
    }

    /** Builds a cosmetic from a model file wherever it is (used for imports and for the browser preview). */
    public static CosmeticDef build(Path p, String base, Opts o) throws Exception {
        String file = p.getFileName().toString();
        boolean bb = file.endsWith(".bbmodel");
        JsonObject root = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
        String slot = SLOTS.contains(o.slot()) ? o.slot() : "head";
        String nice = o.name();
        double scale = o.scale();
        double[] off = o.off() == null ? new double[3] : o.off();
        boolean yaw = o.yaw();

        NativeImage img;
        double uDiv = 16, vDiv = 16;
        if (bb) {
            JsonObject t0 = root.getAsJsonArray("textures").get(0).getAsJsonObject();
            String src = t0.get("source").getAsString();
            byte[] data = Base64.getMimeDecoder().decode(src.substring(src.indexOf(',') + 1));
            img = NativeImage.read(new ByteArrayInputStream(data));
            if (root.has("resolution")) {
                JsonObject res = root.getAsJsonObject("resolution");
                uDiv = res.get("width").getAsDouble(); vDiv = res.get("height").getAsDouble();
            } else { uDiv = img.getWidth(); vDiv = img.getHeight(); }
        } else {
            String stem = file.substring(0, file.lastIndexOf('.'));
            Path png = p.resolveSibling(stem + ".png");
            if (!Files.exists(png)) throw new IllegalStateException("falta " + stem + ".png");
            try (InputStream in = Files.newInputStream(png)) { img = NativeImage.read(in); }
        }
        Identifier tid = new Identifier("cosmetica", "imported/" + sanitize(base));
        MinecraftClient.getInstance().getTextureManager().registerTexture(tid, new NativeImageBackedTexture(img));

        Ctx ctx = new Ctx();
        ctx.uDiv = uDiv; ctx.vDiv = vDiv; ctx.slot = slot; ctx.s = scale; ctx.yaw = yaw;
        ctx.ox = off[0]; ctx.oy = off.length > 1 ? off[1] : 0; ctx.oz = off.length > 2 ? off[2] : 0;
        JsonArray els = root.getAsJsonArray("elements");
        if (bb && root.has("outliner") && root.getAsJsonArray("outliner").size() > 0) {
            Map<String, JsonObject> by = new HashMap<>();
            for (JsonElement e : els) {
                JsonObject o = e.getAsJsonObject();
                if (o.has("uuid")) by.put(o.get("uuid").getAsString(), o);
            }
            for (JsonElement n : root.getAsJsonArray("outliner")) walk(n, by, new ArrayList<>(), ctx);
        } else {
            for (JsonElement e : els) cube(e.getAsJsonObject(), new ArrayList<>(), ctx);
        }
        if (ctx.out.isEmpty()) throw new IllegalStateException("sin geometria");
        String anchor = slot.equals("head") ? "head" : slot.equals("hand") ? "right_arm" : slot.equals("pet") ? "root" : "body";
        CosmeticDef.PetSpec pet = slot.equals("pet") ? new CosmeticDef.PetSpec("ground", 1f) : null;
        return new CosmeticDef("import:" + base, nice, slot, "Modelo importado de Blockbench.", null, List.of(), null,
            new MeshDef(anchor, tid, ctx.out), 0x55FF99, "import", pet);
    }

    private static final class Ctx {
        double uDiv, vDiv, s, ox, oy, oz; boolean yaw; String slot;
        final List<float[]> out = new ArrayList<>();
    }

    private static void walk(JsonElement n, Map<String, JsonObject> by, List<double[][]> chain, Ctx ctx) {
        if (n.isJsonPrimitive()) {
            JsonObject c = by.get(n.getAsString());
            if (c != null) cube(c, chain, ctx);
        } else if (n.isJsonObject()) {
            JsonObject g = n.getAsJsonObject();
            if (g.has("export") && !g.get("export").getAsBoolean()) return;
            double[] o = doubles(g.get("origin"), new double[3]), r = doubles(g.get("rotation"), new double[3]);
            chain.add(new double[][]{o, r});
            if (g.has("children")) for (JsonElement c : g.getAsJsonArray("children")) walk(c, by, chain, ctx);
            chain.remove(chain.size() - 1);
        }
    }

    private static double[] rot(double[] p, double[] o, double[] r) {
        double rx = Math.toRadians(r[0]), ry = Math.toRadians(r[1]), rz = Math.toRadians(r[2]);
        double x = p[0] - o[0], y = p[1] - o[1], z = p[2] - o[2];
        double y1 = y * Math.cos(rx) - z * Math.sin(rx), z1 = y * Math.sin(rx) + z * Math.cos(rx);
        double x2 = x * Math.cos(ry) + z1 * Math.sin(ry), z2 = -x * Math.sin(ry) + z1 * Math.cos(ry);
        double x3 = x2 * Math.cos(rz) - y1 * Math.sin(rz), y3 = x2 * Math.sin(rz) + y1 * Math.cos(rz);
        return new double[]{o[0] + x3, o[1] + y3, o[2] + z2};
    }

    private static double[][] face(String name, double[] f, double[] t) {
        return switch (name) {
            case "north" -> new double[][]{{t[0], t[1], f[2]}, {f[0], t[1], f[2]}, {f[0], f[1], f[2]}, {t[0], f[1], f[2]}};
            case "south" -> new double[][]{{f[0], t[1], t[2]}, {t[0], t[1], t[2]}, {t[0], f[1], t[2]}, {f[0], f[1], t[2]}};
            case "east" -> new double[][]{{t[0], t[1], t[2]}, {t[0], t[1], f[2]}, {t[0], f[1], f[2]}, {t[0], f[1], t[2]}};
            case "west" -> new double[][]{{f[0], t[1], f[2]}, {f[0], t[1], t[2]}, {f[0], f[1], t[2]}, {f[0], f[1], f[2]}};
            case "up" -> new double[][]{{f[0], t[1], f[2]}, {t[0], t[1], f[2]}, {t[0], t[1], t[2]}, {f[0], t[1], t[2]}};
            default -> new double[][]{{f[0], f[1], t[2]}, {t[0], f[1], t[2]}, {t[0], f[1], f[2]}, {f[0], f[1], f[2]}};
        };
    }

    /** blockbench space (y up, 16px/block) -> player-model space of the slot's anchor */
    private static double[] place(double[] p, Ctx c) {
        double x = p[0] - 8, z = p[2] - 8;
        double X = -x * c.s, Z = z * c.s;
        double Y = switch (c.slot) {
            case "head" -> -p[1] * c.s;
            case "pet" -> 24 - p[1] * c.s;          // feet at y = 24 in vanilla entity model space
            default -> -(p[1] - 8) * c.s;
        };
        if (c.yaw) { X = -X; Z = -Z; }
        switch (c.slot) {
            case "head" -> { Y -= 8; }
            case "back" -> { Y += 6; Z += 3.5; }
            case "pet" -> { }
            default -> { X += -1; Y += 10.5; }
        }
        return new double[]{X + c.ox, Y + c.oy, Z + c.oz};
    }

    private static void cube(JsonObject c, List<double[][]> chain, Ctx ctx) {
        if (c.has("type") && !c.get("type").getAsString().equals("cube")) return;
        if (c.has("export") && !c.get("export").getAsBoolean()) return;
        double[] f = doubles(c.get("from"), null), t = doubles(c.get("to"), null);
        if (f == null || t == null) return;
        double inf = c.has("inflate") ? c.get("inflate").getAsDouble() : 0;
        f = new double[]{f[0] - inf, f[1] - inf, f[2] - inf}; t = new double[]{t[0] + inf, t[1] + inf, t[2] + inf};
        double[] origin = new double[]{8, 8, 8}, rotation = new double[3];
        JsonElement re = c.get("rotation");
        if (re != null && re.isJsonArray()) { rotation = doubles(re, rotation); origin = doubles(c.get("origin"), origin); }
        else if (re != null && re.isJsonObject()) {
            JsonObject ro = re.getAsJsonObject();
            origin = doubles(ro.get("origin"), origin);
            double ang = ro.has("angle") ? ro.get("angle").getAsDouble() : 0;
            String ax = ro.has("axis") ? ro.get("axis").getAsString() : "y";
            rotation = new double[]{ax.equals("x") ? ang : 0, ax.equals("y") ? ang : 0, ax.equals("z") ? ang : 0};
        }
        JsonObject faces = c.getAsJsonObject("faces");
        if (faces == null) return;

        double[] cc = place(world(new double[]{(f[0] + t[0]) / 2, (f[1] + t[1]) / 2, (f[2] + t[2]) / 2}, origin, rotation, chain), ctx);
        for (String name : new String[]{"north", "south", "east", "west", "up", "down"}) {
            if (!faces.has(name) || !faces.get(name).isJsonObject()) continue;
            JsonObject fo = faces.getAsJsonObject(name);
            if (!fo.has("uv")) continue;
            if (fo.has("texture") && fo.get("texture").isJsonNull()) continue;
            double[] uv = doubles(fo.get("uv"), null);
            if (uv == null || uv.length < 4) continue;
            int rotK = fo.has("rotation") ? (fo.get("rotation").getAsInt() / 90) & 3 : 0;
            double[][] corners = face(name, f, t);
            double[][] cuv = {{uv[0], uv[1]}, {uv[2], uv[1]}, {uv[2], uv[3]}, {uv[0], uv[3]}};
            float[] q = new float[23];
            double[][] wp = new double[4][];
            for (int i = 0; i < 4; i++) {
                wp[i] = place(world(corners[i], origin, rotation, chain), ctx);
                q[i * 3] = (float) wp[i][0]; q[i * 3 + 1] = (float) wp[i][1]; q[i * 3 + 2] = (float) wp[i][2];
                double[] u = cuv[(i + rotK) & 3];
                q[12 + i * 2] = (float) (u[0] / ctx.uDiv); q[13 + i * 2] = (float) (u[1] / ctx.vDiv);
            }
            double[] a = {wp[1][0] - wp[0][0], wp[1][1] - wp[0][1], wp[1][2] - wp[0][2]};
            double[] b = {wp[3][0] - wp[0][0], wp[3][1] - wp[0][1], wp[3][2] - wp[0][2]};
            double nx = a[1] * b[2] - a[2] * b[1], ny = a[2] * b[0] - a[0] * b[2], nz = a[0] * b[1] - a[1] * b[0];
            double fx = (wp[0][0] + wp[2][0]) / 2 - cc[0], fy = (wp[0][1] + wp[2][1]) / 2 - cc[1], fz = (wp[0][2] + wp[2][2]) / 2 - cc[2];
            if (nx * fx + ny * fy + nz * fz < 0) { nx = -nx; ny = -ny; nz = -nz; }
            double l = Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-9;
            q[20] = (float) (nx / l); q[21] = (float) (ny / l); q[22] = (float) (nz / l);
            ctx.out.add(q);
        }
    }

    private static double[] world(double[] p, double[] origin, double[] rotation, List<double[][]> chain) {
        double[] r = rot(p, origin, rotation);
        for (int i = chain.size() - 1; i >= 0; i--) r = rot(r, chain.get(i)[0], chain.get(i)[1]);
        return r;
    }

    /**
     * Copies a model from anywhere into config/cosmetica/models (named SLOT_name) and writes its .cosmetic.json.
     * Returns the cosmetic key ("import:slot_name").
     */
    public static String install(Path src, Opts o) throws Exception {
        Files.createDirectories(DIR);
        String file = src.getFileName().toString();
        boolean bb = file.endsWith(".bbmodel");
        String raw = file.substring(0, file.lastIndexOf('.'));
        for (String sl : SLOTS) if (raw.startsWith(sl + "_")) { raw = raw.substring(sl.length() + 1); break; }
        String base = o.slot() + "_" + sanitize(raw.replace(' ', '_'));
        Path dst = DIR.resolve(base + (bb ? ".bbmodel" : ".json"));
        if (!isSame(src, dst)) Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
        if (!bb) {
            Path png = src.resolveSibling(file.substring(0, file.lastIndexOf('.')) + ".png");
            Path pdst = DIR.resolve(base + ".png");
            if (Files.exists(png) && !isSame(png, pdst)) Files.copy(png, pdst, StandardCopyOption.REPLACE_EXISTING);
        }
        JsonObject meta = new JsonObject();
        meta.addProperty("name", o.name());
        meta.addProperty("slot", o.slot());
        meta.addProperty("scale", Math.round(o.scale() * 100.0) / 100.0);
        JsonArray oa = new JsonArray();
        double[] off = o.off() == null ? new double[3] : o.off();
        for (int i = 0; i < 3; i++) oa.add(i < off.length ? off[i] : 0);
        meta.add("offset", oa);
        meta.addProperty("yaw", o.yaw() ? 180 : 0);
        Files.writeString(DIR.resolve(base + ".cosmetic.json"), meta.toString());
        return "import:" + base;
    }

    private static boolean isSame(Path a, Path b) {
        try { return Files.exists(b) && Files.isSameFile(a, b); } catch (Exception e) { return false; }
    }

    /** True if the file looks like something we can import (bbmodel, or Java block json with its png next to it). */
    public static boolean looksImportable(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        try {
            if (n.endsWith(".bbmodel")) return true;
            if (!n.endsWith(".json") || n.endsWith(".cosmetic.json")) return false;
            Path png = p.resolveSibling(p.getFileName().toString().substring(0, p.getFileName().toString().length() - 5) + ".png");
            if (!Files.exists(png) || Files.size(p) > 5_000_000) return false;
            return JsonParser.parseString(Files.readString(p)).getAsJsonObject().has("elements");
        } catch (Exception e) { return false; }
    }
    private ImportedModels() {}
}
