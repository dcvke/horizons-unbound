package com.horizonsunbound.client;

import com.horizonsunbound.HorizonsUnbound;
import com.horizonsunbound.Storm;
import com.horizonsunbound.StormType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
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

import it.unimi.dsi.fastutil.floats.FloatArrayList;

import java.util.function.Function;

/**
 * Draws a storm the way the reference footage shows it: a straight column of stacked cloud tiers with glowing
 * glitch-pixel rings on the seams, a pale ring at its foot, and on top a stepped cloud umbrella with broken glowing circles
 * and a ragged pixel rim. Everything uses pixel-art textures and is rebuilt every frame from the storm's size and UUID.
 */

public final class StormRenderer {
    private static final ResourceLocation WALL = tex("storm_wall"), BAND = tex("storm_band"), GLOW = tex("storm_glow"), FX = tex("storm_fx");
    private static final int DOT = 0, BEAM = 1, RING = 2, SPARK = 3; // cells of storm_fx (tools/make_fx.py)
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
    private final float capFront, arcsIn, spinUp;
    private final float dying, capFade;       // its death: 0..1 through it; the cloud cap thinning at the end
    private static final float WIDE = 3.4f;   // being born: how many times its width the vortex starts out
    private final float wavePhase;            // ...and where along its height it closes in first
    private final float outside;              // 0 with the camera inside the column, 1 well outside it, soft across the wall
    // light inside the cloud cap (see cloudLight()): where, how wide, how strong, which color
    private float litX, litZ, litR2 = 1, lit;
    private final float litCr, litCg, litCb;
    private boolean capPass;
    private float pull;                       // blocks each glow vertex is pulled towards the camera, see Row.glow()                // ring brightness: 1, up to 3.5 while the column flares before a wave
    private final StormType type;
    private final long seed;
    private final boolean inEye;            // camera truly inside the column (for the eye's sky)
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
        float closed = born ? 1 : StormBirth.descend(t);
        dying = storm.dying();
        spinUp = .35f * closed * closed * closed + .5f * dying * dying; // it turns faster as it tightens (kept on afterwards, so nothing jumps), and unwinds as it dies
        capFade = 1 - smooth((dying - .55f) / .45f);
        wavePhase = (storm.seed() & 255) / 40f;
        float unfurl = born ? 1 : StormBirth.unfurl(t);
        capFront = born ? dying > .3f ? r * (TOP * .6f + (CAP - TOP * .6f) * (1 - StormBirth.ease((dying - .3f) / .7f))) : Float.MAX_VALUE : unfurl <= 0 ? -1 : r * (TOP * .6f + (CAP - TOP * .6f) * unfurl);
        arcsIn = born ? 1 - smooth(dying * 3) : StormBirth.arcs(t);
        sx = (float) Mth.lerp(partial, storm.xo, storm.x);
        sy = (float) Mth.lerp(partial, storm.yo, storm.y);
        sz = (float) Mth.lerp(partial, storm.zo, storm.z);
        float c = storm.charge(storm.age + partial);
        heard = heard(storm, partial);
        pulse = 1 + 1.6f * c + 2 * heard + (born ? 0 : 2.5f * StormBirth.settle(t) + 3 * StormBirth.snap(t)); // a wave's warning, a sculk storm answering, the birth's snap and last pulse
        litCr = (storm.type.glow >> 16 & 255) / 255f;
        litCg = (storm.type.glow >> 8 & 255) / 255f;
        litCb = (storm.type.glow & 255) / 255f;
        this.light = light;
        type = storm.type;
        seed = storm.seed();
        // How far outside the wall the camera is, at its own height (under the cap). Soft across the wall: near it the
        // wall thins like cloud, the far side and what the wall hid fade in, so walking through never blanks the storm.
        outside = -ty > h ? 1 : smooth((Mth.sqrt(tx * tx + tz * tz) - profile(Mth.clamp(-ty, 0, h)) * 1.02f) / (r * .2f));
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

