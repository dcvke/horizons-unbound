package com.horizonsunbound;

import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The storms' advancements (data/horizons_unbound/advancements): each kind seen, all four, the great storm, standing
 * in the eye, tempered gear, a Deep Dark storm hearing you. They use the "impossible" trigger and are granted from
 * here, so they work with cutscenes off too, and quest mods can build on them.
 */
public final class StormAdvancements {
    private static final int SEE = 160; // blocks between a player under the sky and a storm's edge for it to count as seen

    private StormAdvancements() {}

    /** Once a second: what storms does this player see or stand in? */
    public static void tick(ServerPlayer p) {
        if (p.tickCount % 20 != 0 || !p.isAlive() || p.isSpectator()) return;
        ServerLevel level = p.serverLevel();
        boolean sky = level.dimensionType().hasCeiling() || level.canSeeSky(p.blockPosition());
        for (Storm s : StormManager.get(level).storms()) {
            if (s.inside(p.getX(), p.getY(), p.getZ())) award(p, "eye", "inside");
            if (!sky || s.dist(p.getX(), p.getZ()) > s.radius() + SEE) continue;
            for (StormType t : StormType.values()) {
                if (!s.has(t)) continue;
                award(p, "root", "seen");
                award(p, "seen_" + t.id, "seen");
                award(p, "all_kinds", t.id);
            }
            if (s.grand) award(p, "grand", "seen");
        }
    }

    /** Grants one criterion of one of the mod's advancements (no-op if a data pack removed it). */
    public static void award(ServerPlayer p, String advancement, String criterion) {
        Advancement a = p.serverLevel().getServer().getAdvancements().getAdvancement(new ResourceLocation(HorizonsUnbound.MODID, advancement));
        if (a != null && !p.getAdvancements().getOrStartProgress(a).isDone()) p.getAdvancements().award(a, criterion);
    }

    /** Gear was tempered: every player close by sees it happen. */
    public static void tempered(ServerLevel level, double x, double y, double z) {
        for (ServerPlayer p : level.players()) if (p.distanceToSqr(x, y, z) < 32 * 32) award(p, "tempered", "tempered");
    }
}
