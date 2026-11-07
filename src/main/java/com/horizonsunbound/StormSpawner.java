package com.horizonsunbound;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Storms appear on their own in the Overworld: near a player but out of their way, at the far edge of what
 * they can see, never on top of a player, their bed, the world spawn or a village.
 */
@EventBusSubscriber(modid = HorizonsUnbound.MODID)
public final class StormSpawner {
    private static final int MINUTE = 1200;                      // ticks
    private static final int KEEP_AWAY = 100;                    // blocks between the storm's edge and anything to protect

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        long time = level.getGameTime();
        if (time % MINUTE != 0 || level.players().isEmpty()) return;
        StormManager m = StormManager.get(level);
        // once every spawnInterval minutes of play (by default an hour), a storm comes with spawnChance (by default 50%)
        if (time % (StormsConfig.SPAWN_INTERVAL.get() * (long) MINUTE) == 0) {
            if (m.quiet() && level.random.nextDouble() < StormsConfig.SPAWN_CHANCE.get()) trySpawn(level);
        } else if (m.storms().size() == 1 && level.random.nextDouble() < StormsConfig.MERGE_CHANCE.get()) {
            tryTwin(level, m.storms().get(0)); // rarely a second storm comes, and the two run into each other
        }
    }

    /**
     * A second storm of another kind, rising (after its omens) close enough to the first that both turn towards each
     * other and merge within a few minutes. Returns it, or null when there was no free spot.
     */
    public static Storm tryTwin(ServerLevel level, Storm first) {
        List<ServerPlayer> players = level.players();
        if (players.isEmpty() || first.target != null) return null;
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
            StormManager.get(level).announce(level, twin);
            return twin;
        }
        return null;
    }

    /** Tries to place a storm near a random player; returns it, or null when no free spot was found. */
    public static Storm trySpawn(ServerLevel level) {
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return null;
        ServerPlayer player = players.get(level.random.nextInt(players.size()));
        int diameter = Mth.nextInt(level.random, Storm.MIN_DIAMETER, Storm.MAX_DIAMETER);
        float r = diameter / 2f;
        int view = Math.min(level.getServer().getPlayerList().getViewDistance(), player.requestedViewDistance()) * 16;
        // the far edge of what the player sees, so it shows from afar; with a short view distance just far enough away
        double dist = Math.max(view - 24, r + KEEP_AWAY + 20);
        for (int attempt = 0; attempt < 8; attempt++) {
            float ang = level.random.nextFloat() * Mth.TWO_PI;
            int x = Mth.floor(player.getX() + Mth.cos(ang) * dist), z = Mth.floor(player.getZ() + Mth.sin(ang) * dist);
            BlockPos pos = new BlockPos(x, Storm.groundUnder(level, x + .5, z + .5, r, true), z);
            if (!isClear(level, pos, r + KEEP_AWAY)) continue;
            List<StormType> types = unlocked(player);
            Storm storm = new Storm(UUID.randomUUID(), types.get(level.random.nextInt(types.size())), diameter, x + .5, pos.getY(), z + .5);
            storm.setHeading(ang + (level.random.nextFloat() - .5f)); // roughly away from the player
            StormManager.get(level).announce(level, storm); // a minute of omens first
            return storm;
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
        if (all || player.getPersistentData().getBoolean(DEEP_DARK_SEEN)) out.add(StormType.DEEP_DARK);
        return out;
    }

    private static boolean done(ServerPlayer player, String advancement) {
        AdvancementHolder a = player.server.getAdvancements().get(ResourceLocation.withDefaultNamespace(advancement));
        return a != null && player.getAdvancements().getOrStartProgress(a).isDone();
    }

    private static final String DEEP_DARK_SEEN = "horizons_unbound_deep_dark";

    /** Sculk storms unlock once the player has stood in the Deep Dark (checked every few seconds). */
    @SubscribeEvent
    public static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % 100 != 0 || p.getPersistentData().getBoolean(DEEP_DARK_SEEN)) return;
        if (p.level().getBiome(p.blockPosition()).is(net.minecraft.world.level.biome.Biomes.DEEP_DARK)) p.getPersistentData().putBoolean(DEEP_DARK_SEEN, true);
    }

    /** The flag outlives death (persistent data isn't copied to the new player by itself). */
    @SubscribeEvent
    public static void onClone(net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone event) {
        if (event.getOriginal().getPersistentData().getBoolean(DEEP_DARK_SEEN)) event.getEntity().getPersistentData().putBoolean(DEEP_DARK_SEEN, true);
    }

    /** True when no player, bed, world spawn or village is within keep blocks of pos. */
    private static boolean isClear(ServerLevel level, BlockPos pos, float keep) {
        double keepSq = keep * keep;
        if (level.getLevelData().getSpawnPos().distSqr(pos) < keepSq) return false;
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
