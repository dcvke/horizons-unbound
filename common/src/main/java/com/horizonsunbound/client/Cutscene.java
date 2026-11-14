package com.horizonsunbound.client;

import com.horizonsunbound.HorizonsUnbound;
import com.horizonsunbound.Storm;
import com.horizonsunbound.StormCutscenes;
import com.horizonsunbound.StormType;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

import static com.horizonsunbound.client.StormBirth.IGNITION;
import static com.horizonsunbound.client.StormBirth.TOUCHDOWN;

/**
 * The cutscene of a storm's birth (server: StormCutscenes), shot like a film. Everything is a function of one
 * clock keyed on the storm's own age, so it stays in step with the storm and pauses with the game.
 * It is staged like a scene from a story game: cuts between the player, acting, and what they see.
 * <pre>
 *  -10  .. -3.8  the camera slips out of the player's eyes and round to their face: they look about, uneasy, raise
 *                their hands and stare at them (sparks crawl over them), then slowly look up at the sky
 *  -3.7 ..  2.3  cut: over their shoulder, the dark sky; the sparks gather
 *   2.3 ..  3.6  cut: the beam fires - their face lit, they flinch, crouch back, an arm thrown up
 *   3.7 ..  6.5  cut: low behind them, small against the beam and the cloud spreading; a crane up
 *   6.5 .. 12.5  the flight in as the vortex closes, a low hero shot of it shutting into the column
 *  12.6 .. 14.4  cut: the player, braced against the shaking, straightening up
 *  14.5 .. 19.5  cut: the reveal, far out round the storm, its name as a title card
 *  19.6 .. 23    cut: over the shoulder, a slow push in, a dip to black back into the eyes
 * </pre>
 * The camera flies a smooth spline (velocity continuous) through those key shots. They are planned once at the
 * start against the loaded world: each is moved up out of terrain, foliage or water, the watching spot is picked
 * for a clear view of the storm, the orbit goes the way that is more in the open, and any stretch of the flight that
 * would pass through something becomes a dip through black. Per frame it costs a few spline evaluations.
 * On top: a faint handheld sway, banking into turns, smooth shakes at ignition and touchdown, the lens breathing.
 */

public final class Cutscene {
    private static final byte SMOOTH = 0, CUT = 1, DIP = 2;

    /**
     * A cutscene's shape: its clock's start and end, when each key shot is reached and its lens (FOV factor), how the
     * stretches between shots go (smooth unless listed: index, CUT or DIP), the thoughts (when, lang key under
     * cutscene.horizons_unbound, % = the storm's kind) and when the title card shows.
     */
    private record Script(float start, float end, float[] kt, float[] fov, int[] kinds, float[] lineAt, String[] lines, float title) {}

    /** The birth, on the storm's clock (0 = it rises). */
    private static final Script BIRTH = new Script(-StormCutscenes.PREROLL / 20f, 23,
            new float[] {-StormCutscenes.PREROLL / 20f, -8.9f, -7.6f, -6.2f, -3.8f, -3.7f, 2.25f, 2.35f, 3.6f, 3.7f, 6.5f, 8.8f, 10.6f, 12.5f,
                    12.6f, 14.4f, 14.5f, 19.5f, 19.6f, 21.6f, 23},
            new float[] {1, .95f, .9f, .8f, .9f, .95f, .9f, .8f, .85f, 1.05f, 1, 1, .95f, 1, .85f, .85f, 1, 1, .95f, .92f, 1},
            new int[] {4, CUT, 6, CUT, 8, CUT, 13, CUT, 15, CUT, 17, CUT, 19, DIP},
            // one short phrase at a time, each said on its own beat of the acting (ACT): the glance, the hands, the look up...
            new float[] {-9.5f, -8.5f, -7.1f, -6.1f, -5.2f, -4f, -3.2f, -2.2f, -.6f, .9f, 2.5f, 3.4f, 5.4f, 7.6f, 9.2f, 11.1f, 13f, 13.9f, 20f, 21.2f},
            new String[] {"quiet", "quiet2", "hands", "hands2", "hands3", "sky", "sky2", "sky3", "horizon", "sparks", "split", "split2",
                    "type.%", "soon", "soon2", "shake", "alive", "alive2", "inside", "inside2"}, 15.4f);

    /**
     * Finding a storm already out there (the first of its kind). Seconds from the start.
     * <pre>
     *   0   .. 1.8   through the eyes: the view turns to it                               "...Wait. What is that?"
     *   1.9 .. 4.4   cut: over the shoulder, the figure tiny before the column, easing back
     *   4.4 .. 9.5   a long, low flight towards it, lifting over whatever is in the way
     *   9.5 .. 13    round it, low under the wall, the rings sweeping overhead
 *  13   .. 16    up above the cloud cap, looking down into the vortex; the title card: a new storm
 *  16   .. 18.6  far out, the whole of it
     *  18.6          cut: over the shoulder again, a slow push in, a dip to black back into the eyes
     * </pre>
     */
    private static final Script DISCOVERY = new Script(0, 22,
            new float[] {0, 1.8f, 1.9f, 4.4f, 7f, 9.5f, 13f, 16f, 18.6f, 18.7f, 20.6f, 22},
            new float[] {1, .88f, .95f, .95f, 1.05f, 1, 1.05f, 1, 1, .95f, .92f, 1},
            new int[] {1, CUT, 8, CUT, 10, DIP},
            new float[] {.3f, 2.6f, 6.4f, 10.2f, 18.6f},
            new String[] {"found", "first.%", "hint.%", "hint2.%", "remember"}, 13.6f);
    /**
     * A storm coming apart at the end of its life (StormRenderer: from the ground up, into the thinning cloud).
     * <pre>
     *   0   .. 1.6   through the eyes: the view turns to it                               "...It's fading."
     *   1.7 .. 4.5   cut: over the shoulder, easing back as the rings let go
     *   4.6 .. 9     cut: far out, slowly round it, the column lifting off and coming apart
     *   9.1 .. 12.5  cut: over the shoulder, a push in, a dip to black back into the eyes
     * </pre>
     */
    private static final Script DEATH = new Script(0, 12.5f,
            new float[] {0, 1.6f, 1.7f, 4.5f, 4.6f, 9f, 9.1f, 11.2f, 12.5f},
            new float[] {1, .9f, .95f, .95f, 1, 1.05f, .95f, .92f, 1},
            new int[] {1, CUT, 3, CUT, 5, CUT, 7, DIP},
            new float[] {.4f, 2.2f, 5.2f, 7.4f, 9.6f},
            new String[] {"fade", "fade2", "fade3", "fade4", "fade5"}, 99);
    private static final float TITLE_LEN = 3.4f;
    private static final int SKIP_HOLD = 20, FADE = 8; // ticks

    private static Cutscene active;

    private final Script sc;
    private final boolean birth;
    private final UUID stormId;
    private final StormType type;   // the kind it is about

    /** The kind as the words call it ("grand": the great storm). Asked each time: that flag may reach us a moment after the cutscene starts. */
    private String kindId() {
        Storm s = ClientStorms.get(stormId);
        return s != null && s.grand ? "grand" : type.id;
    }
    private final ResourceKey<Level> dimension;
    private final Vec3 eye0, look0, base, seed;
    private final float h;
    private final float size;               // the player's height in ordinary players (Pehkui, Origins): the close shots keep their framing
    private final Vec3[] kp, kl, mp, ml;  // key shots: where, looking at what, and the curve's tangents there
    private final byte[] kind;              // how each stretch between two shots goes
    private int ticks, lost, skipHold, fade = -1;
    private final boolean[] strong;         // lines said with feeling (a *starred* or SHOUTED word in them)