    private static final java.util.Set<java.util.UUID> FAILED = new java.util.HashSet<>(); // storms whose drawing threw: no longer drawn
    private static java.lang.invoke.MethodHandle irisApi, irisInUse, irisPackName; // Iris's API and pack name, if Iris is there (looked up once)
    private static boolean irisLooked;

    /** True while an Iris shader pack is in use (asked through Iris's own API; false without Iris). */
    static boolean shaderPack() {
        if (!irisLooked) {
            irisLooked = true;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                var lookup = java.lang.invoke.MethodHandles.publicLookup();
                irisApi = lookup.findStatic(api, "getInstance", java.lang.invoke.MethodType.methodType(api));
                irisInUse = lookup.findVirtual(api, "isShaderPackInUse", java.lang.invoke.MethodType.methodType(boolean.class));
                try {
                    irisPackName = lookup.findStatic(Class.forName("net.irisshaders.iris.Iris"), "getCurrentPackName", java.lang.invoke.MethodType.methodType(String.class));
                } catch (ReflectiveOperationException | LinkageError e) {
                    irisPackName = null; // not part of Iris's public API: may go away; then auto means classic
                }
            } catch (ReflectiveOperationException | LinkageError e) {
                irisApi = null;
            }
        }
        if (irisApi == null) return false;
        try {
            return (boolean) irisInUse.invoke(irisApi.invoke());
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * True when the storm should be drawn with the plain textured shader, blended by alpha (StormConfig.SHADER_MODE):
     * for packs like Photon, which treat beacon beams and glowing eyes as solid lit surfaces (the wall showed as a
     * hole, the glow vanished). Other packs keep the beam/eyes shaders, which they draw as glowing.
     */
    static boolean forwardPack() {
        if (!shaderPack()) return false;
        StormConfig.ShaderMode mode = StormConfig.SHADER_MODE.get();
        if (mode != StormConfig.ShaderMode.AUTO) return mode == StormConfig.ShaderMode.FORWARD;
        try {
            return irisPackName != null && String.valueOf(irisPackName.invoke()).toLowerCase(java.util.Locale.ROOT).contains("photon");
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * When the storm is drawn. Plain: after clouds and rain, so they don't paint over it (under a storm they're hidden
     * anyway). With a shader pack: with the particles, a pass every pack composites (some packs drop what is drawn
     * after the weather, and the storm vanished).
     */
    public static boolean particlesStage() {
        return shaderPack();
    }

    // Drawn after clouds and rain, so they don't paint over the storm (under a storm they're hidden anyway).

    public static void onRenderLevel(Vec3 cam, float partial) {
        if (ClientStorms.all().isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        // 1 at noon, .2 at night; the fullbright storm darkens with the sky, but not so far that it vanishes
        float light = Math.max(.35f, mc.level.getSkyDarken(partial));
        // vertices are camera-relative and the camera rotation sits in the model-view matrix at this stage
        FrustumIntersection view = new FrustumIntersection(new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()));
        int viewDistance = StormConfig.viewDistance(mc.options.getEffectiveRenderDistance());
        for (Storm storm : ClientStorms.all()) {
            float tx = (float) (Mth.lerp(partial, storm.xo, storm.x) - cam.x), ty = (float) (Mth.lerp(partial, storm.yo, storm.y) - cam.y);
            float tz = (float) (Mth.lerp(partial, storm.zo, storm.z) - cam.z), reach = storm.radius() * CAP;
            // drawn while any part of it is within the view distance, and only when it's on screen at all
            if (Mth.sqrt(tx * tx + tz * tz) - reach > viewDistance && !Cutscene.about(storm.id)) continue; // a cutscene's storm, however far
            if (!view.testAab(tx - reach, ty - SINK, tz - reach, tx + reach, ty + storm.radius() * HEIGHT + 10, tz + reach)) continue;
            if (FAILED.contains(storm.id)) continue;
            try {
                new StormRenderer(storm, tx, ty, tz, partial, light, view).draw();
            } catch (RuntimeException e) { // never crash the game over a storm's looks: log it, drop the half-built mesh, stop drawing that storm
                FAILED.add(storm.id);
                Tesselator.getInstance().clear();
                LogUtils.getLogger().error("Horizons Unbound: a storm could not be drawn and is hidden from now on", e);
            }
        }
    }


    /**
     * The passes are drawn right here, one after the other, not through the shared buffer source: mods that batch
     * it (Iris, Sodium) flush it in their own order, which drew the bands first and the black wall over them.
     */
    private void draw() {
        // The wall isn't depth-sorted (thousands of quads every frame); instead the far part goes first:
        // seen from below the cap is behind the column, seen from above it covers it.
        RenderType wallType = Types.wall(WALL);
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
        RenderType glowType = Types.glow(GLOW);
        BufferBuilder glow = begin(glowType);
        rings(glow, false);
        wave(glow, false);
        seams(glow, false);
        stars(glow);
        aurora(glow);
        end(glow, glowType);
        RenderType coreType = Types.glow(BAND);
        BufferBuilder core = begin(coreType);
        rings(core, true);
        wave(core, true);
        seams(core, true);
        end(core, coreType);
        if (!born || dying > 0) { // the birth's (or death's) light: soft glows, never a hard edge
            RenderType fxType = Types.glow(FX);
            BufferBuilder fx = begin(fxType);
            birth(fx);
            end(fx, fxType);
        }
        // The arcs inside the cloud cap shine through the cloud itself (from above and from the side too), but not
        // through blocks: depth-tested, pulled towards the camera so the cap's sheet can't hide them (arcs()).
        // Only the column hides the far ones (front()).
        BufferBuilder arcs = begin(glowType);
        arcs(arcs, false);
        end(arcs, glowType);
        arcs = begin(coreType);
        arcs(arcs, true);
        end(arcs, coreType);
    }

    static RenderType glowType() { return Types.glow(GLOW); }

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
        if (arcsIn * outside <= 0) return; // (from inside the column the wall hides it)
        if (type != StormType.OVERWORLD || tx * tx + tz * tz > Mth.square(r * CAP)) return;
        pull = 0;
        int[] colors = {0x46FF9A, 0x40E8FF, 0x6A8CFF};
        for (int k = 0; k < 3; k++) {
            float base = r * (1.9f + .55f * k), q = (base - TOP * r) / ((CAP - TOP) * r), top = capHeight(q) - 3, depth = 26 + 8 * k;
            if (!onScreen(0, 0, base + r * .13f, top - depth, top)) continue;
            for (int i = 0; i <= RING_SEG; i++) {
                float ang = i * TAU / RING_SEG, rad = base + r * .12f * Mth.sin(ang * 3 + t * .25f + k);
                float x = rad * Mth.cos(ang), z = rad * Mth.sin(ang), shimmer = .6f + .4f * Mth.sin(ang * 5 - t * .6f + k * 2);
                float a = .9f * shimmer * arcsIn * outside, hz = haze(tx + x, ty + top, tz + z), u = i * 6f / RING_SEG;
                rowA.glow(i, x, top - depth, z, u, 0, colors[k], 0, hz);
                rowB.glow(i, x, top, z, u, 1, colors[k], a, hz);
            }
            strip(vc, rowA, rowB, RING_SEG);
            strip(vc, rowA, rowB, RING_SEG); // twice: additive, the curtains need the brightness
        }
    }

    /**
     * The light of a storm's birth (StormBirth), all of it soft (storm_fx: glows that fade out to nothing):
     * sparks with little trails spiralling in to a seed of light that pools on the ground, a ring of light rushing
     * out at ignition, the beam (a white-hot core in a wide halo, flaring where it hits the sky), motes swept round
     * with the vortex as it closes in, and as it shuts a flash, a ring rolling out over the ground and a burst of embers.
     * A few hundred quads, and only for the first seconds.
     */
    private void birth(VertexConsumer vc) {
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        org.joml.Vector3f left = cam.getLeftVector(), up = cam.getUpVector();
        lx = left.x(); ly = left.y(); lz = left.z();
        ux = up.x(); uy = up.y(); uz = up.z();
        if (born) { // dying: motes of light lifting off where the column comes apart, drifting up into the thinning cloud
            float front = Mth.clamp(dying * 1.6f - .45f, 0, 1) * h, fadeOut = 1 - smooth((dying - .8f) / .2f);
            RandomSource rnd = RandomSource.create(seed * 17);
            for (int i = 0; i < 56; i++) {
                float ang = rnd.nextFloat() * TAU + t * (.2f + .3f * rnd.nextFloat()), up2 = frac(t * .22f + rnd.nextFloat());
                float y = Math.min(front + up2 * r * .9f, h), rad = profile(y) * (.85f + .35f * rnd.nextFloat());
                sprite(vc, DOT, rad * Mth.cos(ang), y, rad * Mth.sin(ang), 4 + 7 * up2, type.glow, Mth.sin(Mth.PI * up2) * fadeOut * .35f);
            }
            return;
        }
        ux = up.x(); uy = up.y(); uz = up.z();
        int glow = type.glow, core = type.core;
        float gather = StormBirth.gather(t);
        if (gather > 0) { // the gathering: the storm's footprint lights up on the ground and winds itself in to the seed
            float g = Mth.clamp(t / StormBirth.IGNITION, 0, 1), in = g * g; // slow at first, then rushing
            RandomSource rnd = RandomSource.create(seed * 7);
            for (int i = 0; i < 3; i++) { // rings of light closing in over the ground, one after another, quicker each time
                float ph = frac(t * (.5f + .7f * g) + i / 3f);
                flatFx(vc, RING, 0, .5f, 0, r * 3.2f * (1 - ph) + r * .2f, glow, Mth.sin(Mth.PI * ph) * gather * (.35f + .5f * g));
            }
            for (int arm = 0; arm < 5; arm++) { // five arms of motes, a little galaxy turning ever faster as it tightens
                for (int j = 1; j <= 16; j++) {
                    float q = j / 16f, rad = r * 1.7f * q * (1 - .9f * in) + r * .04f;
                    float ang = arm * TAU / 5 + q * 2.6f + t * (.8f + 3.5f * in), y = 1.2f + q * r * .12f * (1 - in) + .6f * Mth.sin(t * 3 + j);
                    float k = gather * (.35f + .65f * g) * (1 - .5f * q);
                    sprite(vc, DOT, rad * Mth.cos(ang), y, rad * Mth.sin(ang), 6 - 2.5f * q, glow, k * .4f);
                    sprite(vc, DOT, rad * Mth.cos(ang), y, rad * Mth.sin(ang), 1.8f, core, k);
                }
            }
            for (int i = 0; i < 44; i++) { // sparks lifting off the ground inside the footprint and bending in towards the seed
                float ang0 = rnd.nextFloat() * TAU, d0 = r * (.25f + 1.1f * Mth.sqrt(rnd.nextFloat())), off = rnd.nextFloat(), top = r * (.25f + .4f * rnd.nextFloat());
                float life = frac(t * (.45f + .5f * g) + off), d = d0 * (1 - life * life * (.5f + .5f * g)), ang = ang0 + life * 1.5f;
                float k = Mth.sin(Mth.PI * life) * gather * (.4f + .6f * g);
                sprite(vc, SPARK, d * Mth.cos(ang), 1 + top * life * life, d * Mth.sin(ang), 2.2f + 2 * life, core, k * .9f);
            }
        }
        float seedK = StormBirth.seed(t);
        if (seedK > 0) {
            sprite(vc, DOT, 0, 2, 0, r * .6f * (.6f + seedK), glow, seedK * .55f);
            sprite(vc, DOT, 0, 2, 0, r * .14f * (.6f + seedK), core, seedK * 1.5f);
            flatFx(vc, DOT, 0, .4f, 0, r * 1.1f, glow, seedK * .45f); // its light pooling on the ground
        }
        float ig = (t - StormBirth.IGNITION) / 1.2f;
        if (ig > 0 && ig < 1) { // a ring of light rushing out over the ground at ignition
            flatFx(vc, RING, 0, .6f, 0, r * (.4f + 4 * StormBirth.ease(ig)), glow, (1 - ig) * (1 - ig) * 1.3f);
            flatFx(vc, RING, 0, .7f, 0, r * (.3f + 3.6f * StormBirth.ease(ig)), core, (1 - ig) * (1 - ig) * .7f);
        }
        float beam = StormBirth.beam(t);
        if (beam > 0) { // the beam, as tall as the storm will be
            float top = StormBirth.beamHeight(t) * (h + 40), flick = .85f + .15f * Mth.sin(t * 41) * Mth.sin(t * 23);
            float len = Mth.sqrt(tx * tx + tz * tz) + 1e-3f, px = tz / len, pz = -tx / len; // across the view, horizontal
            pillar(vc, px, pz, -SINK, top, r * .55f, glow, beam * .5f);
            pillar(vc, px, pz, -SINK, top, r * .16f, glow, beam);
            pillar(vc, px, pz, -SINK, top, r * .045f, core, beam * 1.6f * flick);
            float hit = (t - StormBirth.IGNITION - .2f) / 1.5f; // it splashes against the sky
            if (hit > 0 && hit < 1) sprite(vc, DOT, 0, top, 0, r * (1 + 2 * hit), glow, beam * (1 - hit) * 1.2f);
            sprite(vc, DOT, 0, 2, 0, r * .5f, core, beam * .5f * flick);
        }
        float dust = StormBirth.dust(t);
        if (dust > 0) { // motes of light swept round with the vortex, faster and faster as it closes in
            RandomSource rnd = RandomSource.create(seed * 13);
            float turned = t * .5f + spinUp * 40;
            for (int i = 0; i < 64; i++) {
                float ang = rnd.nextFloat() * TAU + turned * (.7f + .6f * rnd.nextFloat()), y = rnd.nextFloat() * h * .85f;
                float rad = profile(y) * (.8f + .35f * rnd.nextFloat()), k = dust * (.5f + .5f * Mth.sin(t * 3 + i));
                sprite(vc, DOT, rad * Mth.cos(ang), y + r * .05f * Mth.sin(t + i), rad * Mth.sin(ang), 5 + 9 * rnd.nextFloat(), glow, k * .32f);
            }
        }
        float flash = Accessibility.flash(StormBirth.flash(t));
        if (flash > 0) {
            flatFx(vc, DOT, 0, 1, 0, r * 5 * (1.2f - flash * .2f), glow, flash * .9f);
            sprite(vc, DOT, 0, r * .2f, 0, r * 1.6f, core, flash * .5f);
        }
        float shock = StormBirth.shock(t);
        if (shock >= 0) flatFx(vc, RING, 0, .8f, 0, storm.zone() * 2 * shock + r, glow, (1 - shock) * 1.1f); // rolling out over the ground
        float burst = t - StormBirth.TOUCHDOWN;
        if (burst > 0 && burst < 2.5f) { // embers flung out of the touchdown
            RandomSource rnd = RandomSource.create(seed * 19);
            float k = 1 - burst / 2.5f;
            for (int i = 0; i < 48; i++) {
                float ang = rnd.nextFloat() * TAU, sp = r * (.4f + rnd.nextFloat() * .8f), vy = r * (.3f + rnd.nextFloat() * .7f);
                float d = sp * burst, y = Math.max(1, vy * burst - r * .35f * burst * burst), x = d * Mth.cos(ang), z = d * Mth.sin(ang);
                sprite(vc, DOT, x, y, z, 6, glow, k * .5f);
                sprite(vc, DOT, x, y, z, 2, core, k * 1.2f);
            }
        }
    }

    private float lx, ly, lz, ux, uy, uz; // the camera's left and up, for sprites

    /** A soft glow facing the camera (storm-local centre, world size): one cell of storm_fx. */
    private void sprite(VertexConsumer vc, int cell, float x, float y, float z, float size, int rgb, float k) {
        float s = size / 2, cx = tx + x, cy = ty + y, cz = tz + z, u0 = (cell & 1) * .5f + .004f, v0 = (cell >> 1) * .5f + .004f, u1 = u0 + .492f, v1 = v0 + .492f;
        int color = fxColor(rgb, k);
        vertex(vc, cx + (lx - ux) * s, cy + (ly - uy) * s, cz + (lz - uz) * s, u0, v1, color);
        vertex(vc, cx + (-lx - ux) * s, cy + (-ly - uy) * s, cz + (-lz - uz) * s, u1, v1, color);
        vertex(vc, cx + (-lx + ux) * s, cy + (-ly + uy) * s, cz + (-lz + uz) * s, u1, v0, color);
        vertex(vc, cx + (lx + ux) * s, cy + (ly + uy) * s, cz + (lz + uz) * s, u0, v0, color);
    }

    /** The beam cell standing upright on the storm's axis, turned across the view, from y0 to y1. */
    private void pillar(VertexConsumer vc, float px, float pz, float y0, float y1, float w, int rgb, float k) {
        int color = fxColor(rgb, k);
        float hw = w / 2, u0 = .504f, u1 = .996f, v0 = .004f, v1 = .496f;
        vertex(vc, tx - px * hw, ty + y0, tz - pz * hw, u0, v1, color);
        vertex(vc, tx + px * hw, ty + y0, tz + pz * hw, u1, v1, color);
        vertex(vc, tx + px * hw, ty + y1, tz + pz * hw, u1, v0, color);
        vertex(vc, tx - px * hw, ty + y1, tz - pz * hw, u0, v0, color);
    }

    /** A soft glow lying flat (storm-local centre, world size): one cell of storm_fx. */
    private void flatFx(VertexConsumer vc, int cell, float x, float y, float z, float size, int rgb, float k) {
        float s = size / 2, cx = tx + x, cy = ty + y, cz = tz + z, u0 = (cell & 1) * .5f + .004f, v0 = (cell >> 1) * .5f + .004f, u1 = u0 + .492f, v1 = v0 + .492f;
        int color = fxColor(rgb, k);
        vertex(vc, cx - s, cy, cz - s, u0, v0, color);
        vertex(vc, cx + s, cy, cz - s, u1, v0, color);
        vertex(vc, cx + s, cy, cz + s, u1, v1, color);
        vertex(vc, cx - s, cy, cz + s, u0, v1, color);
    }

    private static int fxColor(int rgb, float k) { return argb(1, (rgb >> 16 & 255) / 255f * k, (rgb >> 8 & 255) / 255f * k, (rgb & 255) / 255f * k); }

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
            lit = .14f + .1f * Mth.sin(t * .8f) + .6f * Accessibility.flash(heard);
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
        lit = Accessibility.noFlashes() ? .25f * (1 - dt / .4f) // no flicker: the spot just glows up and fades
                : dt < .07f ? 1 : dt < .14f ? .25f : dt < .21f ? .8f : (.4f - dt) / .19f * .5f;
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
        return r * (1 + (TOP - 1) * q * q) * wide(y);
    }

