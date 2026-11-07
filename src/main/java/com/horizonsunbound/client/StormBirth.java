package com.horizonsunbound.client;

import com.horizonsunbound.Storm;
import net.minecraft.util.Mth;

/**
 * The birth of a storm, as a timeline in seconds since it rose (0 .. LENGTH). Everything about how it is born is
 * a function of that one number, so it is the same on every client and a cutscene camera can key on the beats.
 * It forms the way a real tornado does: the cloud first, then a funnel lowering out of it to the ground.
 * <pre>
 *  0.0 - 2.3  GATHER     sparks spiral in from all around to a pulsing seed of light on the ground
 *  2.3        IGNITION   a beam shoots up the full height and feeds the sky
 *  2.6 - 10   UNFURL     the cloud cap spreads out from the top of the beam, a glowing edge running ahead of it
 *  4.5 - 11   DESCENT    a thin, swaying rope of a funnel lowers out of the cloud along the beam; the rings ride
 *                        on it, small and dim near its tip; dust whirls up from the ground to meet it
 * 11          TOUCHDOWN  the funnel hits the ground: a flash and a shockwave
 *  8 - 16                the funnel thickens and fills out into the straight column, its sway dying down
 * 12.5 - 16.5            the broken circles of light under the cap appear
 * 18          SETTLE     one last pulse through every ring; the storm is whole
 * </pre>
 */
public final class StormBirth {
    public static final float LENGTH = Storm.BIRTH / 20f;
    public static final float IGNITION = 2.3f, DESCENT = 4.5f, TOUCHDOWN = 11f, SETTLE = 18f;

    private StormBirth() {}

    /** 0..1 how far sparks have gathered (0 before and after the gathering). */
    public static float gather(float s) { return s < 0 || s > IGNITION ? 0 : Math.min(1, s / .4f); }

    /** Brightness of the seed on the ground: pulsing faster as ignition comes. */
    public static float seed(float s) {
        if (s > IGNITION + .1f) return 0;
        float k = s / IGNITION;
        return k * k * (.6f + .4f * Mth.sin(s * (6 + 18 * k)));
    }

    /** 0..1 how far up the beam has shot. */
    public static float beamHeight(float s) { return ease((s - IGNITION + .15f) / .35f); }

    /** Brightness of the beam: gone once the funnel has come down along it. */
    public static float beam(float s) { return s < IGNITION - .15f ? 0 : s < 5 ? 1 : 1 - smooth((s - 5) / 4.5f); }

    /** The ground flash: a small one at ignition, the big one at touchdown. */
    public static float flash(float s) {
        float f = s < IGNITION || s > IGNITION + 2 ? 0 : .45f * (float) Math.exp(-(s - IGNITION) * 3);
        return f + (s < TOUCHDOWN || s > TOUCHDOWN + 3 ? 0 : (float) Math.exp(-(s - TOUCHDOWN) * 2));
    }

    /** 0..1 how far the touchdown shockwave has rolled out (-1 when there is none). */
    public static float shock(float s) { return s < TOUCHDOWN || s > TOUCHDOWN + 3.5f ? -1 : ease((s - TOUCHDOWN) / 3.5f); }

    /** 0..1 how far the funnel's tip has come down from the cloud to the ground. */
    public static float descend(float s) { return smooth((s - DESCENT) / (TOUCHDOWN - DESCENT)); }

    /** Width of the column, as a share of its full width. */
    public static float girth(float s) { return .3f + .7f * ease((s - 8) / 8); }

    /** 0..1 the funnel filling out from a cone narrowing to its tip into the straight column. */
    public static float fill(float s) { return ease((s - TOUCHDOWN + 1) / 6); }

    /** 0..1 how much the funnel sways like a rope. */
    public static float rope(float s) { return smooth((s - DESCENT) / 2.5f) * (1 - smooth((s - TOUCHDOWN + 1) / 6)); }

    /** 0..1 the dust whirling up where the funnel comes down. */
    public static float dust(float s) { return smooth((s - 6.5f) / 3) * (1 - smooth((s - 14) / 4)); }

    /** 0..1 how far the cloud cap has spread. */
    public static float unfurl(float s) { return ease((s - 2.6f) / 7.5f); }

    /** 0..1 the broken circles of light under the cap fading in. */
    public static float arcs(float s) { return smooth((s - 12.5f) / 4); }

    /** The last pulse through every ring. */
    public static float settle(float s) { float x = (s - SETTLE) / .6f; return (float) Math.exp(-x * x); }

    /** 0..1 how much the storm darkens the sky and rumbles yet. */
    public static float presence(float s) { return smooth(s / 12); }

    static float ease(float x) { x = Mth.clamp(x, 0, 1); return 1 - (1 - x) * (1 - x) * (1 - x); }

    static float smooth(float x) { x = Mth.clamp(x, 0, 1); return x * x * (3 - 2 * x); }
}