    public record View(Vec3 pos, float yaw, float pitch, float roll, boolean detached) {}

    private Cutscene(Minecraft mc, Storm s, StormType about, Script script) {
        LocalPlayer p = mc.player;
        sc = script;
        birth = script == BIRTH;
        int n = sc.kt.length;
        kp = new Vec3[n];
        kl = new Vec3[n];
        mp = new Vec3[n];
        ml = new Vec3[n];
        kind = new byte[n - 1];
        stormId = s.id;
        type = about;
        dimension = mc.level.dimension();
        eye0 = p.getEyePosition();
        look0 = p.getViewVector(1);
        base = new Vec3(s.x, s.y, s.z);
        seed = base.add(0, 2, 0);
        h = 3 * s.radius(); // the column's height (StormRenderer)
        size = Mth.clamp(p.getBbHeight() / 1.8f, .25f, 8);
        strong = new boolean[sc.lines.length];
        for (int i = 0; i < strong.length; i++) strong[i] = line(i).matches(".*(\\*|\\p{Lu}{3}).*");
        long t0 = System.nanoTime();
        plan(mc.level, p, s.radius());
        LogUtils.getLogger().info("Storm cutscene planned in {} ms: shots {}, stretches {}", (System.nanoTime() - t0) / 1_000_000,
                java.util.Arrays.toString(kp), java.util.Arrays.toString(kind));
    }

    /** The server says: watch this storm be born, or find it (StormCutscenes.Payload; network handler, main thread). */
    public static void start(UUID id, int mode) {
        Minecraft mc = Minecraft.getInstance();
        Storm s = ClientStorms.get(id);
        if (active != null || !StormConfig.CUTSCENES.get() || mc.player == null || mc.level == null || s == null) {
            com.horizonsunbound.platform.Services.NETWORK.toServer(new StormCutscenes.Payload(id, 0)); // not watching
            return;
        }
        KeyMapping.releaseAll(); // no key stays held through it (a punch, walking)
        mc.player.swinging = false;
        if (mc.gameMode != null) mc.gameMode.stopDestroyBlock();
        StormType[] kinds = StormType.values();
        boolean known = mode == StormCutscenes.BIRTH || mode == StormCutscenes.DEATH;
        active = new Cutscene(mc, s, known ? s.type : kinds[Mth.clamp(mode - 1, 0, kinds.length - 1)],
                mode == StormCutscenes.BIRTH ? BIRTH : mode == StormCutscenes.DEATH ? DEATH : DISCOVERY);
    }

    public static boolean active() { return active != null; }

    private static boolean detachedView; // the last frame's camera was out of the player's eyes

    /**
     * True while the cutscene's camera is away from the player's eyes: the game then reports a third-person camera
     * (OptionsMixin), so avatar mods (Figura scripts asking "first person?") draw the whole player, not just what
     * a first-person view would show. The option itself is never changed.
     */
    public static boolean thirdPerson() { return active != null && detachedView; }

    /** The clock of the last frame drawn (autotest traces). */
    public static float lastT;

    /** The camera this frame, or null when no cutscene runs (CameraMixin). */
    public static View view(float partial) {
        Cutscene c = active;
        LocalPlayer p = Minecraft.getInstance().player;
        if (c == null || p == null) return null;
        try {
            View v = c.frame(p, partial);
            detachedView = v != null && v.detached();
            return v;
        } catch (RuntimeException e) {
            fail(e);
            return null;
        }
    }

    /** Something in the cutscene threw (another mod in the way, say): it ends right there, the game goes on. */
    private static void fail(RuntimeException e) {
        LogUtils.getLogger().error("Horizons Unbound: the storm cutscene stopped after an error", e);
        if (active != null) active.stop();
    }

    private void stop() {
        active = null;
        KeyMapping.releaseAll();
        com.horizonsunbound.platform.Services.NETWORK.toServer(new StormCutscenes.Payload(stormId, 0));
    }

    /**
     * Seconds on the storm's clock (0 = it rises). Its own steady count rather than the storm's age: that one gets
     * corrected by the server now and then, and a tick's jump shows as a hitch in a moving camera.
     */
    private float time(float partial) { return sc.start + (ticks + partial) / 20f; }

    // ---------------------------------------------------------------- the plan

    private void plan(ClientLevel level, LocalPlayer p, float r) {
        boolean roof = level.dimensionType().hasCeiling();
        Vec3 fwd = new Vec3(base.x - eye0.x, 0, base.z - eye0.z).normalize(), right = new Vec3(-fwd.z, 0, fwd.x);
        Vec3 shoulder = place(level, roof, eye0.subtract(fwd.scale(4.6 * size)).add(right.scale(1.9 * size)).add(0, .55 * size, 0), false);
        if (shoulder == null) shoulder = eye0;
        Vec3 back = place(level, roof, eye0.subtract(fwd.scale(5.6 * size)).add(right.scale(2.4 * size)).add(0, 1.3 * size, 0), false);
        if (back == null) back = shoulder;
        if (birth) planBirth(level, p, r, roof, fwd, right, shoulder, back);
        else if (sc == DEATH) planDeath(level, p, r, roof, shoulder, back);
        else planDiscovery(level, p, r, roof, shoulder, back);
        for (int i = 0; i < sc.kinds.length; i += 2) kind[sc.kinds[i]] = (byte) sc.kinds[i + 1];
        // stretches that would pass through something become dips through black
        for (int pass = 0; pass < 3; pass++) {
            tangents(kp, mp, true);
            boolean changed = false;
            for (int i = 0; i < kind.length; i++) {
                if (kind[i] != SMOOTH || clear(level, i)) continue;
                kind[i] = DIP;
                changed = true;
            }
            if (!changed) break;
        }
        for (int i = 0; i < kind.length; i++) if (kp[i].distanceToSqr(kp[i + 1]) < .01) kind[i] = SMOOTH; // a cut to the same spot: no blink
        tangents(kp, mp, true);
        tangents(kl, ml, true);
    }

