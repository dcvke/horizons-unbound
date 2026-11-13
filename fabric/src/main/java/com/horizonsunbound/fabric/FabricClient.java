package com.horizonsunbound.fabric;

import com.horizonsunbound.*;
import com.horizonsunbound.client.*;
import com.horizonsunbound.effect.HotCrustBlock;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Camera;
import com.mojang.blaze3d.vertex.PoseStack;
import com.horizonsunbound.StormPacket;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;

public final class FabricClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        EntityRendererRegistry.register(ModContent.ECHO.get(), EchoRenderer::new);
        ColorProviderRegistry.BLOCK.register((state, level, pos, tint) -> HotCrustBlock.COLORS[state.getValue(HotCrustBlock.AGE)], ModContent.HOT_CRUST.get());
        ParticleFactoryRegistry.getInstance().register(ModContent.THAW_SMOKE.get(), ThawSmokeProvider::new);
        ClientPlayNetworking.registerGlobalReceiver(StormSyncPayload.ID, (client, handler, buffer, response) -> {
            StormSyncPayload packet = StormSyncPayload.read(buffer);
            client.execute(() -> ClientStorms.accept(packet.storms()));
        });
        ClientPlayNetworking.registerGlobalReceiver(StormCutscenes.Payload.ID, (client, handler, buffer, response) -> {
            StormCutscenes.Payload packet = StormCutscenes.Payload.read(buffer);
            client.execute(() -> Cutscene.start(packet.storm(), packet.mode()));
        });
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
    public static void send(StormPacket packet) {
        var buffer = PacketByteBufs.create(); packet.write(buffer); ClientPlayNetworking.send(packet.id(), buffer);
    }
    public static void render(boolean particles, Camera camera, float partial, PoseStack pose) {
        StormRenderStage.render(particles, camera.getPosition(), partial, pose);
    }
}
