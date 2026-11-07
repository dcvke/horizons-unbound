package com.horizonsunbound.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** config/horizons_unbound-client.toml */
public final class StormConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue VIEW_DISTANCE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        VIEW_DISTANCE = b.comment(
                        "How far away storms are drawn, in chunks, counted to the nearest part of the storm.",
                        "0 = as far as your render distance (default). Lower it to save performance, raise it to see storms",
                        "beyond your render distance. Storms are not entities, so entity-culling mods don't hide them.")
                .defineInRange("stormViewDistance", 0, 0, 128);
        SPEC = b.build();
    }

    /** Blocks. */
    static int viewDistance(int renderDistanceChunks) {
        int chunks = VIEW_DISTANCE.get();
        return (chunks > 0 ? chunks : renderDistanceChunks) * 16;
    }
}
