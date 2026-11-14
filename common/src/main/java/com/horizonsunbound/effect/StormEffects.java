package com.horizonsunbound.effect;

import com.horizonsunbound.HorizonsUnbound;
import com.horizonsunbound.Storm;
import com.horizonsunbound.StormManager;
import com.horizonsunbound.StormType;
import com.horizonsunbound.StormsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import com.horizonsunbound.HorizonsUnbound;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** What storms do to the world around them; dispatches to the per-dimension effects. Shared helpers live here. */
public final class StormEffects {
    private StormEffects() {}

    public static void tick(ServerLevel level, StormManager m, Storm s) {
        if (s.newborn()) return; // nothing happens to the world while the storm is being born
        if (StormsConfig.TRANSFORM_TERRAIN.get()) DimensionPieces.tick(level, m, s);
        StormMobs.tick(level, s);
        if (!StormsConfig.effects(s.type)) return;
        List<ServerPlayer> near = playersIn(level, s, s.zone());
        // inside End and Nether columns players float (low gravity, updrafts): landing never hurts there
        if (s.type != StormType.OVERWORLD)
            for (ServerPlayer p : near) if (s.inside(p.getX(), p.getY(), p.getZ()) && p.fallDistance > 2.5f) p.fallDistance = 2.5f;
        run(s.type, level, m, s, near);
        // a merged storm does what both its kinds do
        if (s.mix != null && s.mix != s.type && StormsConfig.effects(s.mix)) run(s.mix, level, m, s, near);
        if (s.grand) for (StormType k : StormType.values()) if (k != s.type && StormsConfig.effects(k)) run(k, level, m, s, near); // the great storm: all of them
        for (StormType k : StormType.values()) { // addons' effects (HorizonsUnboundApi.addEffect)
            if (!s.has(k)) continue;
            for (var effect : com.horizonsunbound.api.HorizonsUnboundApi.effects(k)) effect.tick(level, s, near);
        }
    }

    private static void run(StormType type, ServerLevel level, StormManager m, Storm s, List<ServerPlayer> near) {
        switch (type) {
            case END -> EndEffects.tick(level, m, s, near);
            case NETHER -> NetherEffects.tick(level, m, s, near);
            case OVERWORLD -> OverworldEffects.tick(level, m, s, near);
            case DEEP_DARK -> SculkEffects.tick(level, m, s, near);
        }
    }

