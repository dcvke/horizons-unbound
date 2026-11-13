package com.horizonsunbound.fabric;

import com.horizonsunbound.*;
import com.horizonsunbound.client.*;
import com.horizonsunbound.effect.HotCrustBlock;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.client.ConfigScreenFactoryRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;

public final class FabricClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        ConfigScreenFactoryRegistry.INSTANCE.register(HorizonsUnbound.MODID, ConfigurationScreen::new);
        EntityRendererRegistry.register(ModContent.ECHO.get(), EchoRenderer::new);
        ColorProviderRegistry.BLOCK.register((state, level, pos, tint) -> HotCrustBlock.COLORS[state.getValue(HotCrustBlock.AGE)], ModContent.HOT_CRUST.get());
        ClientPlayNetworking.registerGlobalReceiver(StormSyncPayload.TYPE, (packet, context) -> ClientStorms.accept(packet.storms()));
        ClientPlayNetworking.registerGlobalReceiver(StormCutscenes.Payload.TYPE, (packet, context) -> Cutscene.start(packet.storm(), packet.mode()));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientStorms.onTick();
            StormAtmosphere.onClientTick();
            ClientEffects.onTick();
            InsideStorm.onTick();
            StormSounds.onTick();
            Cutscene.onTick();
            ClientTest.onTick();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientStorms.onLogout();
            Cutscene.onLogout();
        });
        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, hit) -> !Cutscene.active());
        HudRenderCallback.EVENT.register((graphics, delta) -> InsideStorm.drawCrossing(graphics));
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> ClientTest.onScreen(screen));
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> ClientTest.onChat(message));
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, timestamp) -> ClientTest.onChat(message));
    }
    public static void send(CustomPacketPayload packet) { ClientPlayNetworking.send(packet); }
    public static void render(boolean particles, Camera camera, DeltaTracker delta) {
        if (particles != StormRenderer.particlesStage()) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        StormRenderer.onRenderLevel(camera.getPosition(), partial);
        InsideStorm.onRender(camera.getPosition(), partial);
    }
}