    /**
     * A newborn storm closes in: 0 when the vortex at height y starts to form, 1 when it has shut into the column.
     * It starts a little earlier or later along its height (a slow ripple), but all of it shuts at the same moment.
     */
    private float closing(float y) {
        float start = StormBirth.DESCENT + .9f * (.5f + .5f * Mth.sin(y / h * 7 + wavePhase));
        return (t - start) / (StormBirth.TOUCHDOWN - start);
    }

    /** Width at height y as a multiple of the whole storm's: a wide vortex tightening ever faster, a pinch as it shuts, settling. */
    private float wide(float y) {
        if (born) return 1 + .8f * dying * dying; // dying, it unwinds and spreads out
        float u = closing(y);
        if (u <= 0) return WIDE;
        if (u < 1) return 1 + (WIDE - 1) * (1 - u * u * u);
        float after = t - StormBirth.TOUCHDOWN;
        return 1 - .1f * (float) Math.exp(-after * 2.2f) * Mth.sin(after * 6);
    }

    /** How solid a newborn storm is at height y: a ghost of a vortex at first, whole as it shuts. */
    private float vis(float y) {
        if (born) return dying <= 0 ? 1 : smooth((y / h - (dying * 1.6f - .45f)) / .25f); // dying, it lifts off the ground and comes apart upwards
        float u = closing(y);
        return u <= 0 ? 0 : u >= 1 ? 1 : smooth(u * 2.5f) * (.2f + .8f * u * u * u);
    }

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
            if (!onScreen(offX[k], offZ[k], (born ? profile(Math.max(yhi, 0)) : r * TOP * WIDE) * 1.03f, ylo, yhi)) continue;
            float u0 = -frac((t * spin[k] + spinUp) * reps);
            Row prev = rowA, cur = rowB;
            for (int j = 0; j <= TIER_ROWS; j++) {
                float s = j / (float) TIER_ROWS;
                for (int i = 0; i < n; i++) {
                    float c = cs[i], si = sn[i], y = Mth.lerp(s, seams[k * n + i], seams[(k + 1) * n + i]);
                    float rad = profile(y) * (1 + .015f * Mth.sin(3 * i * TAU / COLUMN_SEG + y * .04f - t * .5f));
                    float x = offX[k] + rad * c, z = offZ[k] + rad * si;
                    float px = tx + x, py = ty + y, pz = tz + z, d = Mth.sqrt(px * px + py * py + pz * pz);
                    // facing: outward normal . (point - camera); > 0 means the camera sees the wall's back
                    float facing = c * px + si * pz;
                    boolean back = facing > 0 && facing * facing > .0625f * (px * px + pz * pz);
                    // seen edge-on, and right by the camera, the wall thins out like a cloud; from outside its back
                    // is unseen (0 alpha lets strip() skip it)
                    float a = (back ? 1 - outside : 1) * .92f * (d < 1e-3f ? 1 : .3f + .7f * smooth(Math.abs(facing) / d / .45f))
                            * (.3f + .7f * smooth(d / (r * .3f))) * vis(y);
                    float shade = tone[k] * (.82f + .18f * Mth.clamp(y / h, 0, 1)) * light; // a bit lighter higher up
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
            float q = j / (float) CAP_ROWS * (SOLID + .15f) - .15f, rad = capRadius(q), y = capHeight(q), shade = capShade(q) * light, aw = .95f * capFade;
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
            int color = argb(runs[p + 11] * capFade, Mth.lerp(hz, WALL_R * shade, fogR) + l * litCr + w, Mth.lerp(hz, WALL_G * shade, fogG) + l * litCg + w,
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
            if (!onScreen(cx, cz, profile(Math.max(ry + tl, 0)) * 1.04f + 1, ry - tl - halfW, ry + tl + halfW)) continue; // after the random draws: same rings either way
            if (!born || dying > 0) { // being born: wide, faint halos riding the vortex in; dying: letting go
                float v = vis(ry);
                if (v <= 0) continue;
                bright *= v * (1 + 2 * (1 - v)); // a last flare as each comes free
            }
            for (int i = 0; i <= RING_SEG; i++) {
                float ang = i * TAU / RING_SEG, c = Mth.cos(ang), s = Mth.sin(ang);
                float y = foot ? r * .05f : seam(k, ang), rad = profile(Math.max(y, 0)) * 1.035f;
                float x = cx + rad * c, z = cz + rad * s, u = u0 + i * reps / (float) RING_SEG;
                float a = (core ? bright : bright * .65f) * pulse * front(x, z, rad / 1.035f), hz = haze(tx + x, ty + y, tz + z);
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
        if (arcsIn * outside <= 0) return; // being born: not yet; from inside the column the wall hides them
        RandomSource rnd = RandomSource.create(~seed);
        for (int k = 0; k < ARC_RINGS; k++) {
            StormType bt = storm.bandType(k);
            int color = core ? bt.core : bt.glow;
            float q = .05f + (k + rnd.nextFloat() * .5f) * .85f / ARC_RINGS, rad = capRadius(q);
            float y = capHeight(q) - 4; // just under the cloud surface; seen through it anyway (pull)
            // From below they're already this side of the cloud: a few blocks clear its wobble, and hills anywhere
            // in between still hide them. From above, halfway to the camera, through the sheet.
            pull = ty + y > 0 ? 8 : -.5f;
            float turn = frac(t * (.012f - .008f * q)) * TAU, halfW = (1.6f + rnd.nextFloat() * 1.4f) * (1 + q) * (core ? 1 : 2.8f);
            float fade = 1 - .6f * q, u0 = -frac(t * (.05f + rnd.nextFloat() * .05f) * (core ? 1 : .3f));
            int count = 2 + rnd.nextInt(3);
            for (int m = 0; m < count; m++) {
                float a0 = rnd.nextFloat() * TAU + turn, len = (.08f + rnd.nextFloat() * .3f) * TAU;
                float bright = (.6f + rnd.nextFloat() * .4f) * fade * (core ? 1 : .6f) * pulse * arcsIn * outside;
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
        if (outside <= 0) return 1;
        float cx = -tx, cz = -tz, dx = x - cx, dz = z - cz;
        float s = Mth.clamp(-(cx * dx + cz * dz) / (dx * dx + dz * dz), 0, 1); // where the line passes nearest the axis
        float px = cx + dx * s, pz = cz + dz * s;
        return 1 - outside * (1 - smooth((Mth.sqrt(px * px + pz * pz) - wallR * .95f) / (wallR * .06f)));
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
            float d = Mth.sqrt(x[i] * x[i] + y[i] * y[i] + z[i] * z[i]);
            a *= .12f + .88f * smooth(d / (r * .25f)); // right by the camera a band is mist, not a wall of light
            if (pull != 0 && a > 0) { // pull < 0: that share of the distance instead
                float f = pull < 0 ? 1 + pull : d > pull * 2 ? 1 - pull / d : .5f;
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

        /** For packs like Photon (forwardPack()): the plain textured shader, which they draw see-through and blend by alpha. */
        private static final ShaderStateShard POS_TEX_COLOR = new ShaderStateShard(net.minecraft.client.renderer.GameRenderer::getPositionTexColorShader);
        static final Function<ResourceLocation, RenderType> WALL_SH = Util.memoize(tex -> create("horizons_unbound_wall_sh",
                DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
                state(tex, POS_TEX_COLOR, TRANSLUCENT_TRANSPARENCY, COLOR_DEPTH_WRITE)));
        static final Function<ResourceLocation, RenderType> GLOW_SH = Util.memoize(tex -> create("horizons_unbound_glow_sh",
                DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
                state(tex, POS_TEX_COLOR, ADDITIVE_TRANSPARENCY, COLOR_WRITE)));

        static RenderType wall(ResourceLocation tex) { return (forwardPack() ? WALL_SH : WALL).apply(tex); }
        static RenderType glow(ResourceLocation tex) { return forwardPack() ? GLOW_SH.apply(alphaGlow(tex)) : GLOW.apply(tex); }

        /** The glow texture whose alpha follows its brightness (tools/make_alpha_glow.py): packs that blend by alpha keep black see-through. */
        private static final Function<ResourceLocation, ResourceLocation> ALPHA = Util.memoize(t -> t.withPath(t.getPath().replace(".png", "_a.png")));

        private static ResourceLocation alphaGlow(ResourceLocation tex) { return ALPHA.apply(tex); }

        private static CompositeState state(ResourceLocation tex, ShaderStateShard shader, TransparencyStateShard blend, WriteMaskStateShard mask) {
            return CompositeState.builder().setShaderState(shader).setTextureState(new TextureStateShard(tex, false, false))
                    .setTransparencyState(blend).setCullState(NO_CULL).setWriteMaskState(mask).setTexturingState(NO_FOG).setOverlayState(OVERLAY)
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