    /**
     * A storm is about to rise here: animals within sight of it grow restless - they start, jump and bolt a few
     * blocks, more often the closer the storm is to rising. Called once a second per coming storm.
     */
    public static void restless(ServerLevel level, Storm omen) {
        float p = omen.omenProgress();
        if (level.getGameTime() % 200 == 0) { // villages near a coming storm ring their bells, and the villagers run inside
            net.minecraft.core.BlockPos c = net.minecraft.core.BlockPos.containing(omen.x, omen.y, omen.z);
            level.getPoiManager().findAll(t -> t.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.MEETING), q -> true, c, (int) omen.zone() + 120,
                    net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).limit(4).forEach(bell -> {
                if (level.getBlockState(bell).getBlock() instanceof net.minecraft.world.level.block.BellBlock b)
                    b.attemptToRing(level, bell, net.minecraft.core.Direction.NORTH);
            });
        }
        List<ServerPlayer> near = playersIn(level, omen, omen.radius() * 6);
        if (near.isEmpty()) return;
        for (net.minecraft.world.entity.animal.Animal a : around(level, near, net.minecraft.world.entity.animal.Animal.class, 32)) {
            if (a.isLeashed() || level.random.nextFloat() > .15f + .35f * p) continue;
            if (a.onGround() && level.random.nextBoolean()) {
                a.setDeltaMovement(a.getDeltaMovement().add(0, .42, 0)); // a start
                a.hurtMarked = true;
            }
            double ax = a.getX() - omen.x, az = a.getZ() - omen.z, al = Math.max(1, Math.sqrt(ax * ax + az * az)); // away from where it will rise
            a.getNavigation().moveTo(a.getX() + ax / al * 12 + level.random.nextInt(5) - 2, a.getY(), a.getZ() + az / al * 12 + level.random.nextInt(5) - 2, 1.6);
            if (level.random.nextInt(4) == 0) a.playAmbientSound();
        }
    }

    /** The storms of a level on either side (the client's copy on a client level). */
    public static Collection<Storm> storms(Level level) {
        return level instanceof ServerLevel sl ? StormManager.get(sl).storms() : Storm.CLIENT;
    }

    /** The storm of this type whose effect zone covers x/z, or null. */
    public static Storm stormAt(Level level, double x, double z, StormType type) {
        for (Storm s : storms(level))
            if (s.has(type) && StormsConfig.effects(type) && s.dist(x, z) < s.zone()) return s;
        return null;
    }

    static List<ServerPlayer> playersIn(ServerLevel level, Storm s, float range) {
        List<ServerPlayer> out = new ArrayList<>(2);
        for (ServerPlayer p : level.players()) if (!p.isSpectator() && s.dist(p.getX(), p.getZ()) < range) out.add(p);
        return out;
    }

    /** Entities of a class near the given players (storm effects only matter where someone can see them). */
    static <T extends Entity> Set<T> around(ServerLevel level, List<ServerPlayer> players, Class<T> cls, double range) {
        Set<T> out = new LinkedHashSet<>();
        for (ServerPlayer p : players) out.addAll(level.getEntitiesOfClass(cls, new AABB(p.blockPosition()).inflate(range)));
        return out;
    }

    /** Natural ground storms may reshape (data pack tag; by default stone, dirt, sand, gravel, snow and the like). */
    public static final TagKey<Block> RESHAPABLE = TagKey.create(Registries.BLOCK, new ResourceLocation(HorizonsUnbound.MODID, "reshapable"));
    /** Blocks storms never touch, whatever else says (data pack tag). */
    public static final TagKey<Block> STORM_PROOF = TagKey.create(Registries.BLOCK, new ResourceLocation(HorizonsUnbound.MODID, "storm_proof"));

    /** May the storm change this block: natural ground (RESHAPABLE) that is free (see free()). */
    static boolean reshape(ServerLevel level, BlockPos pos, BlockState s) { return s.is(RESHAPABLE) && free(level, pos); }

    /** May the storm touch this spot at all: no player put a block there, it isn't STORM_PROOF, and no land claim forbids it. */
    static boolean free(ServerLevel level, BlockPos pos) {
        return !PlacedBlocks.placed(level, pos) && !level.getBlockState(pos).is(STORM_PROOF) && unclaimed(level, pos);
    }

    private static final java.util.Map<net.minecraft.resources.ResourceKey<Level>, it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap> CLAIMS = new java.util.HashMap<>();

    /**
     * True unless a land-claim mod forbids changing blocks here (FTB Chunks, Open Parties and Claims and the like):
     * asked the way they are asked about a player, with a fake "storm" player breaking the block (the event is
     * posted, nothing is broken). Remembered per chunk for half a minute: a storm asks a lot, and claims rarely change.
     */
    static boolean unclaimed(ServerLevel level, BlockPos pos) {
        if (!StormsConfig.RESPECT_CLAIMS.get()) return true;
        var cache = CLAIMS.computeIfAbsent(level.dimension(), k -> new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap());
        long chunk = net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4), now = level.getGameTime(), v = cache.get(chunk);
        if (v != 0 && v >>> 1 > now) return (v & 1) == 1;
        boolean ok = com.horizonsunbound.platform.Services.PROTECTION.canBreak(level, pos);
        if (cache.size() > 4096) cache.clear();
        cache.put(chunk, (now + 600) << 1 | (ok ? 1 : 0));
        return ok;
    }

    /** The top solid ground block at x/z, or null where the chunk isn't loaded or water/lava is on top. */
    static BlockPos ground(ServerLevel level, int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) return null;
        BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
        return level.getFluidState(pos).isEmpty() ? pos : null;
    }

    /** True this close to the world spawn or anyone's bed in this dimension. */
    static boolean nearHome(ServerLevel level, BlockPos pos, int radius) {
        long r2 = (long) radius * radius;
        if (level.dimension() == Level.OVERWORLD && level.getSharedSpawnPos().distSqr(pos) < r2) return true;
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            BlockPos bed = p.getRespawnPosition();
            if (bed != null && p.getRespawnDimension() == level.dimension() && bed.distSqr(pos) < r2) return true;
        }
        return false;
    }

    static boolean anchored(ServerLevel level, Entity e) { return StormManager.get(level).anchored(e.getX(), e.getY(), e.getZ()); }

    /** Seconds-old storms only: a fresh one gives players a moment before it starts acting up. */
    static boolean warmedUp(Storm s) { return s.age > 100; }

    static String id(String path) { return HorizonsUnbound.MODID + ":" + path; }
}
