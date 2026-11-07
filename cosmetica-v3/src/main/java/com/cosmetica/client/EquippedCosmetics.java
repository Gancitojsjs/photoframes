package com.cosmetica.client;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.*;
import java.util.*;

/** Equipped cosmetics (one per slot), saved in config/cosmetica.json. PREVIEW overrides them while the menu is open. */
public final class EquippedCosmetics {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("cosmetica.json");
    public static final Map<String, String> BY_SLOT = new HashMap<>();
    public static final Map<String, String> PREVIEW = new HashMap<>();

    public static void toggle(CosmeticDef d) {
        if (d.key().equals(BY_SLOT.get(d.slot()))) BY_SLOT.remove(d.slot()); else BY_SLOT.put(d.slot(), d.key());
        save();
    }
    public static void clearAll() { BY_SLOT.clear(); save(); }
    public static boolean isEquipped(CosmeticDef d) { return d.key().equals(BY_SLOT.get(d.slot())); }

    public static List<CosmeticDef> effective() {
        List<CosmeticDef> out = new ArrayList<>();
        for (String slot : CosmeticRegistry.SLOTS) {
            String k = PREVIEW.containsKey(slot) ? PREVIEW.get(slot) : BY_SLOT.get(slot);
            if (k == null) continue;
            CosmeticDef d = CosmeticRegistry.get(k);
            if (d != null && d.slot().equals(slot)) out.add(d);
        }
        return out;
    }

    public static void load() {
        try {
            if (!Files.exists(FILE)) return;
            JsonParser.parseString(Files.readString(FILE)).getAsJsonObject()
                .entrySet().forEach(e -> BY_SLOT.put(e.getKey(), e.getValue().getAsString()));
        } catch (Exception ignored) {}
    }
    public static void save() {
        try {
            JsonObject o = new JsonObject();
            BY_SLOT.forEach(o::addProperty);
            Files.writeString(FILE, o.toString());
        } catch (Exception ignored) {}
    }
    private EquippedCosmetics() {}
}
