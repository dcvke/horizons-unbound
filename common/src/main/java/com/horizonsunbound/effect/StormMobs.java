package com.horizonsunbound.effect;

import com.horizonsunbound.Storm;
import com.horizonsunbound.StormManager;
import com.horizonsunbound.StormType;
import com.horizonsunbound.StormsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Creatures of a storm's dimension (StormsConfig "mobs"): now and then one appears on open ground around a risen
 * storm, at least 24 blocks from any player, never in Peaceful or near the world
 * spawn and beds. They are tagged; when the storm comes apart they go with it (leaveWithStorm).
 */
public final class StormMobs {
    public static final String TAG = "horizons_unbound_storm";

    private StormMobs() {}

    static void tick(ServerLevel level, Storm s) {
        if (!StormsConfig.MOBS.get() || s.dying() > 0 || level.getDifficulty() == Difficulty.PEACEFUL) return;
        if (s.age % (StormsConfig.MOBS_EVERY.get() * 20) != 0) return;
        if (StormEffects.playersIn(level, s, s.zone()).isEmpty()) return; // nobody around to meet them
        if (mobs(level, s).size() >= StormsConfig.MAX_MOBS.get()) return;
        List<EntityType<?>> types = new ArrayList<>();
        for (StormType k : StormType.values())
            if (s.has(k)) for (String id : StormsConfig.mobs(k)) {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl != null) BuiltInRegistries.ENTITY_TYPE.getOptional(rl).ifPresent(types::add);
            }
        if (types.isEmpty()) return;
        EntityType<?> type = types.get(level.random.nextInt(types.size()));
        for (int attempt = 0; attempt < 6; attempt++) {
            float ang = level.random.nextFloat() * Mth.TWO_PI, d = s.radius() * (1.05f + .4f * level.random.nextFloat());
            BlockPos ground = StormEffects.ground(level, Mth.floor(s.x + Mth.cos(ang) * d), Mth.floor(s.z + Mth.sin(ang) * d));
            if (ground == null) continue;
            BlockPos pos = ground.above();
            if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir() || !level.getFluidState(ground).isEmpty()) continue;
            if (level.getNearestPlayer(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 24, false) != null) continue;
            if (StormEffects.nearHome(level, pos, StormsConfig.PROTECT_RADIUS.get())) continue;
            Entity e = type.spawn(level, pos, MobSpawnType.EVENT);
            if (e == null) continue;
            e.addTag(TAG); // they stay as long as the storm, then go with it (stays())
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, e.getX(), e.getY() + 1, e.getZ(), 30, .4, .8, .4, .05);
            return;
        }
    }

    /** The storm comes apart: its creatures go with it. */
    public static void leave(ServerLevel level, Storm s) {
        if (!StormsConfig.MOBS_LEAVE.get()) return;
        for (Mob m : mobs(level, s)) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, m.getX(), m.getY() + 1, m.getZ(), 30, .4, .8, .4, .05);
            m.discard();
        }
    }

    /**
     * Mob.checkDespawn (MobMixin), each tick: true keeps a storm's creature from the usual despawning while it is near
     * a storm; away from every storm (its own came apart while it was off somewhere, or in an unloaded chunk) it goes.
     */
    public static boolean stays(ServerLevel level, Mob m) {
        if (!m.getTags().contains(TAG) || !StormsConfig.MOBS_LEAVE.get() || level.getDifficulty() == Difficulty.PEACEFUL) return false;
        for (Storm s : StormManager.get(level).storms()) {
            double dx = m.getX() - s.x, dz = m.getZ() - s.z, r = s.zone() + 64;
            if (dx * dx + dz * dz < r * r) return true;
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, m.getX(), m.getY() + 1, m.getZ(), 30, .4, .8, .4, .05);
        m.discard();
        return true;
    }

    private static List<Mob> mobs(ServerLevel level, Storm s) {
        float r = s.zone();
        return level.getEntitiesOfClass(Mob.class, new AABB(s.x - r, level.getMinBuildHeight(), s.z - r, s.x + r, level.getMaxBuildHeight(), s.z + r),
                m -> m.getTags().contains(TAG));
    }
}