    private void planBirth(ClientLevel level, LocalPlayer p, float r, boolean roof, Vec3 fwd, Vec3 right, Vec3 shoulder, Vec3 back) {
        Vec3 column = base.add(0, h * .42f, 0), above = seed.add(0, h * .12f, 0), head = eye0.add(0, -.1 * size, 0);
        double a1 = Math.atan2(eye0.z - base.z, eye0.x - base.x);
        // the far shots go round the storm whichever way is more in the open
        Vec3[] far = new Vec3[5];
        int bestMiss = Integer.MAX_VALUE;
        for (int sgn : new int[] {1, -1}) {
            Vec3[] o = {
                    place(level, roof, around(level, roof, a1 + sgn * Math.toRadians(10), r + 70, 14, false), true),  // the flight in
                    place(level, roof, around(level, roof, a1 + sgn * Math.toRadians(20), r + 48, 3, true), true),    // low: the touchdown
                    place(level, roof, around(level, roof, a1 + sgn * Math.toRadians(30), r + 52, 4, true), true),
                    place(level, roof, around(level, roof, a1 + sgn * Math.toRadians(40), r + 120, h * .2f, false), true), // the reveal, from
                    null};                                                                                             // ...to
            for (double deg : new double[] {65, 50, 80, 35}) { // the reveal needs a view of the whole column
                Vec3 c = place(level, roof, around(level, roof, a1 + sgn * Math.toRadians(deg), r + 175, h * .3f, false), true);
                if (c != null && sees(level, p, c, column)) {
                    o[4] = c;
                    break;
                }
            }
            int miss = 0;
            for (Vec3 c : o) if (c == null) miss++;
            if (miss < bestMiss) {
                bestMiss = miss;
                far = o;
            }
        }
        Vec3 crane = place(level, roof, eye0.subtract(fwd.scale(8 * size)).subtract(right.scale(3.5 * size)).add(0, 6 * size, 0), true);
        kp[0] = kp[20] = eye0;
        kp[1] = near(level, roof, fwd, right, -3, 1.4, .5);    // out of the eyes, behind
        kp[2] = near(level, roof, fwd, right, .6, 3.2, .1);    // round the side
        kp[3] = near(level, roof, fwd, right, 2.4, 1.3, -.25); // their hands and face
        kp[4] = near(level, roof, fwd, right, 2.6, -1.2, -.7); // low, as they look up
        kp[5] = kp[18] = back;
        kp[6] = kp[19] = shoulder;
        kp[7] = near(level, roof, fwd, right, 2.2, .5, -.3);   // the flinch
        kp[8] = near(level, roof, fwd, right, 2.9, .7, -.4);
        kp[9] = near(level, roof, fwd, right, -7, -3, .3);     // small against the beam
        kp[10] = crane != null ? crane : kp[9];
        kp[11] = far[0];
        kp[12] = far[1];
        kp[13] = far[2];
        kp[14] = near(level, roof, fwd, right, 3, -1.6, .2);   // braced
        kp[15] = near(level, roof, fwd, right, 2.6, -2.4, .4);
        kp[16] = far[3];
        kp[17] = far[4];
        for (int i = 11; i <= 17; i++) if (kp[i] == null) kp[i] = kp[i - 1]; // no spot for this shot: hold the last one
        Vec3[] looks = {eye0.add(look0.scale(64)), eye0.add(fwd.scale(5)), head, head.add(0, -.35 * size, 0), head.add(0, .2 * size, 0),
                base.add(0, h * .28f, 0), base.add(0, h * .2f, 0), head, head, base.add(0, h * .3f, 0), base.add(0, h * .5f, 0),
                tip(8.8f), base.add(0, h * .1f, 0), base.add(0, h * .3f, 0), head, head, base.add(0, h * .4f, 0), base.add(0, h * .45f, 0),
                base.add(0, h * .16f, 0), above, eye0.add(above.subtract(eye0).normalize().scale(64))}; // the end: where the player looks
        System.arraycopy(looks, 0, kl, 0, looks.length);
        for (int i = 1; i < kp.length - 1; i++) if (kp[i] == eye0) kl[i] = column; // no room for a shot of the player: their view instead
        // the acting: where the player looks, moment by moment (yaw, pitch)
        yawS = (float) (Mth.atan2(fwd.z, fwd.x) * Mth.RAD_TO_DEG) - 90;
        float y0 = yawS + Mth.wrapDegrees(p.getYRot() - yawS), sky = pitchTo(base.add(0, h * .5f, 0));
        actAt = new float[] {sc.start, -9.2f, -8f, -7.3f, -5f, -3.6f, IGNITION, IGNITION + .25f, 4.5f, 7.5f, TOUCHDOWN, 13, 20, sc.end};
        actYaw = new float[] {y0, yawS + 50, yawS - 45, yawS, yawS, yawS, yawS, yawS, yawS, yawS, yawS, yawS, yawS, yawS};
        actPitch = new float[] {p.getXRot(), 2, -4, 50, 50, sky, sky, pitchTo(base.add(0, h, 0)), pitchTo(base.add(0, h * .75f, 0)), pitchTo(tip(7.5f)),
                pitchTo(seed), pitchTo(base.add(0, h * .3f, 0)), pitchTo(above), pitchTo(above)};
    }

    /** A spot by the player (blocks towards the storm, to their right, up from their eyes), mirrored or raised if it is blocked; their eyes if all are. */
    private Vec3 near(ClientLevel level, boolean roof, Vec3 fwd, Vec3 right, double f, double rt, double up) {
        for (int i = 0; i < 4; i++) {
            Vec3 c = eye0.add(fwd.scale(f * size)).add(right.scale(((i & 1) == 0 ? rt : -rt) * size)).add(0, (up + (i >> 1)) * size, 0);
            if (open(level, c)) return c;
        }
        return eye0;
    }

    private float pitchTo(Vec3 v) {
        Vec3 d = v.subtract(eye0);
        return Mth.clamp((float) -(Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG), -80, 80);
    }

    // ---------------------------------------------------------------- the actor (the birth only)

    private float yawS;                         // the yaw from the player to the storm
    private float[] actAt, actYaw, actPitch;    // where they look, keyed in time

