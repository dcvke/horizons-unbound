package com.horizonsunbound.forge;

import com.horizonsunbound.*;
import com.horizonsunbound.client.*;
import com.horizonsunbound.effect.HotCrustBlock;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;

@Mod.EventBusSubscriber(modid = HorizonsUnbound.MODID, value = Dist.CLIENT)
public final class ForgeClient {
    static void configure() {
        net.minecraftforge.fml.ModLoadingContext.get().registerExtensionPoint(net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new ConfigScreen(parent)));
    }
    static void sync(StormSyncPayload packet) { ClientStorms.accept(packet.storms()); }
    static void cutscene(StormCutscenes.Payload packet) { Cutscene.start(packet.storm(), packet.mode()); }

    @SubscribeEvent public static void prediction(TickEvent.ClientTickEvent e) { if (e.phase != TickEvent.Phase.END) return; ClientStorms.onTick(); }
    @SubscribeEvent public static void atmosphere(TickEvent.ClientTickEvent e) { if (e.phase != TickEvent.Phase.END) return; StormAtmosphere.onClientTick(); }
    @SubscribeEvent public static void effects(TickEvent.ClientTickEvent e) { if (e.phase != TickEvent.Phase.END) return; ClientEffects.onTick(); }
    @SubscribeEvent public static void inside(TickEvent.ClientTickEvent e) { if (e.phase != TickEvent.Phase.END) return; InsideStorm.onTick(); }
    @SubscribeEvent public static void sounds(TickEvent.ClientTickEvent e) { if (e.phase != TickEvent.Phase.END) return; StormSounds.onTick(); }
    @SubscribeEvent public static void cutscene(TickEvent.ClientTickEvent e) { if (e.phase != TickEvent.Phase.END) return; Cutscene.onTick(); }
    @SubscribeEvent public static void test(TickEvent.ClientTickEvent e) { if (e.phase != TickEvent.Phase.END) return; ClientTest.onTick(); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientStorms.onLogout();
        Cutscene.onLogout();
    }
    private static boolean stage(RenderLevelStageEvent e) {
        return e.getStage() == (StormRenderer.particlesStage() ? RenderLevelStageEvent.Stage.AFTER_PARTICLES : RenderLevelStageEvent.Stage.AFTER_WEATHER);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if (!stage(e)) return;
        StormRenderStage.render(e.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES, e.getCamera().getPosition(), e.getPartialTick(), e.getPoseStack());
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
        e.setNearPlaneDistance(Math.min(e.getNearPlaneDistance(), far * .15f));
        e.setCanceled(true);
    }
    @SubscribeEvent public static void fov(ViewportEvent.ComputeFov e) { e.setFOV(Cutscene.onFov(e.getFOV(), (float) e.getPartialTick())); }
    @SubscribeEvent public static void gui(RenderGuiEvent.Pre e) {
        if (Cutscene.onGui(e.getGuiGraphics(), e.getPartialTick())) e.setCanceled(true);
    }
    @SubscribeEvent public static void move(MovementInputUpdateEvent e) { Cutscene.onMove(e.getInput(), e.getEntity()); }
    @SubscribeEvent public static void use(InputEvent.InteractionKeyMappingTriggered e) {
        if (Cutscene.active()) { e.setCanceled(true); e.setSwingHand(false); }
    }
    @SubscribeEvent public static void attack(InputEvent.InteractionKeyMappingTriggered e) { if (e.isAttack()) InsideStorm.onAttack(); }
    @SubscribeEvent public static void outline(RenderHighlightEvent.Block e) { if (Cutscene.active()) e.setCanceled(true); }
    @SubscribeEvent public static void player(TickEvent.PlayerTickEvent e) {
        if (e.phase == TickEvent.Phase.START && e.player instanceof net.minecraft.client.player.LocalPlayer player) InsideStorm.onPlayerTick(player);
    }
    @SubscribeEvent public static void sound(PlaySoundEvent e) { e.setSound(InsideStorm.onSound(e.getSound())); }
    @SubscribeEvent public static void recordSound(PlaySoundEvent e) { ClientTest.onSound(e.getSound()); }
    @SubscribeEvent public static void screen(ScreenEvent.Init.Post e) { ClientTest.onScreen(e.getScreen()); }
    @SubscribeEvent public static void chat(ClientChatReceivedEvent e) { ClientTest.onChat(e.getMessage()); }
    @SubscribeEvent public static void roll(ViewportEvent.ComputeCameraAngles e) {
        Cutscene.View view = Cutscene.view((float) e.getPartialTick());
        if (view != null) e.setRoll(view.roll());
    }
    @Mod.EventBusSubscriber(modid = HorizonsUnbound.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e) { e.registerEntityRenderer(ModContent.ECHO.get(), EchoRenderer::new); }
        @SubscribeEvent public static void blocks(RegisterColorHandlersEvent.Block e) {
            e.register((state, level, pos, tint) -> HotCrustBlock.COLORS[state.getValue(HotCrustBlock.AGE)], ModContent.HOT_CRUST.get());
        }
        @SubscribeEvent public static void particles(RegisterParticleProvidersEvent e) { e.registerSpriteSet(ModContent.THAW_SMOKE.get(), ThawSmokeProvider::new); }
        @SubscribeEvent public static void layers(RegisterGuiOverlaysEvent e) {
            e.registerAboveAll("storm_crossing", (gui, graphics, partial, width, height) -> InsideStorm.drawCrossing(graphics));
        }
    }
}
