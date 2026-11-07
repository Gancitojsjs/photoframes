package com.cosmetica.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class CosmeticaClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EquippedCosmetics.load();

        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
            new SimpleSynchronousResourceReloadListener() {
                @Override public Identifier getFabricId() { return new Identifier("cosmetica", "cosmetics"); }
                @Override public void reload(ResourceManager rm) { CosmeticRegistry.load(rm); }
            });

        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
            if (renderer instanceof PlayerEntityRenderer pr) helper.register(new CosmeticLayer(pr));
        });

        KeyBinding open = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.cosmetica.menu", GLFW.GLFW_KEY_K, "key.categories.cosmetica"));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (open.wasPressed()) mc.setScreen(new CosmeticScreen());
            ParticleEmitter.tick(mc);
            PetManager.tick(mc);
        });

        // auras (own particles) and pets are drawn by us after the world
        WorldRenderEvents.AFTER_ENTITIES.register(PetManager::render);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(AuraParticles::render);
    }
}
