package com.horizonsunbound.client;

import net.neoforged.neoforge.common.ModConfigSpec;


/** config/horizons_unbound-client.toml */
public final class StormConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue VIEW_DISTANCE;
    public static final ModConfigSpec.BooleanValue CUTSCENES;
    public static final ModConfigSpec.DoubleValue VOLUME;
    public static final ModConfigSpec.EnumValue<ShaderMode> SHADER_MODE;

    /** How the storm is drawn under an Iris shader pack (StormRenderer.forwardPack()). */
    public enum ShaderMode { AUTO, CLASSIC, FORWARD }

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        VIEW_DISTANCE = b.comment(
                        "How far away storms are drawn, in chunks, counted to the nearest part of the storm.",
                        "0 = as far as your render distance (default; with Distant Horizons at least 64 chunks). Lower it to save performance, raise it to see storms",
                        "beyond your render distance. Storms are not entities, so entity-culling mods don't hide them.")
                .defineInRange("stormViewDistance", 0, 0, 128);
        CUTSCENES = b.translation("horizons_unbound.configuration.myCutscenes").comment("Show the cutscene of a storm's birth when you come close to one that is about to rise (hold sneak to skip it).")
                .define("cutscenes", true);
        VOLUME = b.comment("Volume of the storms' own sounds (their rumble, birth, wind inside), on top of the game's volume.")
                .defineInRange("stormVolume", 1.0, 0, 1);
        SHADER_MODE = b.comment("With an Iris shader pack: how the storm is drawn. AUTO picks per pack (FORWARD for Photon, CLASSIC for others).",
                "If a pack shows the storm wrong (invisible, a dark hole, black boxes around the glow), try the other one.")
                .defineEnum("shaderPackMode", ShaderMode.AUTO);
        SPEC = b.build();
    }

    private static final boolean DISTANT_HORIZONS = com.horizonsunbound.platform.Services.PLATFORM.isModLoaded("distanthorizons");

    /** Blocks. */
    static int viewDistance(int renderDistanceChunks) {
        int chunks = VIEW_DISTANCE.get();
        if (chunks > 0) return chunks * 16;
        // With Distant Horizons the land is seen for kilometres: a storm should show as far (one storm is cheap to draw)
        return (DISTANT_HORIZONS ? Math.max(renderDistanceChunks, 64) : renderDistanceChunks) * 16;
    }
}