    private static float hands(float t) { return smooth((t + 7.7f) / .6f) * (1 - smooth((t + 5.1f) / .7f)); }       // staring at their hands
    /** One tick of acting: the head (and so the view they come back to), the body turning to face the storm, sparks on the hands. */
    private void act(LocalPlayer p, float t) {
        int i = 0;
        while (i < actAt.length - 2 && t >= actAt[i + 1]) i++;
        float k = smooth((t - actAt[i]) / (actAt[i + 1] - actAt[i])), hands = hands(t);
        p.setYRot(Mth.lerp(k, actYaw[i], actYaw[i + 1]) + hands * 16 * Mth.sin(t * 2.4f)); // from one hand to the other
        p.setXRot(Mth.lerp(k, actPitch[i], actPitch[i + 1]));
        p.yHeadRot = p.getYRot();
        if (t > -8.4f) p.yBodyRot = Mth.approachDegrees(p.yBodyRot, yawS, 9);
        if (hands > .3f && ticks % 24 == 0) // their own heartbeat, loud in the quiet, while they stare at their hands
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.WARDEN_HEARTBEAT, .9f, .55f));
        if (hands > .5f && ticks % 2 == 0) {
            float yb = p.yBodyRot * Mth.DEG_TO_RAD, side = ticks % 4 == 0 ? .3f : -.3f;
            var rnd = p.getRandom();
            p.level().addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                    p.getX() - Mth.sin(yb) * .6 - Mth.cos(yb) * side + (rnd.nextFloat() - .5) * .2, p.getY() + 1 + rnd.nextFloat() * .25,
                    p.getZ() + Mth.cos(yb) * .6 - Mth.sin(yb) * side + (rnd.nextFloat() - .5) * .2, 0, .02, 0);
        }
    }

    /**
     * The player's performance through the birth: key poses in time, the body flowing through them on a smooth curve
     * (so a move starts with a little wind-up and overshoots before it settles, like a real limb). Per key: time, then
     * right arm x/y/z, left arm x/y/z (radians; x negative = raised forward, z = out to the side), the torso's twist
     * and lean (positive = forward), the head's tilt, right and left leg swing, and how far the legs are apart.
     */
    private static final float[][] ACT = {
            {-10f,    0, 0, .1f,          0, 0, -.1f,          0, 0, 0,          0, 0, 0},
            {-9.4f,   -.15f, 0, .2f,      -.1f, 0, -.18f,      .28f, 0, .06f,    .08f, -.06f, 0},      // uneasy: a glance one way
            {-8.6f,   .12f, 0, .14f,      -.28f, 0, -.22f,     -.32f, .03f, -.05f, -.1f, .1f, .02f},   // ...and the other, weight shifting
            {-7.9f,   .28f, 0, .12f,      .28f, 0, -.12f,      0, .05f, 0,       0, 0, 0},             // the wind-up: arms dip back
            {-7.25f,  -1.4f, -.38f, 0,    -1.38f, .38f, 0,     0, .14f, 0,       0, 0, .03f},          // hands up, hunched over them
            {-6.8f,   -1.05f, -.3f, .05f, -1.1f, .3f, -.05f,   0, .1f, .03f,     0, 0, .03f},
            {-6.2f,   -1.28f, -.48f, .4f, -.95f, .25f, -.05f,  .08f, .1f, .13f,  0, 0, .03f},          // turning the right hand over
            {-5.5f,   -.95f, -.25f, .05f, -1.32f, .48f, -.4f,  -.08f, .1f, -.13f, 0, 0, .03f},         // ...then the left
            {-5f,     -.8f, -.2f, .05f,   -.82f, .2f, -.05f,   0, .05f, 0,       0, 0, .02f},
            {-4.2f,   -.2f, 0, .14f,      -.26f, 0, -.14f,     0, -.08f, 0,      0, 0, 0},             // arms sink as the eyes go up
            {-3.2f,   .06f, 0, .22f,      0, 0, -.24f,         0, -.11f, .04f,   .05f, -.03f, .02f},   // slack, staring
            {-1f,     -.5f, -.1f, .16f,   .05f, 0, -.2f,       -.06f, -.1f, 0,   -.12f, .06f, .02f},   // a hand half raised at the sky
            {1.5f,    -.95f, -.16f, .1f,  .05f, 0, -.22f,      -.12f, -.08f, 0,  -.12f, .06f, .02f},   // pointing at the sparks
            {2.25f,   -.9f, -.15f, .1f,   .08f, 0, -.22f,      -.12f, -.06f, 0,  -.12f, .06f, .02f},
            {2.42f,   -2.55f, -.85f, 0,   .75f, 0, -.65f,      .38f, -.2f, -.1f, .2f, -.25f, .06f},    // the flinch: an arm thrown up
            {2.8f,    -2.2f, -.7f, 0,     .5f, 0, -.45f,       .26f, -.15f, -.06f, .12f, -.15f, .05f},
            {3.6f,    -2.1f, -.65f, .05f, .45f, 0, -.42f,      .22f, -.14f, -.05f, .1f, -.12f, .05f},
            {4.6f,    -1.5f, -.5f, .1f,   .2f, 0, -.3f,        .1f, -.12f, 0,    .04f, -.05f, .03f},   // peering past the arm
            {6.5f,    -.15f, 0, .26f,     -.15f, 0, -.26f,     0, -.14f, .03f,   0, 0, .02f},          // awe: arms loose, leaning back
            {10.6f,   -.2f, 0, .3f,       -.12f, 0, -.28f,     0, -.15f, -.03f,  0, 0, .02f},
            {11.05f,  -.6f, 0, 1.15f,     -.5f, 0, -1.15f,     0, .12f, 0,       .1f, -.1f, .2f},      // touchdown: thrown off balance
            {11.6f,   -.2f, 0, .7f,       -.55f, 0, -1f,       -.18f, .05f, .08f, -.15f, .12f, .18f},  // wobbling
            {12.3f,   -.48f, 0, .95f,     -.2f, 0, -.6f,       .14f, .08f, -.06f, .1f, -.08f, .16f},
            {13.2f,   -.25f, 0, .42f,     -.25f, 0, -.42f,     0, .04f, 0,       0, 0, .1f},           // steadying
            {14.4f,   0, 0, .14f,         0, 0, -.14f,         0, -.08f, 0,      0, 0, .02f},          // standing tall
            {19.6f,   -.1f, 0, .16f,      0, 0, -.14f,         0, -.06f, 0,      0, 0, .02f},
            {20.4f,   .1f, 0, .2f,        .15f, 0, -.2f,       .3f, -.04f, -.05f, .12f, -.1f, .02f},   // half turning away: time to go
            {21.6f,   -1.15f, -.1f, .06f, 0, 0, -.16f,         -.06f, .05f, .04f, -.1f, .05f, .02f},   // ...but a hand reaches out towards it
            {22.4f,   -.4f, 0, .12f,      0, 0, -.12f,         0, 0, 0,          0, 0, 0},
            {23f,     0, 0, .1f,          0, 0, -.1f,          0, 0, 0,          0, 0, 0}};

    /** Channel ch of the performance at time t: a smooth curve through the key poses. */
    private static float act(float t, int ch) {
        int n = ACT.length, i = 0;
        while (i < n - 2 && t >= ACT[i + 1][0]) i++;
        float t0 = ACT[i][0], t1 = ACT[i + 1][0], dt = t1 - t0, u = Mth.clamp((t - t0) / dt, 0, 1), p0 = ACT[i][ch], p1 = ACT[i + 1][ch];
        float m0 = i == 0 ? 0 : (p1 - ACT[i - 1][ch]) / (t1 - ACT[i - 1][0]), m1 = i + 2 >= n ? 0 : (ACT[i + 2][ch] - p0) / (ACT[i + 2][0] - t0);
        float u2 = u * u, u3 = u2 * u;
        return (2 * u3 - 3 * u2 + 1) * p0 + (u3 - 2 * u2 + u) * dt * m0 + (3 * u2 - 2 * u3) * p1 + (u3 - u2) * dt * m1;
    }

    /** The player's whole body during the birth, over whatever the model was doing (PlayerModelMixin, every frame it is drawn). */
    public static void pose(net.minecraft.client.model.PlayerModel<?> m, net.minecraft.world.entity.LivingEntity e) {
        Cutscene c = active;
        if (c == null || !c.birth || e != Minecraft.getInstance().player) return;
        float t = lastT, w = smooth((t - c.sc.start) / .6f) * (1 - smooth((t - c.sc.end + 1) / .9f)); // in from and back out to the model's own pose
        float breath = Mth.sin(t * 1.9f), tremble = hands(t) * .045f * Mth.sin(t * 33), sway = .02f * noise(t * 1.3f, 9);
        arm(m.rightArm, w, act(t, 1) + tremble + .015f * breath, act(t, 2), act(t, 3) + .02f * breath);
        arm(m.leftArm, w, act(t, 4) - tremble + .015f * breath, act(t, 5), act(t, 6) - .02f * breath);
        float twist = w * (act(t, 7) + sway), lean = w * (act(t, 8) + .012f * breath);
        m.body.yRot += twist; // the shoulders go round with the torso (as in the model's own swing)
        m.rightArm.z = Mth.sin(m.body.yRot) * 5;
        m.rightArm.x = -Mth.cos(m.body.yRot) * 5;
        m.leftArm.z = -Mth.sin(m.body.yRot) * 5;
        m.leftArm.x = Mth.cos(m.body.yRot) * 5;
        m.rightArm.yRot += twist;
        m.leftArm.yRot += twist;
        m.body.xRot += lean; // it turns about the neck, so the hips, and the legs with them, shift
        m.rightLeg.z += 12 * Mth.sin(lean);
        m.leftLeg.z += 12 * Mth.sin(lean);
        m.head.zRot = w * act(t, 9);
        float spread = w * act(t, 12);
        m.rightLeg.xRot += w * act(t, 10);
        m.leftLeg.xRot += w * act(t, 11);
        m.rightLeg.zRot += spread;
        m.leftLeg.zRot -= spread;
        m.hat.copyFrom(m.head);
        m.jacket.copyFrom(m.body);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftPants.copyFrom(m.leftLeg);
    }

    private static void arm(net.minecraft.client.model.geom.ModelPart part, float w, float x, float y, float z) {
        part.xRot = Mth.lerp(w, part.xRot, x);
        part.yRot = Mth.lerp(w, part.yRot, y);
        part.zRot = Mth.lerp(w, part.zRot, z);
    }

    private void planDeath(ClientLevel level, LocalPlayer p, float r, boolean roof, Vec3 shoulder, Vec3 back) {
        Vec3 col = base.add(0, h * .35f, 0);
        double a0 = Math.atan2(eye0.z - base.z, eye0.x - base.x);
        int bestMiss = Integer.MAX_VALUE;
        for (int sgn : new int[] {1, -1}) { // far out and slowly round it, whichever way is more in the open
            Vec3[] o = {place(level, roof, around(level, roof, a0 + sgn * Math.toRadians(35), r + 150, h * .25f, false), true),
                    place(level, roof, around(level, roof, a0 + sgn * Math.toRadians(55), r + 170, h * .35f, false), true)};
            int miss = (o[0] == null ? 1 : 0) + (o[1] == null ? 1 : 0);
            if (miss < bestMiss) {
                bestMiss = miss;
                kp[4] = o[0];
                kp[5] = o[1];
            }
        }
        kp[0] = kp[1] = kp[8] = eye0;
        kp[2] = kp[7] = shoulder;
        kp[3] = kp[6] = back;
        if (kp[4] == null) kp[4] = back;
        if (kp[5] == null) kp[5] = kp[4];
        Vec3 low = base.add(0, h * .2f, 0);
        Vec3[] looks = {eye0.add(look0.scale(64)), col, low, base.add(0, h * .3f, 0), base.add(0, h * .4f, 0), base.add(0, h * .65f, 0),
                low, col, eye0.add(col.subtract(eye0).normalize().scale(64))};
        System.arraycopy(looks, 0, kl, 0, looks.length);
    }

    private void planDiscovery(ClientLevel level, LocalPlayer p, float r, boolean roof, Vec3 shoulder, Vec3 back) {
        Vec3 col = base.add(0, h * .35f, 0);
        double a0 = Math.atan2(eye0.z - base.z, eye0.x - base.x);
        Vec3 near = place(level, roof, around(level, roof, a0, r + 55, 8, true), true);
        if (near == null) near = back;
        // halfway there, high enough to clear the ground all along the way (where there is a sky to rise into)
        Vec3 mid = back.lerp(near, .5);
        double top = Math.max(back.y, near.y);
        for (int i = 1; i < 16 && !roof; i++) {
            Vec3 q = back.lerp(near, i / 16.0);
            top = Math.max(top, level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(q.x), Mth.floor(q.z)) + 6);
        }
        Vec3 m = place(level, roof, new Vec3(mid.x, Math.max(mid.y, top), mid.z), true);
        mid = m != null ? m : back;
        // then round it and up, whichever way round is more in the open, and far out for all of it
        int bestMiss = Integer.MAX_VALUE;
        for (int sgn : new int[] {1, -1}) {
            Vec3[] o = {
                    place(level, roof, around(level, roof, a0 + sgn * Math.toRadians(40), r + 30, h * .12f, false), true), // under the wall, the rings sweeping overhead
                    roof ? null : place(level, false, around(level, false, a0 + sgn * Math.toRadians(75), r * 1.3, h * 1.25f, false), true), // above the cloud cap
                    null};
            for (double deg : new double[] {95, 80, 110, 60}) {
                Vec3 c = place(level, roof, around(level, roof, a0 + sgn * Math.toRadians(deg), r + 190, h * .5f, false), true);
                if (c != null && sees(level, p, c, col)) {
                    o[2] = c;
                    break;
                }
            }
            int miss = 0;
            for (Vec3 c : o) if (c == null) miss++;
            if (miss < bestMiss) {
                bestMiss = miss;
                System.arraycopy(o, 0, kp, 6, 3);
            }
        }
        kp[0] = kp[1] = kp[11] = eye0;
        kp[2] = kp[10] = shoulder;
        kp[3] = kp[9] = back;
        kp[4] = mid;
        kp[5] = near;
        for (int i = 6; i <= 8; i++) if (kp[i] == null) kp[i] = kp[i - 1];
        Vec3 low = base.add(0, h * .1f, 0); // over the shoulder: the player in the lower third, the storm towering over them
        Vec3[] looks = {eye0.add(look0.scale(64)), col, low, base.add(0, h * .3f, 0), base.add(0, h * .3f, 0), col,
                base.add(0, h * .6f, 0), base.add(0, h * .95f, 0), base.add(0, h * .45f, 0), low, low,
                eye0.add(col.subtract(eye0).normalize().scale(64))}; // the end: where the player was turned to look
        System.arraycopy(looks, 0, kl, 0, looks.length);
    }

    /** Finite-difference tangents, in blocks per second, capped so the curve can't overshoot; 0 at the ends and by cuts. */
    private void tangents(Vec3[] k, Vec3[] m, boolean byKind) {
        for (int i = 0; i < k.length; i++) {
            if (i == 0 || i == k.length - 1 || byKind && (kind[i - 1] != SMOOTH || kind[i] != SMOOTH)) {
                m[i] = Vec3.ZERO;
                continue;
            }
            Vec3 t = k[i + 1].subtract(k[i - 1]).scale(1 / (sc.kt[i + 1] - sc.kt[i - 1]));
            double cap = 1.5 * Math.min(k[i].distanceTo(k[i - 1]) / (sc.kt[i] - sc.kt[i - 1]), k[i + 1].distanceTo(k[i]) / (sc.kt[i + 1] - sc.kt[i]));
            double len = t.length();
            m[i] = len > cap ? t.scale(cap / len) : t;
        }
    }

    /** True if the stretch from shot i to i + 1 stays in the open (the player's own spot aside). */
    private boolean clear(ClientLevel level, int i) {
        int n = Math.min(240, 6 + (int) (kp[i].distanceTo(kp[i + 1]) * 1.5));
        for (int j = 1; j < n; j++) {
            Vec3 q = hermite(kp[i], mp[i], kp[i + 1], mp[i + 1], sc.kt[i + 1] - sc.kt[i], j / (float) n);
            if (q.distanceToSqr(eye0) > 2.25 && !open(level, q)) return false;
        }
        return true;
    }

    /** A spot at this angle and distance from the storm, up above its foot (or above the ground there, if ground). */
    private Vec3 around(ClientLevel level, boolean roof, double angle, double dist, float up, boolean ground) {
        double x = base.x + Math.cos(angle) * dist, z = base.z + Math.sin(angle) * dist;
        double surface = roof ? base.y : level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
        return new Vec3(x, ground ? surface + up : roof ? base.y + up : Math.max(base.y + up, surface + 3), z);
    }

    /** c, or a little above it if c isn't in the open (where there is a sky to go up into); null if nothing is. */
    private static Vec3 place(ClientLevel level, boolean roof, Vec3 c, boolean lift) {
        for (int dy : new int[] {0, 4, 9, 16}) {
            Vec3 q = c.add(0, dy, 0);
            if (open(level, q)) return q;
            if (!lift || roof) break;
        }
        return null;
    }

    /** True if a camera at c is in the open: no block to collide with or fluid around it, in a loaded chunk. */
    private static boolean open(ClientLevel level, Vec3 c) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 9; i++) { // the center and the corners of a small box
            m.set(c.x + (i == 8 ? 0 : (i & 1) == 0 ? -.45 : .45), c.y + (i == 8 ? 0 : (i & 2) == 0 ? -.45 : .45), c.z + (i == 8 ? 0 : (i & 4) == 0 ? -.45 : .45));
            if (!level.hasChunkAt(m)) return false;
            BlockState st = level.getBlockState(m);
            if (!st.getFluidState().isEmpty() || !st.getCollisionShape(level, m).isEmpty()) return false;
        }
        return true;
    }

    /** True if nothing solid stands between a and (almost) b. */
    private static boolean sees(ClientLevel level, LocalPlayer p, Vec3 a, Vec3 b) {
        BlockHitResult hit = level.clip(new ClipContext(a, b, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(b) < 25;
    }

    /** The funnel's tip as it comes down (StormRenderer's tipY), at least a little above the ground. */
    private Vec3 tip(float t) { return base.add(0, Math.max(h - StormBirth.descend(t) * (h + 8), 2), 0); }

    // ---------------------------------------------------------------- splines

    private int segment(float t) {
        int i = 0;
        while (i < sc.kt.length - 2 && t >= sc.kt[i + 1]) i++;
        return i;
    }

    private static Vec3 hermite(Vec3 p0, Vec3 m0, Vec3 p1, Vec3 m1, float dt, float s) {
        float s2 = s * s, s3 = s2 * s;
        return p0.scale(2 * s3 - 3 * s2 + 1).add(m0.scale((s3 - 2 * s2 + s) * dt)).add(p1.scale(3 * s2 - 2 * s3)).add(m1.scale((s3 - s2) * dt));
    }

    private Vec3 pos(float t) {
        int i = segment(t);
        float dt = sc.kt[i + 1] - sc.kt[i], s = Mth.clamp((t - sc.kt[i]) / dt, 0, 1);
        return kind[i] != SMOOTH ? s < .5f ? kp[i] : kp[i + 1] : hermite(kp[i], mp[i], kp[i + 1], mp[i + 1], dt, s);
    }

    private Vec3 look(float t) {
        int i = segment(t);
        float dt = sc.kt[i + 1] - sc.kt[i];
        float s = Mth.clamp((t - sc.kt[i]) / dt, 0, 1);
        return kind[i] != SMOOTH ? s < .5f ? kl[i] : kl[i + 1] : hermite(kl[i], ml[i], kl[i + 1], ml[i + 1], dt, s); // a cut cuts the view too
    }

    private float fov(float t) {
        int i = segment(t);
        float u = (t - sc.kt[i]) / (sc.kt[i + 1] - sc.kt[i]);
        float f = kind[i] == CUT ? u < .5f ? sc.fov[i] : sc.fov[i + 1] : Mth.lerp(smooth(u), sc.fov[i], sc.fov[i + 1]);
        if (birth && t > IGNITION) f += .14f * (float) Math.exp(-(t - IGNITION) * 4); // the kick as the beam fires
        if (birth && t > TOUCHDOWN) f += .08f * (float) Math.exp(-(t - TOUCHDOWN) * 3);
        for (int k = sc.lineAt.length - 1; k >= 0; k--) { // a slow lean in while something is said with feeling
            if (t < sc.lineAt[k]) continue;
            if (strong[k]) f -= .045f * smooth((t - sc.lineAt[k]) / .5f) * (1 - smooth((t - sc.lineAt[k] - 1.2f) / .8f));
            break;
        }
        return f;
    }

    private static final float BLUR = .55f; // seconds either side the camera's path is averaged over
    private static final float[] BLUR_W = {.06f, .12f, .2f, .24f, .2f, .12f, .06f};

    /**
     * The spline averaged over a short window around t (a gaussian), so even at the key shots there is no corner in
     * the motion. Never across a cut, and the window narrows to nothing towards the ends of a smooth run, so the
     * camera still leaves from and comes back to exactly the right spot.
     */
    private Vec3 smoothed(float t, boolean look) {
        int i = segment(t);
        if (kind[i] != SMOOTH) return look ? look(t) : pos(t);
        int lo = i, hi = i;
        while (lo > 0 && kind[lo - 1] == SMOOTH) lo--;
        while (hi < kind.length - 1 && kind[hi + 1] == SMOOTH) hi++;
        float r0 = sc.kt[lo], r1 = sc.kt[hi + 1], span = BLUR * Mth.clamp(Math.min(t - r0, r1 - t) / .6f, 0, 1);
        if (span <= 1e-3f) return look ? look(t) : pos(t);
        double x = 0, y = 0, z = 0;
        for (int k = 0; k < BLUR_W.length; k++) {
            float u = Mth.clamp(t + (k - 3) * span / 3, r0, r1);
            Vec3 v = look ? look(u) : pos(u);
            x += v.x * BLUR_W[k];
            y += v.y * BLUR_W[k];
            z += v.z * BLUR_W[k];
        }
        return new Vec3(x, y, z);
    }

    /** 0..1 how much of the black of a dip shows. */
    private float dip(float t) {
        int i = segment(t);
        if (kind[i] != DIP) return 0;
        float mid = (sc.kt[i] + sc.kt[i + 1]) / 2, w = Math.min(.45f, (sc.kt[i + 1] - sc.kt[i]) / 2);
        return smooth(1 - Math.abs(t - mid) / w);
    }

    // ---------------------------------------------------------------- the camera

    private View frame(LocalPlayer p, float partial) {
        float t = lastT = time(partial);
        Vec3 eye = p.getEyePosition(partial), drift = eye.subtract(eye0); // the player stands still, but may be nudged
        Vec3 pos = smoothed(t, false).add(drift), target = smoothed(t, true).add(drift);
        // the living camera: none at the very start and end, so it leaves and comes back to the eyes exactly
        float live = smooth((t - sc.start) / 1.5f) * (1 - smooth((t - sc.end + 1.5f) / 1.2f));
        // shakes: a jolt as the beam fires, a rumble growing as the vortex closes in, the slam as it shuts
        float shake = !birth ? 0 : .16f * (float) Math.exp(-Math.max(0, t - IGNITION) * 3) * (t > IGNITION ? 1 : 0)
                + .07f * StormBirth.descend(t) * (t < TOUCHDOWN ? 1 : 0)
                + .5f * (float) Math.exp(-Math.max(0, t - TOUCHDOWN) * 1.8f) * (t > TOUCHDOWN ? 1 : 0);
        float motion = Accessibility.motion(); // the game's "Distortion Effects"
        shake *= motion;
        live *= motion;
        pos = pos.add(noise(t * 7.3f, 0) * shake, noise(t * 8.1f, 2) * shake * .7f, noise(t * 6.7f, 4) * shake);
        Vec3 d = target.subtract(pos);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90, pitch = (float) -(Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG);
        yaw += live * (.35f * noise(t * .5f, 1) + shake * 2 * noise(t * 9, 3));
        pitch += live * (.25f * noise(t * .43f, 5) + shake * 2 * noise(t * 8.6f, 6));
        // banking into the turns, like a drone; nothing across a cut
        int i = segment(t);
        float bank = 0;
        if (kind[i] == SMOOTH) {
            Vec3 v = smoothed(t + .04f, false).subtract(smoothed(t - .04f, false)).scale(1 / .08f);
            float yr = yaw * Mth.DEG_TO_RAD;
            bank = Mth.clamp((float) (v.x * -Mth.cos(yr) + v.z * -Mth.sin(yr)) * .15f, -5, 5);
        }
        float roll = live * (bank + shake * 4 * noise(t * 7.7f, 7));
        return new View(pos, yaw, pitch, roll, pos.distanceToSqr(eye) > .3); // away from the eyes the player is in the picture
    }

    /** Smooth noise in about -1..1: a few incommensurate sines. */
    private static float noise(float x, int k) { return .6f * Mth.sin(x + k * 1.7f) + .3f * Mth.sin(x * 2.13f + k * 3.1f) + .1f * Mth.sin(x * 4.37f + k); }

    private static float smooth(float x) { return StormBirth.smooth(x); }

    // ---------------------------------------------------------------- the overlay: dialogue, title, skip

    private void draw(GuiGraphics g, float t) {
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), ht = g.guiHeight();
        float black = Math.max(fade >= 0 ? fade / (float) FADE : 0, dip(t));
        if (black > 0) g.fill(0, 0, w, ht, (int) (black * 255) << 24);
        if (birth) { // the screen lit for a moment by the beam and by the storm shutting, in its own color
            float flash = (t > IGNITION ? .28f * (float) Math.exp(-(t - IGNITION) * 5) : 0) + (t > TOUCHDOWN ? .4f * (float) Math.exp(-(t - TOUCHDOWN) * 4) : 0);
            flash = Accessibility.flash(flash);
            if (flash > .02f) g.fill(0, 0, w, ht, (int) (flash * 255) << 24 | type.core & 0xFFFFFF);
        }
        int shade = (int) (95 * smooth((t - sc.start) / 1.5f) * (1 - smooth((t - sc.end + 1.2f) / 1.2f))); // the frame darkening towards top and bottom
        g.fillGradient(0, 0, w, ht / 3, shade << 24, 0);
        g.fillGradient(0, ht * 2 / 3, w, ht, 0, shade << 24);
        int bar = Math.round(ht * .055f * smooth((t - sc.start) / 1.2f) * (1 - smooth((t - sc.end + 1.2f) / 1.2f))); // thin: a hint of film
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, ht - bar, w, ht, 0xFF000000);
        title(g, mc.font, t - sc.title, w, ht);
        for (int i = sc.lineAt.length - 1; i >= 0; i--) {
            if (t < sc.lineAt[i]) continue;
            dialogue(g, mc, i, t - sc.lineAt[i], i + 1 < sc.lineAt.length ? sc.lineAt[i + 1] - sc.lineAt[i] - .12f : 4, w, ht);
            break;
        }
        float hintA = smooth((t - sc.start - .8f) / .8f) * (1 - smooth((t - sc.end + 2.5f) / .8f)); // bottom right, faint: hold sneak to skip
        if (hintA > .05f) {
            Component hint = Component.translatable("cutscene.horizons_unbound.skip", mc.options.keyShift.getTranslatedKeyMessage());
            int hw = mc.font.width(hint), x = w - hw - 6, y = ht - 14;
            g.drawString(mc.font, hint, x, y, (int) (hintA * 110) << 24 | 0xFFFFFF, true);
            if (skipHold > 0) g.fill(x, y + 10, x + hw * skipHold / SKIP_HOLD, y + 11, 0xC0FFFFFF);
        }
    }

    /** The storm's name, big and letter-spaced, with what it is beneath a line that draws itself out. */
    private void title(GuiGraphics g, Font font, float age, int w, int ht) {
        if (age < 0 || age > TITLE_LEN) return;
        float a = Math.min(1, age / .9f) * Math.min(1, (TITLE_LEN - age) / 1f);
        if (a < .03f) return;
        String name = String.join(" ", Component.translatable("cutscene.horizons_unbound.title." + kindId()).getString().split(""));
        String kind = Component.translatable("cutscene.horizons_unbound.kind." + kindId()).getString();
        int cy = (int) (ht * .36f), alpha = (int) (a * 255) << 24;
        float sc = Math.min(3, (w - 20f) / font.width(name)) * (1 + .04f * age / TITLE_LEN); // slowly coming closer
        g.pose().pushPose();
        g.pose().translate(w / 2f, cy, 0);
        g.pose().scale(sc, sc, 1);
        g.drawString(font, name, -font.width(name) / 2, -4, alpha | 0xFFFFFF, true);
        g.pose().popPose();
        int line = (int) (font.width(kind) * .7f * StormBirth.ease(age / 1.4f)) + 6;
        g.fill(w / 2 - line, cy + (int) (6 * sc) + 2, w / 2 + line, cy + (int) (6 * sc) + 3, (int) (a * 160) << 24 | 0xFFFFFF);
        g.drawString(font, kind, (w - font.width(kind)) / 2, cy + (int) (6 * sc) + 7, alpha | type.core & 0xFFFFFF, true);
        if (!birth) { // a new kind found
            String found = Component.translatable("cutscene.horizons_unbound.discovered").getString();
            g.drawString(font, found, (w - font.width(found)) / 2, cy - (int) (6 * sc) - 12, (int) (a * 200) << 24 | 0xD0D0D0, true);
        }
    }

    // the phrase on show, word by word: text, row and x within the row, when it is said (seconds in), whether it trembles
    private int shownLine = -1;
    private Component[] words;
    private String[] text;
    private static final float LETTER = 1 / 26f; // seconds per letter: typed out at the pace of speech
    private static final java.util.Map<String, Component> GLYPHS = new java.util.HashMap<>(); // italic letters, made once
    private int[] wordRow, wordX, rowW;
    private float[] wordAt;
    private boolean[] wordShaky, wordLoud;
    private float spoken;   // seconds the phrase takes to say

    private String line(int i) { return Component.translatable("cutscene.horizons_unbound." + sc.lines[i].replace("%", kindId())).getString(); }

    /**
     * A phrase of the player's thoughts as plain italic text over the bottom of the screen, typed out at the pace of
     * speech (silently): letter by letter, each fading in and settling into place, with a breath
     * at a comma and a longer one at an ellipsis or the end of a sentence. A *starred* word trembles a little; a
     * SHOUTED one lands bigger and settles. The phrase stays to be read, then fades before the next one (room: the
     * seconds until then). Wrapped if long.
     */
    private void dialogue(GuiGraphics g, Minecraft mc, int line, float age, float room, int w, int ht) {
        Font font = mc.font;
        if (line != shownLine) layout(font, line, Math.min(w * .7f, 300));
        float dur = Math.min(room, spoken + 1.3f), a = smooth(age / .15f) * smooth((dur - age) / .35f);
        if (age >= dur || a < .03f) return;
        int lh = 11, y0 = ht - 30 - (rowW.length - 1) * lh;
        for (int k = 0; k < words.length; k++) {
            float since = age - wordAt[k];
            if (since <= 0) continue;
            float sc = wordLoud[k] ? 1 + .28f * (float) Math.exp(-since * 7) : 1; // a shout lands big
            float tx = wordShaky[k] ? .3f * Mth.sin(age * 31 + k) : 0, ty = wordShaky[k] ? .3f * Mth.cos(age * 27 + k * 2) : 0;
            g.pose().pushPose();
            g.pose().translate((w - rowW[wordRow[k]]) / 2f + wordX[k] + tx, y0 + wordRow[k] * lh + ty + 4, 0);
            g.pose().scale(sc, sc, 1);
            String word = text[k];
            float cx = 0;
            for (int c = 0; c < word.length(); c++) { // letter by letter, each fading in and rising into place
                String ch = String.valueOf(word.charAt(c));
                float in = Mth.clamp((since - c * LETTER) / .08f, 0, 1), la = a * in;
                if (la >= .03f) { // (the font draws a near-zero alpha as opaque)
                    g.pose().pushPose();
                    g.pose().translate(cx, (1 - in) * 3, 0);
                    g.drawString(font, GLYPHS.computeIfAbsent(ch, c1 -> Component.literal(c1).withStyle(net.minecraft.ChatFormatting.ITALIC)), 0, -4, (int) (la * 255) << 24 | 0xFFFFFF, true);
                    g.pose().popPose();
                }
                cx += font.width(GLYPHS.computeIfAbsent(ch, c1 -> Component.literal(c1).withStyle(net.minecraft.ChatFormatting.ITALIC)));
            }
            g.pose().popPose();
        }
    }

    /**
     * Splits line i into words, wraps them to maxW and works out when each is said. Chinese and Japanese have no
     * spaces: there each character is a word of its own, set without a gap, so long lines still wrap.
     */
    private void layout(Font font, int line, float maxW) {
        shownLine = line;
        java.util.List<String> tokens = new java.util.ArrayList<>();
        java.util.List<Boolean> shaky = new java.util.ArrayList<>(), gap = new java.util.ArrayList<>();
        boolean star = false;
        for (String raw : line(line).split(" ")) {
            if (raw.isEmpty()) continue;
            if (!cjk(raw)) {
                if (raw.startsWith("*")) star = true;
                tokens.add(raw.replace("*", ""));
                shaky.add(star);
                gap.add(true);
                if (raw.endsWith("*") || raw.replaceAll("[.,!?]+$", "").endsWith("*")) star = false;
                continue;
            }
            for (int i = 0; i < raw.length(); ) {
                int cp = raw.codePointAt(i);
                i += Character.charCount(cp);
                if (cp == '*') {
                    star = !star;
                    continue;
                }
                tokens.add(new String(Character.toChars(cp)));
                shaky.add(star);
                gap.add(false);
            }
            if (!gap.isEmpty()) gap.set(gap.size() - 1, true);
        }
        int n = tokens.size(), row = 0, x = 0, space = font.width(" ");
        words = new Component[n];
        text = new String[n];
        wordRow = new int[n];
        wordX = new int[n];
        wordAt = new float[n];
        wordShaky = new boolean[n];
        wordLoud = new boolean[n];
        java.util.List<Integer> widths = new java.util.ArrayList<>();
        float at = .05f;
        for (int k = 0; k < n; k++) {
            String word = tokens.get(k);
            wordShaky[k] = shaky.get(k);
            wordLoud[k] = word.matches(".*\\p{Lu}{3}.*");
            words[k] = Component.literal(word).withStyle(net.minecraft.ChatFormatting.ITALIC);
            text[k] = word;
            int ww = font.width(words[k]), after = gap.get(k) ? space : 0;
            boolean closing = word.length() == 1 && "\u3002\uff0c\u3001\uff01\uff1f\u2026\u2014\uff09\u300d".indexOf(word.charAt(0)) >= 0;
            if (x > 0 && x + ww > maxW && !closing) { // (CJK punctuation never starts a line)
                widths.add(x - (k > 0 && gap.get(k - 1) ? space : 0));
                row++;
                x = 0;
            }
            wordRow[k] = row;
            wordX[k] = x;
            x += ww + after;
            wordAt[k] = at;
            at += (gap.get(k) ? word.length() + 1 : 2) * LETTER; // its letters and a space (a CJK character: two letters' time); then the breaths
            if (word.endsWith("...") || word.endsWith("\u2026") || word.endsWith("\u2014")) at += .5f;
            else if (word.endsWith(".") || word.endsWith("!") || word.endsWith("?") || word.endsWith("\u3002") || word.endsWith("\uff01") || word.endsWith("\uff1f")) at += .4f;
            else if (word.endsWith(",") || word.endsWith("\uff0c") || word.endsWith("\u3001")) at += .2f;
        }
        widths.add(Math.max(0, x - (n > 0 && gap.get(n - 1) ? space : 0)));
        rowW = widths.stream().mapToInt(Integer::intValue).toArray();
        spoken = at;
    }

    /** True if the word is written in Chinese or Japanese characters (no spaces between words). */
    private static boolean cjk(String s) {
        return s.codePoints().anyMatch(cp -> {
            Character.UnicodeScript sc = Character.UnicodeScript.of(cp);
            return sc == Character.UnicodeScript.HAN || sc == Character.UnicodeScript.HIRAGANA || sc == Character.UnicodeScript.KATAKANA;
        });
    }

    // ---------------------------------------------------------------- events

    public static void onTick() {
        try {
            tick();
        } catch (RuntimeException ex) {
            fail(ex);
        }
    }

    private static void tick() {
        Cutscene c = active;
        Minecraft mc = Minecraft.getInstance();
        if (c == null || mc.isPaused()) return;
        LocalPlayer p = mc.player;
        if (p == null || !p.isAlive() || mc.level == null || mc.level.dimension() != c.dimension || p.getEyePosition().distanceToSqr(c.eye0) > 64 * 64) {
            c.stop(); // died, went elsewhere or was teleported away
            return;
        }
        c.ticks++;
        lastT = c.time(0);
        c.lost = ClientStorms.get(c.stormId) == null ? c.lost + 1 : 0;
        float t = c.time(0);
        if (c.lost > 40 || t >= c.sc.end || c.fade >= FADE) {
            c.stop();
            return;
        }
        if (c.birth) c.act(p, t);
        else if (t < c.sc.kt[1] + .2f) { // the player turns to look while the camera is still behind their eyes
            Vec3 d = c.look(t).subtract(p.getEyePosition());
            float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90;
            p.setYRot(p.getYRot() + Mth.wrapDegrees(yaw - p.getYRot()));
            p.setXRot((float) -(Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG));
        }
        if (c.fade >= 0) c.fade++;
        else if (mc.options.keyShift.isDown()) { if (++c.skipHold >= SKIP_HOLD) c.fade = 0; }
        else c.skipHold = 0;
    }

    public static boolean onGui(net.minecraft.client.gui.GuiGraphics graphics, float partial) { // no HUD, chat or hotbar: just the dialogue box and the title
        Cutscene c = active;
        if (c == null) return false;
        try {
            c.draw(graphics, c.time(partial));
        } catch (RuntimeException ex) {
            fail(ex);
        }
        return true;
    }

    public static double onFov(double fov, float partial) {
        Cutscene c = active;
        return c == null ? fov : fov * c.fov(c.time(partial));
    }

    public static void onMove(Input in, net.minecraft.world.entity.player.Player player) { // the player stands still meanwhile
        if (active == null) return;
        in.forwardImpulse = in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = in.jumping = in.shiftKeyDown = false;
        Cutscene c = active;
        if (!c.birth || !player.onGround()) return; // (sneaking: no stepping off an edge; in the air it would mean "down")
        float t = c.time(0);
        if (t >= IGNITION && t < IGNITION + 1.1f) { // the flinch: a crouch and a step back from the beam
            in.shiftKeyDown = true;
            if (t < IGNITION + .4f) in.forwardImpulse = -.3f;
        } else if (t >= TOUCHDOWN && t < TOUCHDOWN + .7f) in.shiftKeyDown = true;
    }

 // where the turn speed formula is zero





    public static void onLogout() { active = null; }
}
