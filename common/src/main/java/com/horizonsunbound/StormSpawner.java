package com.horizonsunbound;

import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Storms appear on their own in the Overworld: near a player but out of their way, at the far edge of what
 * they can see, never on top of a player, their bed, the world spawn or a village.
 */

public final class StormSpawner {
    private static final int MINUTE = 1200;                      // ticks
    private static final int KEEP_AWAY = 100;                    // blocks between the storm's edge and anything to protect

    /**
     * Once a second: while no storm is out, a countdown of minutesBetweenStorms runs (it waits while one is out, so it
     * starts when the last one is gone); at its end a storm comes with stormChance, else it starts over. While one is
     * out, a second may come to meet it, a few minutes after it rose.
     */

    public static void onLevelTick(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) return;
        if (level.getGameTime() % 20 != 0 || level.players().isEmpty()) return;
        StormManager m = StormManager.get(level);
        int interval = StormsConfig.SPAWN_INTERVAL.get() * 60; // seconds
        if (!m.quiet()) {
            m.calm = interval;
            if (m.twinIn > 0 && m.storms().size() == 1 && --m.twinIn == 0) tryTwin(level, m.storms().get(0));
            return;
        }
        if (m.calm < 0 || m.calm > interval) m.calm = interval;
        if (--m.calm > 0) return;
        m.calm = interval;
        m.setDirty();
        if (level.random.nextDouble() >= StormsConfig.SPAWN_CHANCE.get()) return;
        Storm s;
        try {
            s = trySpawn(level);
        } catch (RuntimeException e) { // another mod's world generation in the way: skip this one, never crash
            org.slf4j.LoggerFactory.getLogger("horizons_unbound").error("Horizons Unbound: a storm could not come", e);
            s = null;
        }
        m.twinIn = s != null && !s.grand && level.random.nextDouble() < StormsConfig.MERGE_CHANCE.get() ? 120 + level.random.nextInt(300) : -1;
    }

    /**
     * A second storm of another kind, rising (after its omens) close enough to the first that both turn towards each
     * other and merge within a few minutes. Returns it, or null when there was no free spot.
     */
    public static Storm tryTwin(ServerLevel level, Storm first) {
        List<ServerPlayer> players = level.players();
        if (players.isEmpty() || first.target != null || first.grand) return null;
        List<StormType> types = unlocked(players.get(level.random.nextInt(players.size())));
        if (types.size() > 1) types.remove(first.type);
        int diameter = Mth.nextInt(level.random, Storm.MIN_DIAMETER, Storm.MAX_DIAMETER);
        float r = diameter / 2f;
        for (int attempt = 0; attempt < 8; attempt++) {
            float ang = level.random.nextFloat() * Mth.TWO_PI, dist = (first.radius() + r) * 1.1f + 30;
            int x = Mth.floor(first.x + Mth.cos(ang) * dist), z = Mth.floor(first.z + Mth.sin(ang) * dist);
            BlockPos pos = new BlockPos(x, Storm.groundUnder(level, x + .5, z + .5, r, true), z);
            if (!isClear(level, pos, r + 40)) continue;
            Storm twin = new Storm(UUID.randomUUID(), types.get(level.random.nextInt(types.size())), diameter, x + .5, pos.getY(), z + .5);
            twin.target = first.id;
            first.target = twin.id;
            return StormManager.get(level).announce(level, twin) ? twin : null;
        }
        return null;
    }

    /** Tries to place a storm near a random player; returns it, or null when no free spot was found. */
    public static Storm trySpawn(ServerLevel level) {
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return null;
        ServerPlayer player = players.get(level.random.nextInt(players.size()));
        for (ServerPlayer p : players) if (StormCutscenes.finalePending(p)) player = p; // someone has met every kind: the great storm comes to them
        boolean grand = StormCutscenes.finalePending(player);
        int diameter = grand ? Storm.MAX_DIAMETER : Mth.nextInt(level.random, Storm.MIN_DIAMETER, Storm.MAX_DIAMETER);
        float r = diameter / 2f;
        List<StormType> kinds = unlocked(player);
        if (kinds.isEmpty()) return null; // every kind turned off
        int view = Math.min(level.getServer().getPlayerList().getViewDistance(), com.horizonsunbound.platform.ViewDistances.get(player)) * 16;
        // the far edge of what the player sees, so it shows from afar; with a short view distance just far enough away
        double dist = Math.max(view - 24, r + KEEP_AWAY + 20);
        for (int attempt = 0; attempt < 8; attempt++) {
            float ang = level.random.nextFloat() * Mth.TWO_PI;
            int x = Mth.floor(player.getX() + Mth.cos(ang) * dist), z = Mth.floor(player.getZ() + Mth.sin(ang) * dist);
            BlockPos pos = new BlockPos(x, Storm.groundUnder(level, x + .5, z + .5, r, true), z);
            if (!isClear(level, pos, r + KEEP_AWAY) || !StormsConfig.biomeAllowed(level.getBiome(pos))) continue;
            Storm storm = new Storm(UUID.randomUUID(), kinds.get(level.random.nextInt(kinds.size())), diameter, x + .5, pos.getY(), z + .5);
            if (grand) {
                storm.grand = true;
                StormCutscenes.finaleCame(player);
            }
            storm.setHeading(ang + (level.random.nextFloat() - .5f)); // roughly away from the player
            return StormManager.get(level).announce(level, storm) ? storm : null; // omens first; it rises when the player comes close
        }
        return null;
    }

    /**
     * Storm kinds this player may meet: Overworld storms always; Nether and End storms once they have been to that
     * dimension (the vanilla "We Need to Go Deeper" / "The End?" advancements), unless the config turns this off.
     */
    static List<StormType> unlocked(ServerPlayer player) {
        List<StormType> out = new ArrayList<>(List.of(StormType.OVERWORLD));
        boolean all = !StormsConfig.REQUIRE_VISIT.get();
        if (all || done(player, "story/enter_the_nether")) out.add(StormType.NETHER);
        if (all || done(player, "story/enter_the_end")) out.add(StormType.END);
        if (all || com.horizonsunbound.platform.Services.STORAGE.data(player).getBoolean(DEEP_DARK_SEEN)) out.add(StormType.DEEP_DARK);
        out.removeIf(t -> !StormsConfig.allowed(t));
        return out;
    }

    private static boolean done(ServerPlayer player, String advancement) {
        Advancement a = player.server.getAdvancements().getAdvancement(new ResourceLocation(advancement));
        return a != null && player.getAdvancements().getOrStartProgress(a).isDone();
    }

    private static final String DEEP_DARK_SEEN = "horizons_unbound_deep_dark";

    /** Sculk storms unlock once the player has stood in the Deep Dark (checked every few seconds). */

    public static void onPlayerTick(ServerPlayer p) {
        if (p.tickCount % 100 != 0 || com.horizonsunbound.platform.Services.STORAGE.data(p).getBoolean(DEEP_DARK_SEEN)) return;
        if (p.level().getBiome(p.blockPosition()).is(net.minecraft.world.level.biome.Biomes.DEEP_DARK)) com.horizonsunbound.platform.Services.STORAGE.data(p).putBoolean(DEEP_DARK_SEEN, true);
    }

    /** The flag outlives death (persistent data isn't copied to the new player by itself). */

    public static void onClone(net.minecraft.world.entity.player.Player original, net.minecraft.world.entity.player.Player player) {
        if (original instanceof ServerPlayer before && player instanceof ServerPlayer after)
            com.horizonsunbound.platform.ViewDistances.set(after, com.horizonsunbound.platform.ViewDistances.get(before));
        if (com.horizonsunbound.platform.Services.STORAGE.data(original).getBoolean(DEEP_DARK_SEEN)) com.horizonsunbound.platform.Services.STORAGE.data(player).putBoolean(DEEP_DARK_SEEN, true);
    }

    /** True when no player, bed, world spawn or village is within keep blocks of pos. */
    private static boolean isClear(ServerLevel level, BlockPos pos, float keep) {
        double keepSq = keep * keep;
        if (level.getSharedSpawnPos().distSqr(pos) < keepSq) return false;
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            if (p.level() == level && p.blockPosition().distSqr(pos) < keepSq) return false;
            BlockPos bed = p.getRespawnPosition();
            if (bed != null && p.getRespawnDimension() == level.dimension() && bed.distSqr(pos) < keepSq) return false;
        }
        // a village spreads ~50 blocks around the start position the search returns
        BlockPos village = level.findNearestMapStructure(StructureTags.VILLAGE, pos, Mth.ceil((keep + 50) / 16), false);
        return village == null || village.distSqr(pos) > Mth.square(keep + 50);
    }
}
