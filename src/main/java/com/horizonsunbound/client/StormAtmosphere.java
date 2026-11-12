package com.horizonsunbound.client;

import com.horizonsunbound.HorizonsUnbound;
import com.horizonsunbound.Storm;
import com.horizonsunbound.StormType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Near a storm the sky, fog and vanilla clouds take on the storm's tint (sky/clouds via ClientLevelMixin). */
@EventBusSubscriber(modid = HorizonsUnbound.MODID, value = Dist.CLIENT)
public final class StormAtmosphere {
    private static float weight; // 0..1, how deep the camera is under the nearest storm
    private static StormType type;
    private static boolean clouds; // under a storm's sky (vanilla clouds stay hidden even in the eye)
    private static int sky;        // the tint color (a merged storm's two colors blended)
    private static float omenDark; // 0..1 how much a coming storm darkens the sky here

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        float best = 0, dark = 0;
        StormType bestType = null;
        Storm bestStorm = null;
        if (mc.level != null) {
            Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
            for (Storm storm : ClientStorms.all()) {
                float reach = storm.radius() * StormRenderer.CAP; // umbrella radius
                float d = (float) Math.sqrt(Mth.square(storm.x - cam.x) + Mth.square(storm.z - cam.z));
                float w = (1 - smooth((d - reach * .7f) / (reach * .55f))) // full tint anywhere under the umbrella
                        * StormBirth.presence(storm.age / 20f) * (1 - storm.dying());               // growing with a newborn storm
                if (w > best) { best = w; bestType = storm.type; bestStorm = storm; }
            }
            clouds = best > 0;
            // Omens: in the minute before a storm rises the sky over that whole region darkens towards its color.
            for (Storm omen : ClientStorms.omens()) {
                float reach = omen.radius() * 6, d = (float) omen.dist(cam.x, cam.z), p = omen.omenProgress();
                float w = p * (1 - smooth((d - reach * .5f) / (reach * .5f)));
                dark = Math.max(dark, w);
                if (w * .8f > best) { best = w * .8f; bestType = omen.type; bestStorm = omen; }
            }
        } else clouds = false;
        omenDark = dark;
        if (bestType == StormType.OVERWORLD) best *= 1 - InsideStorm.weight(StormType.OVERWORLD); // the eye: clear sky
        weight = best;
        type = bestType;
        if (bestStorm != null) { // a merged storm tints the sky with both its kinds
            int a = bestType.sky, b = bestStorm.mix != null ? bestStorm.mix.sky : a;
            sky = ((a >> 16 & 255) + (b >> 16 & 255)) / 2 << 16 | ((a >> 8 & 255) + (b >> 8 & 255)) / 2 << 8 | ((a & 255) + (b & 255)) / 2;
        }
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        Vec3 c = tint(new Vec3(event.getRed(), event.getGreen(), event.getBlue()), .8f);
        event.setRed((float) c.x);
        event.setGreen((float) c.y);
        event.setBlue((float) c.z);
    }

    /** True while the camera is under a storm's sky; vanilla clouds are hidden then (LevelRendererMixin). */
    public static boolean active() { return clouds; }

    /** Pulls a vanilla sky/fog/cloud color toward the storm tint, keeping its brightness so nights stay dark. */
    public static Vec3 tint(Vec3 c, float darken) {
        if (type == null || weight <= 0) return c;
        float r = (sky >> 16 & 255) / 255f, g = (sky >> 8 & 255) / 255f, b = (sky & 255) / 255f;
        double k = (.3 * c.x + .59 * c.y + .11 * c.z) / (.3 * r + .59 * g + .11 * b) * darken * (1 - .45 * omenDark);
        return c.lerp(new Vec3(Math.min(1, r * k), Math.min(1, g * k), Math.min(1, b * k)), weight * .85);
    }

    /** 0..1 how close a storm is to rising near the camera (omens: wind, sound). */
    public static float omen() { return omenDark; }

    private static float smooth(float x) { x = Mth.clamp(x, 0, 1); return x * x * (3 - 2 * x); }
}
