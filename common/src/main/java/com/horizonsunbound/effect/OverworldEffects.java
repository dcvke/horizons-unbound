package com.horizonsunbound.effect;

import com.horizonsunbound.Storm;
import com.horizonsunbound.StormManager;
import com.horizonsunbound.StormType;
import com.horizonsunbound.StormsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.List;

/**
 * Under an Overworld storm nature changes in front of you: bands of frost, thaw and growth sweep across the
 * ground, rain falls in some pockets under the cap and not in others (Level mixin + client rain), short streams
 * run down the slopes after the rain, the ground glows blue a few seconds before lightning strikes it, and
 * animals drift out to the calm edge while some monsters gather towards the middle.
 */
public final class OverworldEffects {
    private static final int WARN = 60; // ticks of blue glow before a strike

    private OverworldEffects() {}

    static void tick(ServerLevel level, StormManager m, Storm s, List<ServerPlayer> near) {
        strikes(level, m, s, near);
        if (near.isEmpty()) return;
        if (s.age % 5 == 0) seasons(level, s, near);
        if (s.age % 100 == 50) stream(level, m, s, near);
        if (s.age % 40 == 20) migrate(level, s, near);
        if (s.age % 4 == 0) wind(level, s, near);
    }

    /** True where an Overworld storm keeps the rain off (its dry pockets); vanilla rain logic asks this. */
    public static boolean dryAt(Level level, BlockPos pos) {
        for (Storm s : StormEffects.storms(level)) {
            if (s.type != StormType.OVERWORLD || !StormsConfig.OVERWORLD_EFFECTS.get()) continue;
            double x = pos.getX() + .5, z = pos.getZ() + .5;
            if (s.dist(x, z) < s.zone() && s.wetness(x, z) < .5f) return true;
        }
        return false;
    }

    // ---- bands of frost, thaw and growth -------------------------------------------------------------------------

    /** 0 frost, 1 thaw, 2 growth: 40-block bands drifting across the ground at a block a second. */
    private static int band(Storm s, double x, double z) {
        float th = (s.seed() >>> 20 & 1023) / 1023f * Mth.TWO_PI;
        double along = x * Mth.cos(th) + z * Mth.sin(th);
        return Math.floorMod(Mth.floor((along - s.age * .05) / 40), 3);
    }

    private static void seasons(ServerLevel level, Storm s, List<ServerPlayer> near) {
        RandomSource rnd = level.random;
        for (ServerPlayer p : near) {
            for (int i = 0; i < 3; i++) {
                int x = p.getBlockX() + rnd.nextInt(41) - 20, z = p.getBlockZ() + rnd.nextInt(41) - 20;
                if (s.dist(x, z) < s.zone() && level.hasChunk(x >> 4, z >> 4)) season(level, rnd, band(s, x, z), x, z);
            }
        }
    }

    private static void season(ServerLevel level, RandomSource rnd, int band, int x, int z) {
        BlockPos g = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z), up = g.above();
        BlockState gs = level.getBlockState(g), us = level.getBlockState(up);
        switch (band) {
            case 0 -> { // frost: thin ice on still water, a snow layer on the ground
                if (gs.is(Blocks.WATER) && level.getFluidState(g).isSource() && us.isAir() && StormEffects.free(level, g)) {
                    level.setBlock(g, Blocks.FROSTED_ICE.defaultBlockState(), Block.UPDATE_ALL);
                    level.scheduleTick(g, Blocks.FROSTED_ICE, Mth.nextInt(rnd, 60, 120)); // melts again by itself
                } else if (us.isAir() && Blocks.SNOW.defaultBlockState().canSurvive(level, up) && StormEffects.free(level, up)) {
                    level.setBlock(up, Blocks.SNOW.defaultBlockState(), Block.UPDATE_ALL);
                } else return;
                level.sendParticles(ParticleTypes.SNOWFLAKE, x + .5, up.getY() + .5, z + .5, 6, .4, .3, .4, .01);
            }
            case 1 -> { // thaw
                if (us.is(Blocks.SNOW) && StormEffects.free(level, up)) level.setBlock(up, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                else if ((gs.is(Blocks.ICE) || gs.is(Blocks.FROSTED_ICE)) && StormEffects.free(level, g)) level.setBlock(g, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
                else return;
                level.sendParticles(ParticleTypes.WHITE_SMOKE, x + .5, up.getY() + .3, z + .5, 4, .3, .1, .3, .01);
            }
            default -> { // growth: plants grow a few steps, grass sprouts flowers
                if (us.isRandomlyTicking()) {
                    us.randomTick(level, up, rnd);
                    level.getBlockState(up).randomTick(level, up, rnd);
                } else if (gs.is(Blocks.GRASS_BLOCK) && us.isAir() && rnd.nextFloat() < .15f) {
                    ((BonemealableBlock) Blocks.GRASS_BLOCK).performBonemeal(level, rnd, g, gs);
                } else return;
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x + .5, up.getY() + .5, z + .5, 5, .4, .3, .4, 0);
            }
        }
    }

