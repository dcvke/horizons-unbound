package com.horizonsunbound.neoforge;

import com.horizonsunbound.*;
import com.horizonsunbound.client.*;
import com.horizonsunbound.effect.HotCrustBlock;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = HorizonsUnbound.MODID, value = Dist.CLIENT)
public final class NeoClient {
    static void configure(ModContainer container) { ConfigScreen.register(container); }
    static void sync(StormSyncPayload packet) { ClientStorms.accept(packet.storms()); }
    static void cutscene(StormCutscenes.Payload packet) { Cutscene.start(packet.storm(), packet.mode()); }

    @SubscribeEvent public static void prediction(ClientTickEvent.Post e) { ClientStorms.onTick(); }
    @SubscribeEvent public static void atmosphere(ClientTickEvent.Post e) { StormAtmosphere.onClientTick(); }
    @SubscribeEvent public static void effects(ClientTickEvent.Post e) { ClientEffects.onTick(); }
    @SubscribeEvent public static void inside(ClientTickEvent.Post e) { InsideStorm.onTick(); }
    @SubscribeEvent public static void sounds(ClientTickEvent.Post e) { StormSounds.onTick(); }
    @SubscribeEvent public static void cutscene(ClientTickEvent.Post e) { Cutscene.onTick(); }
    @SubscribeEvent public static void test(ClientTickEvent.Post e) { ClientTest.onTick(); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientStorms.onLogout();
        Cutscene.onLogout();
    }
    private static boolean stage(RenderLevelStageEvent e) {
        return e.getStage() == (StormRenderer.particlesStage() ? RenderLevelStageEvent.Stage.AFTER_PARTICLES : RenderLevelStageEvent.Stage.AFTER_WEATHER);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if (!stage(e)) return;
        StormRenderer.onRenderLevel(e.getCamera().getPosition(), e.getPartialTick().getGameTimeDeltaPartialTick(false));
    }
    @SubscribeEvent public static void interior(RenderLevelStageEvent e) {
        if (!stage(e)) return;
        InsideStorm.onRender(e.getCamera().getPosition(), e.getPartialTick().getGameTimeDeltaPartialTick(false));
    }
    @SubscribeEvent public static void color(ViewportEvent.ComputeFogColor e) {
        Vec3 color = StormAtmosphere.tint(new Vec3(e.getRed(), e.getGreen(), e.getBlue()), .8f);
        e.setRed((float) color.x); e.setGreen((float) color.y); e.setBlue((float) color.z);
    }
    @SubscribeEvent public static void fog(ViewportEvent.RenderFog e) {
        if (e.getMode() != FogRenderer.FogMode.FOG_TERRAIN) return;
        float far = ClientEffects.fogDistance(e.getFarPlaneDistance());
        if (far >= e.getFarPlaneDistance() - .5f) return;
        e.setFarPlaneDistance(far);
        e.setNearPlaneDistance(ClientEffects.fogStart(e.getNearPlaneDistance(), far));
        e.setCanceled(true);
    }
    @SubscribeEvent public static void fov(ViewportEvent.ComputeFov e) { e.setFOV(Cutscene.onFov(e.getFOV(), (float) e.getPartialTick())); }
    @SubscribeEvent public static void gui(RenderGuiEvent.Pre e) {
        if (Cutscene.onGui(e.getGuiGraphics(), e.getPartialTick().getGameTimeDeltaPartialTick(false))) e.setCanceled(true);
    }
    @SubscribeEvent public static void move(MovementInputUpdateEvent e) { Cutscene.onMove(e.getInput(), e.getEntity()); }
    @SubscribeEvent public static void turn(CalculatePlayerTurnEvent e) { if (Cutscene.active()) e.setMouseSensitivity(-1 / 3.0); }
    @SubscribeEvent public static void use(InputEvent.InteractionKeyMappingTriggered e) {
        if (Cutscene.active()) { e.setCanceled(true); e.setSwingHand(false); }
    }
    @SubscribeEvent public static void attack(InputEvent.InteractionKeyMappingTriggered e) { if (e.isAttack()) InsideStorm.onAttack(); }
    @SubscribeEvent public static void outline(RenderHighlightEvent.Block e) { if (Cutscene.active()) e.setCanceled(true); }
    @SubscribeEvent public static void player(PlayerTickEvent.Pre e) {
        if (e.getEntity() instanceof net.minecraft.client.player.LocalPlayer player) InsideStorm.onPlayerTick(player);
    }
    @SubscribeEvent public static void sound(PlaySoundEvent e) { e.setSound(InsideStorm.onSound(e.getSound())); }
    @SubscribeEvent public static void recordSound(PlaySoundEvent e) { ClientTest.onSound(e.getSound()); }
    @SubscribeEvent public static void screen(ScreenEvent.Init.Post e) { ClientTest.onScreen(e.getScreen()); }
    @SubscribeEvent public static void chat(ClientChatReceivedEvent e) { ClientTest.onChat(e.getMessage()); }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e) { e.registerEntityRenderer(ModContent.ECHO.get(), EchoRenderer::new); }
    @SubscribeEvent public static void blocks(RegisterColorHandlersEvent.Block e) {
        e.register((state, level, pos, tint) -> HotCrustBlock.COLORS[state.getValue(HotCrustBlock.AGE)], ModContent.HOT_CRUST.get());
    }
    @SubscribeEvent public static void layers(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath(HorizonsUnbound.MODID, "storm_crossing"), (g, delta) -> InsideStorm.drawCrossing(g));
    }
}
