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
        if (!StormsConfig.effects(s.type)) return;
        List<ServerPlayer> near = playersIn(level, s, s.zone());
        // inside End and Nether columns players float (low gravity, updrafts): landing never hurts there
        if (s.type != StormType.OVERWORLD)
            for (ServerPlayer p : near) if (s.inside(p.getX(), p.getY(), p.getZ()) && p.fallDistance > 2.5f) p.fallDistance = 2.5f;
        run(s.type, level, m, s, near);
        // a merged storm does what both its kinds do
        if (s.mix != null && s.mix != s.type && StormsConfig.effects(s.mix)) run(s.mix, level, m, s, near);
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
        List<ServerPlayer> near = playersIn(level, omen, omen.radius() * 6);
        if (near.isEmpty()) return;
        for (net.minecraft.world.entity.animal.Animal a : around(level, near, net.minecraft.world.entity.animal.Animal.class, 32)) {
            if (a.isLeashed() || level.random.nextFloat() > .15f + .35f * p) continue;
            if (a.onGround() && level.random.nextBoolean()) {
                a.setDeltaMovement(a.getDeltaMovement().add(0, .42, 0)); // a start
                a.hurtMarked = true;
            }
            a.getNavigation().moveTo(a.getX() + level.random.nextInt(13) - 6, a.getY(), a.getZ() + level.random.nextInt(13) - 6, 1.6);
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

    /** Natural ground a storm may reshape: stone, dirt, sand, gravel, snow and the like, never anything built. */
    static boolean natural(BlockState s) {
        return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL)
                || s.is(BlockTags.SNOW) || s.is(Blocks.CLAY) || s.is(BlockTags.TERRACOTTA) || s.is(BlockTags.BASE_STONE_NETHER)
                || s.is(BlockTags.NYLIUM) || s.is(Blocks.SOUL_SAND) || s.is(Blocks.SOUL_SOIL) || s.is(Blocks.END_STONE)
                || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.BLACKSTONE) || s.is(Blocks.BASALT);
    }

    /** May the storm change this block: natural ground no player put there. */
    static boolean reshape(ServerLevel level, BlockPos pos, BlockState s) { return natural(s) && !PlacedBlocks.placed(level, pos); }

    /** No player put a block here. */
    static boolean free(ServerLevel level, BlockPos pos) { return !PlacedBlocks.placed(level, pos); }

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