    // ---- streams ------------------------------------------------------------------------------------------------

    /** In a rainy pocket a spring opens on a slope for half a minute; its path washes out and ends in a new pool. */
    private static void stream(ServerLevel level, StormManager m, Storm s, List<ServerPlayer> near) {
        if (!level.isRaining()) return;
        RandomSource rnd = level.random;
        ServerPlayer p = near.get(rnd.nextInt(near.size()));
        float ang = rnd.nextFloat() * Mth.TWO_PI, d = 6 + rnd.nextFloat() * 18;
        int x = Mth.floor(p.getX() + Mth.cos(ang) * d), z = Mth.floor(p.getZ() + Mth.sin(ang) * d);
        if (s.wetness(x, z) < .7f) return;
        BlockPos g = StormEffects.ground(level, x, z);
        if (g == null || !level.getBlockState(g).is(BlockTags.DIRT) || !StormEffects.free(level, g) || !level.getBlockState(g.above()).isAir()
                || StormEffects.nearHome(level, g, StormsConfig.PROTECT_RADIUS.get())) return;
        BlockPos low = downhill(level, g);
        if (low == null || g.getY() - low.getY() < 1) return; // needs a slope to run down
        level.setBlock(g, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        m.addStream(g, level.getGameTime() + 600);
        BlockPos cur = low;
        for (int step = 0; step < 10; step++) { // wash out the way down
            BlockState cs = level.getBlockState(cur);
            if ((cs.is(Blocks.GRASS_BLOCK) || cs.is(Blocks.DIRT)) && StormEffects.free(level, cur)) {
                float f = rnd.nextFloat();
                if (f < .45f) level.setBlock(cur, (f < .3f ? Blocks.COARSE_DIRT : Blocks.GRAVEL).defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            BlockPos next = downhill(level, cur);
            if (next == null || next.getY() >= cur.getY()) break;
            cur = next;
        }
        if (level.getBlockState(cur).is(BlockTags.DIRT) && level.getBlockState(cur.above()).isAir() && StormEffects.free(level, cur)) // the new pool
            level.setBlock(cur, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
        level.playSound(null, g, SoundEvents.BUCKET_EMPTY, SoundSource.AMBIENT, 1, .7f);
    }

    /** The lowest ground among the four neighbours two blocks away, or null if none is loaded and dry. */
    private static BlockPos downhill(ServerLevel level, BlockPos from) {
        BlockPos best = null;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos n = StormEffects.ground(level, from.getX() + dir.getStepX() * 2, from.getZ() + dir.getStepZ() * 2);
            if (n != null && (best == null || n.getY() < best.getY())) best = n;
        }
        return best;
    }

    // ---- charged ground -----------------------------------------------------------------------------------------

    /** Marks a spot WARN ticks ahead (clients show the blue glow), then strikes it and leaves a small scar. */
    private static void strikes(ServerLevel level, StormManager m, Storm s, List<ServerPlayer> near) {
        int coming = 0;
        for (Iterator<Storm.Mark> it = s.marks.iterator(); it.hasNext(); ) {
            Storm.Mark mk = it.next();
            if (mk.kind() != Storm.Mark.STRIKE) continue;
            if (mk.a() > s.age) { coming++; continue; }
            it.remove();
            m.resync();
            BlockPos g = new BlockPos(mk.x(), mk.y(), mk.z());
            if (!level.isLoaded(g)) continue;
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(Vec3.atBottomCenterOf(g.above()));
                level.addFreshEntity(bolt);
            }
            scar(level, g);
        }
        if (near.isEmpty() || coming >= 3 || s.age % 20 != 0 || level.random.nextFloat() > .08f) return;
        RandomSource rnd = level.random;
        ServerPlayer p = near.get(rnd.nextInt(near.size()));
        float ang = rnd.nextFloat() * Mth.TWO_PI, d = 5 + rnd.nextFloat() * 20;
        BlockPos g = StormEffects.ground(level, Mth.floor(p.getX() + Mth.cos(ang) * d), Mth.floor(p.getZ() + Mth.sin(ang) * d));
        if (g == null || s.dist(g.getX(), g.getZ()) > s.zone() || StormEffects.nearHome(level, g, StormsConfig.PROTECT_RADIUS.get())) return;
        s.marks.add(new Storm.Mark(Storm.Mark.STRIKE, g.getX(), g.getY(), g.getZ(), s.age + WARN));
        m.resync();
    }

    /** A star of tuff around the strike, sand fused to glass, and now and then raw copper in the middle. */
    private static void scar(ServerLevel level, BlockPos center) {
        RandomSource rnd = level.random;
        for (Direction dir : new Direction[] {null, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            int len = dir == null ? 1 : 1 + rnd.nextInt(2);
            for (int k = dir == null ? 0 : 1; k <= (dir == null ? 0 : len); k++) {
                BlockPos g = StormEffects.ground(level, center.getX() + (dir == null ? 0 : dir.getStepX() * k), center.getZ() + (dir == null ? 0 : dir.getStepZ() * k));
                if (g == null) continue;
                BlockState gs = level.getBlockState(g);
                if (!StormEffects.reshape(level, g, gs)) continue;
                Block b = gs.is(BlockTags.SAND) ? Blocks.GLASS : dir == null && rnd.nextFloat() < .3f ? Blocks.RAW_COPPER_BLOCK
                        : rnd.nextBoolean() ? Blocks.TUFF : Blocks.COBBLED_DEEPSLATE;
                level.setBlock(g, b.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /** Inside the column the air turns slowly around the axis and drifts loose items along with it. */
    private static void wind(ServerLevel level, Storm s, List<ServerPlayer> near) {
        for (net.minecraft.world.entity.item.ItemEntity item : StormEffects.around(level, near, net.minecraft.world.entity.item.ItemEntity.class, 24)) {
            if (!s.inside(item.getX(), item.getY(), item.getZ())) continue;
            double dx = item.getX() - s.x, dz = item.getZ() - s.z, d = Math.sqrt(dx * dx + dz * dz) + 1e-3;
            item.setDeltaMovement(item.getDeltaMovement().add(-dz / d * .03, 0, dx / d * .03)); // clockwise seen from above
            item.hasImpulse = true;
        }
    }

    // ---- migration ----------------------------------------------------------------------------------------------

    /** Animals head out to the calm edge of the storm, some monsters in towards its middle: read the storm by them. */
    private static void migrate(ServerLevel level, Storm s, List<ServerPlayer> near) {
        RandomSource rnd = level.random;
        for (Animal a : StormEffects.around(level, near, Animal.class, 48)) {
            double d = s.dist(a.getX(), a.getZ());
            if (d > s.zone() * .95 || d < 1 || a.isLeashed() || a instanceof TamableAnimal t && t.isOrderedToSit()
                    || !a.getNavigation().isDone() || rnd.nextFloat() < .4f) continue;
            a.getNavigation().moveTo(a.getX() + (a.getX() - s.x) / d * 12, a.getY(), a.getZ() + (a.getZ() - s.z) / d * 12, 1.15);
        }
        for (Monster mo : StormEffects.around(level, near, Monster.class, 48)) {
            double d = s.dist(mo.getX(), mo.getZ());
            if (d < s.radius() * .6 || mo.getTarget() != null || !mo.getNavigation().isDone() || rnd.nextFloat() < .6f) continue;
            mo.getNavigation().moveTo(mo.getX() - (mo.getX() - s.x) / d * 12, mo.getY(), mo.getZ() - (mo.getZ() - s.z) / d * 12, 1);
        }
    }
}
