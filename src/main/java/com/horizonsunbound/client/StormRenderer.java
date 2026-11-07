package com.horizonsunbound.client;

import com.horizonsunbound.HorizonsUnbound;
import com.horizonsunbound.Storm;
import com.horizonsunbound.StormType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import it.unimi.dsi.fastutil.floats.FloatArrayList;

import java.util.function.Function;

/**
 * Draws a storm the way the reference footage shows it: a straight column of stacked cloud tiers with glowing
 * glitch-pixel rings on the seams, a pale ring at its foot, and on top a stepped cloud umbrella with broken glowing circles
 * and a ragged pixel rim. Everything uses pixel-art textures and is rebuilt every frame from the storm's size and UUID.
 */
@EventBusSubscriber(modid = HorizonsUnbound.MODID, value = Dist.CLIENT)
public final class StormRenderer {
    private static final ResourceLocation WALL = tex("storm_wall"), BAND = tex("storm_band"), GLOW = tex("storm_glow");
    private static final float TAU = Mth.TWO_PI;
    // Shape, in storm radii.
    private static final float HEIGHT = 3;    // column height, up to where it opens into the umbrella
    private static final float TOP = 1.55f;   // column radius at the very top
    static final float CAP = 6.5f;            // umbrella radius
    private static final float DROP = 1.3f;   // how far the umbrella rim hangs below its middle
    private static final float SOLID = .5f;   // share of the umbrella that is solid; past it, it breaks up into pixel cells
    private static final float SINK = 8;      // blocks of wall below the storm's base
    private static final int TIERS = 7, TERRACES = 4, ARC_RINGS = 9;
    // Mesh resolution: each of these stays within about a block of the true circle at the size it is drawn.
    private static final int COLUMN_SEG = 48, CAP_SEG = 40, TIER_ROWS = 2, CAP_ROWS = 20, RING_SEG = 64, ARC_SEG = 48, MAX_ROW = RING_SEG + 1;
    private static final int MAX_RUN = 4;      // pixel cells of the rim merged into one quad at most
    private static final int RUN_FLOATS = 12;  // floats per merged rim run, see rimRuns()
    // Storm-cloud gray with a cold violet tint (daylight value, dimmed at night).
    private static final float WALL_R = .40f, WALL_G = .40f, WALL_B = .50f;

    private final Storm storm;
    private final float tx, ty, tz;           // storm base relative to the camera
    private final float r, h, t, light;
    private final float sx, sy, sz;           // storm base in the world, this frame
    private final float pulse, heard;
    // the storm's birth (StormBirth): all 1 / unlimited once it is whole
    private final boolean born;
    private final float girth, tipY, fill, sway, capFront, arcsIn;
    // light inside the cloud cap (see cloudLight()): where, how wide, how strong, which color
    private float litX, litZ, litR2 = 1, lit;
    private final float litCr, litCg, litCb;
    private boolean capPass;
    private float pull;                       // blocks each glow vertex is pulled towards the camera, see Row.glow()                // ring brightness: 1, up to 3.5 while the column flares before a wave
    private final StormType type;
    private final long seed;
    private final boolean camInside, inEye; // camera within the wall / truly inside the column (for the eye's sky)
    private final FrustumIntersection view; // the screen, in camera-relative coordinates
    private final float fogR, fogG, fogB;
    private final float[] tilt = new float[TIERS + 1], tiltPhase = new float[TIERS + 1];
    private final float[] offX = new float[TIERS], offZ = new float[TIERS], spin = new float[TIERS], tone = new float[TIERS];
    private final Row rowA = new Row(), rowB = new Row();

    private StormRenderer(Storm storm, float tx, float ty, float tz, float partial, float light, FrustumIntersection view) {
        this.storm = storm;
        this.view = view;
        this.tx = tx;
        this.ty = ty;
        this.tz = tz;
        r = storm.radius();
        h = HEIGHT * r;
        t = (storm.age + partial) / 20f;
        born = storm.age >= Storm.BIRTH;
        girth = born ? 1 : StormBirth.girth(t);
        tipY = born ? -SINK : h - StormBirth.descend(t) * (h + SINK);
        fill = born ? 1 : StormBirth.fill(t);
        sway = born ? 0 : r * .22f * StormBirth.rope(t);
        float unfurl = born ? 1 : StormBirth.unfurl(t);
        capFront = born ? Float.MAX_VALUE : unfurl <= 0 ? -1 : r * (TOP * .6f + (CAP - TOP * .6f) * unfurl);
        arcsIn = born ? 1 : StormBirth.arcs(t);
        sx = (float) Mth.lerp(partial, storm.xo, storm.x);
        sy = (float) Mth.lerp(partial, storm.yo, storm.y);
        sz = (float) Mth.lerp(partial, storm.zo, storm.z);
        float c = storm.charge(storm.age + partial);
        heard = heard(storm, partial);
        pulse = 1 + 1.6f * c + 2 * heard + (born ? 0 : 2.5f * StormBirth.settle(t)); // a wave's warning, a sculk storm answering, the birth's last pulse
        litCr = (storm.type.glow >> 16 & 255) / 255f;
        litCg = (storm.type.glow >> 8 & 255) / 255f;
        litCb = (storm.type.glow & 255) / 255f;
        this.light = light;
        type = storm.type;
        seed = storm.seed();
        // Inside = within the wall itself at the camera's height (its wobble and tier offsets included), so there is
        // no gap between "outside" and "inside" where the near wall counts as a back face and the rings as hidden
        // behind the column - crossing that gap made the storm vanish for a moment.
        camInside = tx * tx + tz * tz < Mth.square(profile(Mth.clamp(-ty, 0, h)) * 1.04f);
        inEye = tx * tx + tz * tz < Mth.square(r * .97f) && -ty > -SINK && -ty < h;
        float[] fog = RenderSystem.getShaderFogColor();
        fogR = fog[0];
        fogG = fog[1];
        fogB = fog[2];
        RandomSource rnd = RandomSource.create(seed);
        for (int k = 1; k < TIERS; k++) {
            tilt[k] = r * (.015f + rnd.nextFloat() * .03f);
            tiltPhase[k] = rnd.nextFloat() * TAU;
        }
        for (int k = 0; k < TIERS; k++) {
            float a = rnd.nextFloat() * TAU + t * .03f, m = r * .015f * k / TIERS; // higher tiers drift a bit off-center
            offX[k] = m * Mth.cos(a);
            offZ[k] = m * Mth.sin(a);
            spin[k] = .006f + rnd.nextFloat() * .008f;
            tone[k] = .85f + rnd.nextFloat() * .15f;
        }
        cloudLight(storm.charge(storm.age + partial));
    }

