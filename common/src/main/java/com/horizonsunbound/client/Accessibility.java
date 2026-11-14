package com.horizonsunbound.client;

import net.minecraft.client.Minecraft;

/**
 * The game's own accessibility settings, as the storms follow them: "Hide Lightning Flashes" turns flashes and
 * flicker into a soft, steady glow; "Distortion Effects" scales the cutscene camera's shakes, sway and banking.
 */
public final class Accessibility {
    private Accessibility() {}

    /** True when the player asked for no lightning flashes. */
    public static boolean noFlashes() {
        Minecraft mc = Minecraft.getInstance();
        return mc.options != null && mc.options.hideLightningFlash().get();
    }

    /** 1 normally, down to 0: how much the camera may shake and sway. */
    public static float motion() {
        Minecraft mc = Minecraft.getInstance();
        return mc.options == null ? 1 : mc.options.screenEffectScale().get().floatValue();
    }

    /** A flash's strength as it may be shown: a quarter of it, at most .25, without flashes. */
    public static float flash(float k) { return noFlashes() ? Math.min(k * .25f, .25f) : k; }
}