    // Drawn after clouds and rain, so they don't paint over the storm (under a storm they're hidden anyway).
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || ClientStorms.all().isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        // 1 at noon, .2 at night; the fullbright storm darkens with the sky, but not so far that it vanishes
        float light = Math.max(.35f, mc.level.getSkyDarken(partial));
        // vertices are camera-relative and the camera rotation sits in the model-view matrix at this stage
        FrustumIntersection view = new FrustumIntersection(new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()));
        int viewDistance = StormConfig.viewDistance(mc.options.getEffectiveRenderDistance());
        for (Storm storm : ClientStorms.all()) {
            float tx = (float) (Mth.lerp(partial, storm.xo, storm.x) - cam.x), ty = (float) (Mth.lerp(partial, storm.yo, storm.y) - cam.y);
            float tz = (float) (Mth.lerp(partial, storm.zo, storm.z) - cam.z), reach = storm.radius() * CAP;
            // drawn while any part of it is within the view distance, and only when it's on screen at all
            if (Mth.sqrt(tx * tx + tz * tz) - reach > viewDistance) continue;
            if (!view.testAab(tx - reach, ty - SINK, tz - reach, tx + reach, ty + storm.radius() * HEIGHT + 10, tz + reach)) continue;
            new StormRenderer(storm, tx, ty, tz, partial, light, view).draw();
        }
    }


    /**
     * The passes are drawn right here, one after the other, not through the shared buffer source: mods that batch
     * it (Iris, Sodium) flush it in their own order, which drew the bands first and the black wall over them.
     */
    private void draw() {
        // The wall isn't depth-sorted (thousands of quads every frame); instead the far part goes first:
        // seen from below the cap is behind the column, seen from above it covers it.
        RenderType wallType = Types.WALL.apply(WALL);
        BufferBuilder wall = begin(wallType);
        if (ty + h > 0) {
            umbrella(wall);
            pixelRim(wall);
            column(wall);
        } else {
            column(wall);
            umbrella(wall);
            pixelRim(wall);
        }
        end(wall, wallType);
        RenderType glowType = Types.GLOW.apply(GLOW);
        BufferBuilder glow = begin(glowType);
        rings(glow, false);
        wave(glow, false);
        birth(glow, false);
        seams(glow, false);
        stars(glow);
        aurora(glow);
        end(glow, glowType);
        RenderType coreType = Types.GLOW.apply(BAND);
        BufferBuilder core = begin(coreType);
        rings(core, true);
        wave(core, true);
        birth(core, true);
        seams(core, true);
        end(core, coreType);
        // The arcs inside the cloud cap shine through the cloud itself (from above and from the side too); only the
        // column hides the far ones (front()). They hang hundreds of blocks up, where no terrain stands in front.
        RenderType arcGlow = Types.THROUGH.apply(GLOW), arcCore = Types.THROUGH.apply(BAND);
        BufferBuilder arcs = begin(arcGlow);
        arcs(arcs, false);
        end(arcs, arcGlow);
        arcs = begin(arcCore);
        arcs(arcs, true);
        end(arcs, arcCore);
    }

    static RenderType glowType() { return Types.GLOW.apply(GLOW); }

    /**
     * Inside an End storm the dark lid over the column's mouth turns into a piece of night sky: stars twinkling
     * and a faint violet nebula. Seen from inside only.
     */
    private void stars(VertexConsumer vc) {
        if (!inEye || type != StormType.END) return;
        RandomSource rnd = RandomSource.create(seed * 31);
        float y = ty + h - 2, mouth = TOP * r * .95f;
        for (int i = 0; i < 6; i++) { // nebula: big soft patches
            float ang = rnd.nextFloat() * TAU, d = Mth.sqrt(rnd.nextFloat()) * mouth * .7f, size = r * (.25f + rnd.nextFloat() * .3f);
            float k = .16f + .06f * Mth.sin(t * .2f + i);
            flat(vc, tx + d * Mth.cos(ang), y + .5f, tz + d * Mth.sin(ang), size, i % 2 == 0 ? 0x7A3FC4 : 0x3F5FC4, k);
        }
        for (int i = 0; i < 260; i++) {
            float ang = rnd.nextFloat() * TAU, d = Mth.sqrt(rnd.nextFloat()) * mouth, size = .5f + rnd.nextFloat() * rnd.nextFloat() * 2.5f;
            float twinkle = .55f + .45f * Mth.sin(t * (1 + rnd.nextFloat() * 3) + i);
            flat(vc, tx + d * Mth.cos(ang), y, tz + d * Mth.sin(ang), size, i % 7 == 0 ? 0xD9C4FF : 0xFFFFFF, twinkle);
        }
    }

    /**
     * Under an Overworld storm's cloud cap, northern lights: three waving curtains of green, cyan and blue along
     * the underside of the cloud, shimmering. Drawn while the camera is under the cap.
     */
    private void aurora(VertexConsumer vc) {
        if (arcsIn <= 0) return;
        if (type != StormType.OVERWORLD || tx * tx + tz * tz > Mth.square(r * CAP)) return;
        pull = 0;
        int[] colors = {0x46FF9A, 0x40E8FF, 0x6A8CFF};
        for (int k = 0; k < 3; k++) {
            float base = r * (1.9f + .55f * k), q = (base - TOP * r) / ((CAP - TOP) * r), top = capHeight(q) - 3, depth = 26 + 8 * k;
            if (!onScreen(0, 0, base + r * .13f, top - depth, top)) continue;
            for (int i = 0; i <= RING_SEG; i++) {
                float ang = i * TAU / RING_SEG, rad = base + r * .12f * Mth.sin(ang * 3 + t * .25f + k);
                float x = rad * Mth.cos(ang), z = rad * Mth.sin(ang), shimmer = .6f + .4f * Mth.sin(ang * 5 - t * .6f + k * 2);
                float a = .9f * shimmer * arcsIn, hz = haze(tx + x, ty + top, tz + z), u = i * 6f / RING_SEG;
                rowA.glow(i, x, top - depth, z, u, 0, colors[k], 0, hz);
                rowB.glow(i, x, top, z, u, 1, colors[k], a, hz);
            }
            strip(vc, rowA, rowB, RING_SEG);
            strip(vc, rowA, rowB, RING_SEG); // twice: additive, the curtains need the brightness
        }
    }

    /**
     * The new parts of a storm's birth (StormBirth): sparks spiralling in to a seed of light, the beam it fires up
     * the full height, the flash on the ground. A few dozen quads, and only for the first seconds.
     */
    private void birth(VertexConsumer vc, boolean core) {
        if (born) return;
        float len = Mth.sqrt(tx * tx + tz * tz) + 1e-3f, px = tz / len, pz = -tx / len; // across the view, horizontal
        int glow = core ? type.core : type.glow;
        float gather = StormBirth.gather(t);
        if (gather > 0) {
            RandomSource rnd = RandomSource.create(seed * 7);
            for (int i = 0; i < 64; i++) { // sparks drawn in from all around, spiralling to the centre
                float delay = rnd.nextFloat() * .8f, ang0 = rnd.nextFloat() * TAU, y0 = rnd.nextFloat() * h * .6f, far = .5f + rnd.nextFloat() * .5f;
                float u = Mth.clamp((t - delay) / 1.5f, 0, 1);
                if (u <= 0 || u >= 1) continue;
                float rad = r * CAP * far * (float) Math.pow(1 - u, 1.6) + r * .03f, ang = ang0 + u * 3;
                float k = Mth.sin(Mth.PI * u) * gather * (core ? 1 : .7f), size = core ? 1.2f : 3.5f;
                billboard(vc, px, pz, rad * Mth.cos(ang), y0 * (1 - u) + 1, rad * Mth.sin(ang), size, size * 2.5f, glow, k);
            }
        }
        float seedK = StormBirth.seed(t);
        if (seedK > 0) billboard(vc, px, pz, 0, 2, 0, r * (core ? .06f : .2f) * (1 + seedK), r * (core ? .06f : .2f) * (1 + seedK), glow, seedK * 1.5f);
        float beam = StormBirth.beam(t);
        if (beam > 0) { // the beam, as tall as the storm will be
            float top = StormBirth.beamHeight(t) * (h + 40);
            billboard(vc, px, pz, 0, -SINK + (top + SINK) / 2, 0, r * (core ? .05f : .2f), top + SINK, glow, beam * (core ? 1.4f : 1));
        }
        float dust = StormBirth.dust(t);
        if (dust > 0) { // dust whirling up from the ground to meet the funnel coming down, spreading as it rises
            RandomSource rnd = RandomSource.create(seed * 13);
            for (int i = 0; i < 48; i++) {
                float ang0 = rnd.nextFloat() * TAU, off = rnd.nextFloat(), spinV = 1.2f + rnd.nextFloat();
                float life = frac(t * .3f + off), y = life * r * .9f, rad = r * girth * (.2f + .8f * life), ang = ang0 + t * spinV;
                float k = dust * Mth.sin(Mth.PI * life) * (core ? .55f : .35f), size = (core ? 1.5f : 4) * (1 + 2 * life);
                billboard(vc, px, pz, swayX(y) + rad * Mth.cos(ang), y, swayZ(y) + rad * Mth.sin(ang), size, size, glow, k);
            }
        }
        float flash = StormBirth.flash(t);
        if (flash > 0 && !core) flat(vc, tx, ty + 1, tz, r * 5 * (1.2f - flash * .2f), glow, flash);
    }

    /** A glowing rectangle standing upright, turned across the view (storm-local centre, width w, height ht). */
    private void billboard(VertexConsumer vc, float px, float pz, float x, float y, float z, float w, float ht, int rgb, float k) {
        int color = argb(1, (rgb >> 16 & 255) / 255f * k, (rgb >> 8 & 255) / 255f * k, (rgb & 255) / 255f * k);
        float hw = w / 2, hh = ht / 2, cx = tx + x, cy = ty + y, cz = tz + z;
        vertex(vc, cx - px * hw, cy - hh, cz - pz * hw, 0, 0, color);
        vertex(vc, cx + px * hw, cy - hh, cz + pz * hw, 0, 1, color);
        vertex(vc, cx + px * hw, cy + hh, cz + pz * hw, 1, 1, color);
        vertex(vc, cx - px * hw, cy + hh, cz - pz * hw, 1, 0, color);
    }

    /** True if a vertical cylinder (storm-local centre x/z, radius rad, heights y0..y1) can be on screen at all. */
    private boolean onScreen(float x, float z, float rad, float y0, float y1) {
        return view.testAab(tx + x - rad, ty + y0, tz + z - rad, tx + x + rad, ty + y1, tz + z + rad);
    }

    /** A flat, horizontal glowing square centred at camera-relative x/y/z. */
    private static void flat(VertexConsumer vc, float x, float y, float z, float size, int rgb, float k) {
        int color = argb(1, (rgb >> 16 & 255) / 255f * k, (rgb >> 8 & 255) / 255f * k, (rgb & 255) / 255f * k);
        float s = size / 2;
        vertex(vc, x - s, y, z - s, 0, 0, color);
        vertex(vc, x + s, y, z - s, 1, 0, color);
        vertex(vc, x + s, y, z + s, 1, 1, color);
        vertex(vc, x - s, y, z + s, 0, 1, color);
    }

    private static BufferBuilder begin(RenderType type) { return Tesselator.getInstance().begin(type.mode(), type.format()); }

    private static void end(BufferBuilder builder, RenderType type) {
        MeshData mesh = builder.build(); // null when everything was off screen
        if (mesh != null) type.draw(mesh);
    }

    /**
     * Something glowing inside the cloud cap, the same on every client (from the storm's seed and age):
     * Overworld - lightning flickering in one spot of the cloud every few seconds; End - rarer violet flashes;
     * Nether - the underside lit by a slow lava throb, brighter while the column flares before a heat wave.
     */
    private void cloudLight(float flare) {
        if (type == StormType.DEEP_DARK) { // slow breathing, and a flash through the cloud when it hears something
            litR2 = Mth.square(r * CAP * .7f);
            lit = .14f + .1f * Mth.sin(t * .8f) + .6f * heard;
            return;
        }
        if (type == StormType.NETHER) {
            litR2 = Mth.square(r * CAP * .8f);
            lit = .22f + .1f * Mth.sin(t * 1.3f) + .35f * flare;
            return;
        }
        float period = type == StormType.OVERWORLD ? 3 : 6;
        int k = Mth.floor(t / period);
        long hash = Mth.getSeed(k, (int) seed, 77);
        float dt = t - (k * period + (hash >>> 20 & 1023) / 1023f * (period - .5f));
        if (dt < 0 || dt > .4f) return;
        // a strike flickers: bright, dim, bright again, fading out
        lit = dt < .07f ? 1 : dt < .14f ? .25f : dt < .21f ? .8f : (.4f - dt) / .19f * .5f;
        float ang = (hash >>> 32 & 1023) / 1023f * TAU, d = r * (TOP + (CAP - TOP) * .4f * (hash >>> 44 & 255) / 255f);
        litX = d * Mth.cos(ang);
        litZ = d * Mth.sin(ang);
        litR2 = Mth.square(r * (type == StormType.OVERWORLD ? 2.8f : 2f));
    }

    /** 0..1: a Deep Dark storm heard a noise just now (its newest PULSE mark, fading over a second). */
    private static float heard(Storm storm, float partial) {
        float h = 0;
        for (Storm.Mark m : storm.marks)
            if (m.kind() == Storm.Mark.PULSE) h = Math.max(h, 1 - (storm.age + partial - m.a()) / 20f);
        return Mth.clamp(h, 0, 1);
    }

    /** How much of the cloud light reaches cap point x/z (storm-local), 0..1. */
    private float litAt(float x, float z) {
        float edge = 0;
        if (!born && capFront > 0) { // being born: the edge of the spreading cloud glows
            float d = Mth.sqrt(x * x + z * z);
            edge = (float) Math.exp(-Math.abs(d - capFront) / (r * .35f)) * .9f * (1 - StormBirth.unfurl(t) * StormBirth.unfurl(t));
        }
        if (lit <= 0) return edge;
        float f = 1 - (Mth.square(x - litX) + Mth.square(z - litZ)) / litR2;
        return edge + (f > 0 ? lit * f * 1.4f : 0);
    }

    /** Column radius at height y (ignoring tiers): straight, opening up in the top fifth. */
    private float profile(float y) {
        float q = Math.max(0, (y / h - .8f) / .2f);
        return r * (1 + (TOP - 1) * q * q) * girth * form(y);
    }

    /**
     * Width of a newborn storm's funnel at height y as a share of its full width: nothing below its tip as it comes
     * down out of the cloud, a rope narrowing to that tip, filling out into the straight column once it touches down.
     */
    private float form(float y) {
        if (born) return 1;
        float u = (y - tipY) / Math.max(h - tipY, 1);
        if (u < 0) return 0;
        u = Math.min(u, 1);
        return Mth.lerp(fill, .08f + .92f * (1 - (1 - u) * (1 - u)), 1);
    }

    /** Sideways sway of a newborn funnel at height y: held by the cloud, the tip swinging like a rope. */
    private float swayX(float y) { return sway == 0 ? 0 : sway * (1 - Mth.clamp(y / h, 0, 1)) * Mth.sin(y / h * 5 + t * 1.3f); }

    private float swayZ(float y) { return sway == 0 ? 0 : sway * (1 - Mth.clamp(y / h, 0, 1)) * Mth.cos(y / h * 4 + t * 1.1f + 1); }

    /** Height of the seam under tier k (k = 0 is the bottom of the wall, TIERS its top), slightly tilted. */
    private float seam(int k, float ang) {
        return -SINK + k * (h + SINK) / TIERS + tilt[k] * Mth.sin(ang + tiltPhase[k]);
    }

    /** A straight cylinder made of tiers that differ only a little: shade, slight offset, each spinning at its own speed. */
    private void column(VertexConsumer vc) {
        int reps = Math.max(1, Math.round(TAU * r / 165)); // whole texture repeats around: ~1.3 blocks per texel
        float v0 = -frac(t * .015f);                       // streaks slowly rise
        // Shared by every tier: the angles' cos/sin and each seam's height along them.
        int n = COLUMN_SEG + 1;
        float[] cs = new float[n], sn = new float[n], seams = new float[(TIERS + 1) * n];
        for (int i = 0; i < n; i++) {
            float ang = i * TAU / COLUMN_SEG;
            cs[i] = Mth.cos(ang);
            sn[i] = Mth.sin(ang);
            for (int k = 0; k <= TIERS; k++) seams[k * n + i] = seam(k, ang);
        }
        for (int k = 0; k < TIERS; k++) {
            // a whole tier off screen is skipped before any of its vertices are worked out
            float ylo = -SINK + k * (h + SINK) / TIERS - tilt[k], yhi = -SINK + (k + 1) * (h + SINK) / TIERS + tilt[k + 1];
            if (yhi < tipY || !onScreen(offX[k], offZ[k], profile(Math.max(yhi, 0)) * 1.03f + sway, ylo, yhi)) continue;
            float u0 = -frac(t * spin[k] * reps);
            Row prev = rowA, cur = rowB;
            for (int j = 0; j <= TIER_ROWS; j++) {
                float s = j / (float) TIER_ROWS;
                for (int i = 0; i < n; i++) {
                    float c = cs[i], si = sn[i], y = Mth.lerp(s, seams[k * n + i], seams[(k + 1) * n + i]);
                    float rad = profile(y) * (1 + .015f * Mth.sin(3 * i * TAU / COLUMN_SEG + y * .04f - t * .5f));
                    float x = offX[k] + swayX(y) + rad * c, z = offZ[k] + swayZ(y) + rad * si;
                    float px = tx + x, py = ty + y, pz = tz + z, d = Mth.sqrt(px * px + py * py + pz * pz);
                    // facing: outward normal . (point - camera); > 0 means the camera sees the wall's back
                    float facing = c * px + si * pz;
                    boolean back = !camInside && facing > 0 && facing * facing > .0625f * (px * px + pz * pz);
                    // seen edge-on the wall thins out like a cloud; 0 alpha on the back lets strip() skip it
                    float a = back ? 0 : .92f * (d < 1e-3f ? 1 : .3f + .7f * smooth(Math.abs(facing) / d / .45f));
                    float shade = tone[k] * (.82f + .18f * Mth.clamp(y / h, 0, 1)) * light; // a bit lighter higher up
                    if (y < tipY) { // being born: the funnel hasn't come down this far yet
                        y = tipY;
                        a = 0;
                    }
                    cur.put(i, x, y, z, u0 + i * reps / (float) COLUMN_SEG, v0 + y / 96);
                    cur.wallColor(i, shade, a, hazeAt(d));
                }
                if (j > 0) strip(vc, prev, cur, n - 1);
                Row swap = prev; prev = cur; cur = swap;
            }
        }
    }

    /** Umbrella radius at q (0 = column top, 1 = rim; below 0 a lid over the column's mouth). */
    private float capRadius(float q) {
        return q < 0 ? TOP * r * (1 + q / .15f) : Mth.lerp(q, TOP * r, CAP * r);
    }

    /** Umbrella height at q: flat ledges with short drops between them, each ledge sagging a little. */
    private float capHeight(float q) {
        if (q <= 0) return h;
        float tq = Math.min(q, 1) * TERRACES, f = tq - Mth.floor(tq);
        float steps = Mth.floor(tq) + smooth((f - .6f) / .4f);
        return h - DROP * r * steps / TERRACES - .05f * r * Mth.sin(Mth.PI * f);
    }

    /** Cap brightness at q before day/night light: lit ledges, darker towards the middle. */
    private static float capShade(float q) {
        float ledge = Mth.sin(Mth.PI * frac(Math.max(q, 0) * TERRACES));
        return (.68f + .32f * ledge) * (.72f + .28f * Mth.clamp(q, 0, 1));
    }

    private int capReps() { return Math.max(1, Math.round(TAU * r * 2.5f / 200)); } // whole texture repeats around the cap

    /** The solid inner part of the storm's cloud cap: a stepped umbrella hanging from the column top, slowly turning. */
    private void umbrella(VertexConsumer vc) {
        if (capFront < 0) return; // being born: no cap yet
        capPass = true;
        int reps = capReps();
        float u0 = -frac(t * .004f * reps);
        Row prev = rowA, cur = rowB;
        // In the eye of an Overworld storm the lid over the column's mouth is left out: clear sky up there.
        int j0 = inEye && type == StormType.OVERWORLD ? Mth.ceil(.15f / (SOLID + .15f) * CAP_ROWS) : 0;
        for (int j = j0; j <= CAP_ROWS; j++) {
            float q = j / (float) CAP_ROWS * (SOLID + .15f) - .15f, rad = capRadius(q), y = capHeight(q), shade = capShade(q) * light, aw = .95f;
            if (rad > capFront) { // being born: the cloud hasn't spread this far yet
                rad = capFront;
                aw = 0;
            }
            for (int i = 0; i <= CAP_SEG; i++) {
                float ang = i * TAU / CAP_SEG;
                cur.wall(i, rad * Mth.cos(ang), y, rad * Mth.sin(ang), u0 + i * reps / (float) CAP_SEG, rad / 100, shade, aw);
            }
            if (j > j0) strip(vc, prev, cur, CAP_SEG);
            Row swap = prev; prev = cur; cur = swap;
        }
        capPass = false;
    }

    /**
     * The outer cap: the same cloud surface continued in rings of small pixel cells (still, not turning). Each cell
     * is there or not by its own random roll against a chance that drops towards the rim, so the round edge turns
     * into a ragged pixel edge that thins out into the sky. Only the per-frame part happens here; see rimRuns().
     */
    private void pixelRim(VertexConsumer vc) {
        if (capFront < 0) return;
        float[] runs = rimRuns();
        float u0 = -frac(t * .004f * capReps());
        // The hottest loop of the storm (thousands of runs): screen test first, then one color per run.
        for (int p = 0; p < runs.length; p += RUN_FLOATS) {
            float r0 = runs[p], r1 = runs[p + 1], y0 = ty + runs[p + 2], y1 = ty + runs[p + 3];
            if (r0 > capFront) continue; // being born: not spread this far yet
            float c0 = runs[p + 4], s0 = runs[p + 5], c1 = runs[p + 6], s1 = runs[p + 7];
            float ax = tx + r0 * c0, az = tz + r0 * s0, bx = tx + r0 * c1, bz = tz + r0 * s1;
            float cx = tx + r1 * c1, cz = tz + r1 * s1, dx = tx + r1 * c0, dz = tz + r1 * s0;
            if (!view.testAab(min4(ax, bx, cx, dx), Math.min(y0, y1), min4(az, bz, cz, dz),
                    max4(ax, bx, cx, dx), Math.max(y0, y1), max4(az, bz, cz, dz))) continue;
            float hz = haze((ax + cx) / 2, (y0 + y1) / 2, (az + cz) / 2), shade = runs[p + 10] * light;
            float l = litAt(r0 * (c0 + c1) * .5f, r0 * (s0 + s1) * .5f) * (1 - hz);
            float w = l * l * .6f; // white core of the flash
            int color = argb(runs[p + 11], Mth.lerp(hz, WALL_R * shade, fogR) + l * litCr + w, Mth.lerp(hz, WALL_G * shade, fogG) + l * litCg + w,
                    Mth.lerp(hz, WALL_B * shade, fogB) + l * litCb + w);
            float ua = u0 + runs[p + 8], ub = u0 + runs[p + 9];
            vertex(vc, ax, y0, az, ua, r0 / 100, color);
            vertex(vc, bx, y0, bz, ub, r0 / 100, color);
            vertex(vc, cx, y1, cz, ub, r1 / 100, color);
            vertex(vc, dx, y1, dz, ua, r1 / 100, color);
        }
    }

    /**
     * Lays out the rim's pixel cells once per storm: rolls every cell, then merges up to MAX_RUN neighbours on a ring
     * into one quad (the chord is off the circle by about a block that far out). Per run: inner/outer radius and height,
     * cos/sin of both ends, texture u of both ends, brightness, alpha.
     */
    private float[] rimRuns() {
        if (storm.rimCache != null) return storm.rimCache; // the client keeps one Storm object per storm and size
        int reps = capReps();
        float cell = r * .1f, from = capRadius(SOLID - .05f), to = capRadius(1), span = (CAP - TOP) * r;
        FloatArrayList out = new FloatArrayList();
        int rings = Mth.ceil((to - from) / cell);
        for (int k = 0; k < rings; k++) {
            float r0 = from + k * cell, r1 = r0 + cell, q = (r0 + cell / 2 - TOP * r) / span;
            float fade = smooth((q - SOLID) / (1 - SOLID)), keep = 1 - fade * fade; // dense near the solid part
            // a block under the solid cap: where the two overlap they'd fight over the same depth (sawtooth triangles)
            float y0 = capHeight((r0 - TOP * r) / span) - 1, y1 = capHeight((r1 - TOP * r) / span) - 1;
            float shade = capShade(q), a = .92f * (1 - .55f * fade);
            int n = Mth.ceil(TAU * r0 / cell);
            for (int i = 0; i < n; ) {
                long hash = Mth.getSeed(i, (int) seed, k);
                if ((hash >>> 16 & 0xFFFF) / 65535f > keep) { i++; continue; }
                float tone = .88f + .24f * (hash >>> 32 & 0xFFFF) / 65535f;
                int j = i + 1;
                while (j < n && j - i < MAX_RUN && (Mth.getSeed(j, (int) seed, k) >>> 16 & 0xFFFF) / 65535f <= keep) j++;
                float a0 = i * TAU / n, a1 = j * TAU / n;
                out.addElements(out.size(), new float[] {r0, r1, y0, y1, Mth.cos(a0), Mth.sin(a0), Mth.cos(a1), Mth.sin(a1),
                        i * reps / (float) n, j * reps / (float) n, shade * tone, a});
                i = j;
            }
        }
        float[] runs = out.toFloatArray();
        storm.rimCache = runs;
        return runs;
    }

    /** Glowing rings on the seams between tiers, plus the pale ring at the storm's foot. Same seed in both passes. */
    private void rings(VertexConsumer vc, boolean core) {
        pull = r * .06f; // clears the wall's wobble and tier offsets (~3% of the radius)
        RandomSource rnd = RandomSource.create(seed ^ 0x5DEECE66DL);
        int reps = Math.max(1, Math.round(TAU * r / 38)); // ~0.6 blocks per band texel
        for (int k = 0; k < TIERS; k++) { // k = 0 is the foot ring, the rest sit on the seams
            boolean foot = k == 0;
            float speed = .015f + rnd.nextFloat() * .02f, bright = .8f + rnd.nextFloat() * .2f;
            float halfW = r * (foot ? .055f : .044f) * (core ? 1 : 2.6f);
            float u0 = -frac(t * speed * reps * (core ? 1 : .3f)); // scale before frac, or the scroll jumps every lap
            StormType bt = storm.bandType(k); // a merged storm alternates its two kinds' colors
            int color = foot ? type.base : core ? bt.core : bt.glow;
            float cx = foot ? 0 : (offX[k - 1] + offX[k]) / 2, cz = foot ? 0 : (offZ[k - 1] + offZ[k]) / 2;
            float ry = foot ? r * .05f : -SINK + k * (h + SINK) / TIERS, tl = foot ? 0 : tilt[k];
            if (!onScreen(cx, cz, profile(Math.max(ry + tl, 0)) * 1.04f + 1 + sway, ry - tl - halfW, ry + tl + halfW)) continue; // after the random draws: same rings either way
            float ox = 0, oz = 0;
            if (!born) { // being born: the rings ride on the funnel, small and dim near its tip
                float f = form(ry);
                if (f <= 0) continue;
                bright *= Math.min(1, f * 1.4f);
                ox = swayX(ry);
                oz = swayZ(ry);
            }
            for (int i = 0; i <= RING_SEG; i++) {
                float ang = i * TAU / RING_SEG, c = Mth.cos(ang), s = Mth.sin(ang);
                float y = foot ? r * .05f : seam(k, ang), rad = profile(Math.max(y, 0)) * 1.035f;
                float x = cx + ox + rad * c, z = cz + oz + rad * s, u = u0 + i * reps / (float) RING_SEG;
                float a = (core ? bright : bright * .65f) * pulse * front(x - ox, z - oz, rad / 1.035f), hz = haze(tx + x, ty + y, tz + z);
                rowA.glow(i, x, y - halfW, z, u, 0, color, a, hz);
                rowB.glow(i, x, y + halfW, z, u, 1, color, a, hz);
            }
            strip(vc, rowA, rowB, RING_SEG);
        }
    }

    /**
     * Glowing light under the cap in broken circles: on each of several radii a few arcs of random length,
     * so the circles read as circles but have gaps and look scattered. Inner circles turn faster.
     */
    private void arcs(VertexConsumer vc, boolean core) {
        if (arcsIn <= 0) return; // being born: not yet
        pull = r * .06f;
        RandomSource rnd = RandomSource.create(~seed);
        for (int k = 0; k < ARC_RINGS; k++) {
            StormType bt = storm.bandType(k);
            int color = core ? bt.core : bt.glow;
            float q = .05f + (k + rnd.nextFloat() * .5f) * .85f / ARC_RINGS, rad = capRadius(q);
            float y = capHeight(q) - 4; // just under the cloud surface; drawn through it anyway (Types.THROUGH)
            float turn = frac(t * (.012f - .008f * q)) * TAU, halfW = (1.6f + rnd.nextFloat() * 1.4f) * (1 + q) * (core ? 1 : 2.8f);
            float fade = 1 - .6f * q, u0 = -frac(t * (.05f + rnd.nextFloat() * .05f) * (core ? 1 : .3f));
            int count = 2 + rnd.nextInt(3);
            for (int m = 0; m < count; m++) {
                float a0 = rnd.nextFloat() * TAU + turn, len = (.08f + rnd.nextFloat() * .3f) * TAU;
                float bright = (.6f + rnd.nextFloat() * .4f) * fade * (core ? 1 : .6f) * pulse * arcsIn;
                if (!onScreen(0, 0, rad + halfW, y - 1, y + 1)) continue; // after its random draws: the rest stay the same
                int n = Math.max(4, (int) (len / TAU * ARC_SEG));
                for (int i = 0; i <= n; i++) {
                    float f = i / (float) n, ang = a0 + len * f, c = Mth.cos(ang), s = Mth.sin(ang);
                    float a = bright * smooth(Math.min(f, 1 - f) * 6) * front(rad * c, rad * s, TOP * r), hz = haze(tx + rad * c, ty + y, tz + rad * s);
                    float u = u0 + len * f * rad / 40;
                    rowA.glow(i, (rad - halfW) * c, y, (rad - halfW) * s, u, 0, color, a, hz);
                    rowB.glow(i, (rad + halfW) * c, y, (rad + halfW) * s, u, 1, color, a, hz);
                }
                strip(vc, rowA, rowB, n);
            }
        }
    }

    /**
     * The running wave (Nether heat, End gravity): a glowing curtain on the ground, rolling out of the column and
     * fading as it goes. It follows the terrain where the client has it loaded.
     */
    private void wave(VertexConsumer vc, boolean core) {
        pull = 1;
        float f = born ? storm.waveFront(t * 20) : StormBirth.shock(t) < 0 ? -1 : StormBirth.shock(t) * storm.zone();
        if (f < 0 || !onScreen(0, 0, f + 1, -SINK - 40, 80)) return; // the curtain follows the ground, give it room
        var level = Minecraft.getInstance().level;
        float fade = (1 - f / storm.zone()) * (core ? .8f : .5f), height = core ? 4 : 12;
        int color = core ? type.core : type.glow, reps = Math.max(1, Math.round(TAU * f / 38));
        float u0 = -frac(t * .5f);
        for (int i = 0; i <= RING_SEG; i++) {
            float ang = i * TAU / RING_SEG, x = f * Mth.cos(ang), z = f * Mth.sin(ang);
            int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(sx + x), Mth.floor(sz + z));
            float y = gy > level.getMinBuildHeight() ? gy - sy - 1 : 0, hz = haze(tx + x, ty + y, tz + z), u = u0 + i * reps / (float) RING_SEG;
            rowA.glow(i, x, y, z, u, 0, color, fade, hz);
            rowB.glow(i, x, y + height, z, u, 1, color, 0, hz);
        }
        strip(vc, rowA, rowB, RING_SEG);
    }

    /** End storms: paired seams in the air, flickering in step with their partner. */
    private void seams(VertexConsumer vc, boolean core) {
        pull = 0; // they stand among terrain: pulled forward they'd shine through hills
        float w = core ? .12f : .5f;
        for (Storm.Mark m : storm.marks) {
            if (m.kind() != Storm.Mark.SEAM) continue;
            float x = m.x() + .5f - sx, y = m.y() - sy, z = m.z() + .5f - sz;
            float flick = .6f + .4f * Mth.sin(t * 9 + m.a() * 1.7f) * Mth.sin(t * 23 + m.a());
            float a = (core ? 1.2f : .8f) * flick, jag = .12f * Mth.sin(t * 31 + m.a() * 5);
            int color = core ? type.core : type.glow;
            for (int side = 0; side < 2; side++) { // two crossed planes, so it reads from every side
                float ex = side == 0 ? w : 0, ez = side == 0 ? 0 : w;
                for (int i = 0; i <= 2; i++) {
                    float yy = y + i * 1.3f, aa = i == 1 ? a : 0, jx = i == 1 ? jag * (side == 0 ? 0 : 1) : 0, jz = i == 1 ? jag * (side == 0 ? 1 : 0) : 0;
                    rowA.glow(i, x - ex + jx, yy, z - ez + jz, i * .5f, 0, color, aa, 0);
                    rowB.glow(i, x + ex + jx, yy, z + ez + jz, i * .5f, 1, color, aa, 0);
                }
                strip(vc, rowA, rowB, 2);
            }
        }
    }

    /**
     * 1 where glow at storm-local x/z is in plain view, 0 where the column (radius wallR there) stands between it and
     * the camera, soft at the edge: does the line from the camera to it pass through the column? Seen from the
     * side only, the column being full height. The wall can't be trusted to hide it by depth: its texture is
     * partly transparent, and those pixels write no depth.
     */
    private float front(float x, float z, float wallR) {
        if (camInside) return 1;
        float cx = -tx, cz = -tz, dx = x - cx, dz = z - cz;
        float s = Mth.clamp(-(cx * dx + cz * dz) / (dx * dx + dz * dz), 0, 1); // where the line passes nearest the axis
        float px = cx + dx * s, pz = cz + dz * s;
        return smooth((Mth.sqrt(px * px + pz * pz) - wallR * .95f) / (wallR * .06f));
    }

    /**
     * Quads between two rows. Skipped when all four corners are invisible (weight ~0) or the quad is off screen:
     * the cap reaches far past the camera, so a good share of it is always behind or beside the view.
     */
    private void strip(VertexConsumer vc, Row a, Row b, int n) {
        for (int i = 0; i < n; i++) {
            if (max4(a.w[i], a.w[i + 1], b.w[i], b.w[i + 1]) < .02f) continue;
            if (!view.testAab(min4(a.x[i], a.x[i + 1], b.x[i], b.x[i + 1]), min4(a.y[i], a.y[i + 1], b.y[i], b.y[i + 1]),
                    min4(a.z[i], a.z[i + 1], b.z[i], b.z[i + 1]), max4(a.x[i], a.x[i + 1], b.x[i], b.x[i + 1]),
                    max4(a.y[i], a.y[i + 1], b.y[i], b.y[i + 1]), max4(a.z[i], a.z[i + 1], b.z[i], b.z[i + 1]))) continue;
            vertex(vc, a, i);
            vertex(vc, a, i + 1);
            vertex(vc, b, i + 1);
            vertex(vc, b, i);
        }
    }

    private static void vertex(VertexConsumer vc, Row row, int i) {
        vertex(vc, row.x[i], row.y[i], row.z[i], row.u[i], row.v[i], row.color[i]);
    }

    /** One vertex in either storm format (BLOCK or NEW_ENTITY); the format just skips what it doesn't have. */
    private static void vertex(VertexConsumer vc, float x, float y, float z, float u, float v, int color) {
        vc.addVertex(x, y, z, color, u, v, OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT, 0, 1, 0);
    }

    /** A line of camera-relative vertices; quads get stretched between two rows. */
    private final class Row {
        final float[] x = new float[MAX_ROW], y = new float[MAX_ROW], z = new float[MAX_ROW], u = new float[MAX_ROW], v = new float[MAX_ROW];
        final int[] color = new int[MAX_ROW];
        final float[] w = new float[MAX_ROW]; // how visible the vertex is (alpha or glow brightness)

        /** Storm-cloud vertex (alpha blended), hazed toward the fog color with distance. */
        void wall(int i, float px, float py, float pz, float pu, float pv, float shade, float a) {
            put(i, px, py, pz, pu, pv);
            wallColor(i, shade, a, haze(x[i], y[i], z[i]));
        }

        void wallColor(int i, float shade, float a, float hz) {
            w[i] = a;
            float l = capPass ? litAt(x[i] - tx, z[i] - tz) * (1 - hz) : 0;
            float wh = l * l * .6f; // white core of the flash
            color[i] = argb(a, Mth.lerp(hz, WALL_R * shade, fogR) + l * litCr + wh, Mth.lerp(hz, WALL_G * shade, fogG) + l * litCg + wh,
                    Mth.lerp(hz, WALL_B * shade, fogB) + l * litCb + wh);
        }

        /** Additive glow vertex: additive blending ignores alpha, so brightness a scales the color, dimmed by haze hz. */
        void glow(int i, float px, float py, float pz, float pu, float pv, int rgb, float a, float hz) {
            put(i, px, py, pz, pu, pv);
            // Pulled towards the camera along its own sight line: same pixel on screen, but nearer in depth, so the
            // wall (which writes depth and wobbles and flares around the bands) can't swallow it.
            if (pull > 0 && a > 0) {
                float d = Mth.sqrt(x[i] * x[i] + y[i] * y[i] + z[i] * z[i]), f = d > pull * 2 ? 1 - pull / d : .5f;
                x[i] *= f;
                y[i] *= f;
                z[i] *= f;
            }
            float k = w[i] = a * (1 - hz * .6f);
            color[i] = argb(1, (rgb >> 16 & 255) / 255f * k, (rgb >> 8 & 255) / 255f * k, (rgb & 255) / 255f * k);
        }

        /** Stores a storm-local vertex relative to the camera. */
        void put(int i, float px, float py, float pz, float pu, float pv) {
            x[i] = tx + px;
            y[i] = ty + py;
            z[i] = tz + pz;
            u[i] = pu;
            v[i] = pv;
        }
    }

    /**
     * Fullbright, double-sided, and without vanilla fog: the storm is far bigger than the fog distance,
     * so it would fade out entirely; it gets its own distance haze instead (haze()).
     * Shader packs (Iris) swap vanilla shaders for their own programs: the wall uses the beacon-beam shader and the
     * glow the "eyes" shader because packs draw those as self-lit, without sun shadows and terrain lighting (with
     * the plain textured shader the edge of the pack's shadow map cut the cap into big triangles).
     * The wall writes depth: packs treat depth-less pixels as open sky and paint sky fog over them. Its parts are
     * drawn far to near (draw()), so the depth doesn't hide any of it.
     */
    private static final class Types extends RenderType {
        static final Function<ResourceLocation, RenderType> WALL = Util.memoize(tex -> create("horizons_unbound_wall",
                DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 1536, false, false,
                state(tex, RENDERTYPE_BEACON_BEAM_SHADER, TRANSLUCENT_TRANSPARENCY, COLOR_DEPTH_WRITE)));
        static final Function<ResourceLocation, RenderType> GLOW = Util.memoize(tex -> create("horizons_unbound_glow",
                DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, false,
                state(tex, RENDERTYPE_EYES_SHADER, ADDITIVE_TRANSPARENCY, COLOR_WRITE)));

        private static float savedFogStart;
        // vanilla fog starts past everything while the storm draws (both shaders apply it)
        private static final TexturingStateShard NO_FOG = new TexturingStateShard("horizons_unbound_no_fog", () -> {
            savedFogStart = RenderSystem.getShaderFogStart();
            RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        }, () -> RenderSystem.setShaderFogStart(savedFogStart));

        static final Function<ResourceLocation, RenderType> THROUGH = Util.memoize(tex -> create("horizons_unbound_glow_through",
                DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, false,
                CompositeState.builder().setShaderState(RENDERTYPE_EYES_SHADER).setTextureState(new TextureStateShard(tex, false, false))
                        .setTransparencyState(ADDITIVE_TRANSPARENCY).setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE)
                        .setDepthTestState(NO_DEPTH_TEST).setTexturingState(NO_FOG).createCompositeState(false)));

        private static CompositeState state(ResourceLocation tex, ShaderStateShard shader, TransparencyStateShard blend, WriteMaskStateShard mask) {
            return CompositeState.builder().setShaderState(shader).setTextureState(new TextureStateShard(tex, false, false))
                    .setTransparencyState(blend).setCullState(NO_CULL).setWriteMaskState(mask).setTexturingState(NO_FOG)
                    .createCompositeState(false);
        }

        private Types(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
            super(name, format, mode, size, crumbling, sort, setup, clear); // never called: only here to reach RenderType's protected parts
        }
    }

    private static int argb(float a, float r, float g, float b) {
        return (int) (Mth.clamp(a, 0, 1) * 255) << 24 | (int) (Mth.clamp(r, 0, 1) * 255) << 16
                | (int) (Mth.clamp(g, 0, 1) * 255) << 8 | (int) (Mth.clamp(b, 0, 1) * 255);
    }

    /** Distance haze for a camera-relative point: none up to 150 blocks, up to half fog color far away. */
    private static float haze(float x, float y, float z) { return hazeAt(Mth.sqrt(x * x + y * y + z * z)); }

    private static float hazeAt(float dist) { return Mth.clamp((dist - 150) / 1000, 0, .5f); }

    private static float min4(float a, float b, float c, float d) { return Math.min(Math.min(a, b), Math.min(c, d)); }
    private static float max4(float a, float b, float c, float d) { return Math.max(Math.max(a, b), Math.max(c, d)); }
    private static float frac(float x) { return x - Mth.floor(x); }
    private static float smooth(float x) { x = Mth.clamp(x, 0, 1); return x * x * (3 - 2 * x); }
    private static ResourceLocation tex(String name) { return ResourceLocation.fromNamespaceAndPath(HorizonsUnbound.MODID, "textures/storm/" + name + ".png"); }
}
