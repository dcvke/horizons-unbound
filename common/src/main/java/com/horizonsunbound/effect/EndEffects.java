package com.horizonsunbound.effect;

import com.horizonsunbound.HorizonsUnbound;
import com.horizonsunbound.Storm;
import com.horizonsunbound.StormManager;
import com.horizonsunbound.StormType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Under an End storm space misbehaves: players leave echoes, projectiles stall in mid-air, paired seams hang in the
 * air and swap whatever steps in, the way near the column is sometimes shorter, and gravity waves roll out of the
 * column. Reality anchors keep a sphere of normal space around them.
 */

public final class EndEffects {
    private static final int KEEP = EchoEntity.DELAY + EchoEntity.REPLAY + 20; // ticks of path kept per player
    private static final int SEAM_EPOCH = 1800, SEAM_PAIRS = 3;                // seams move every 90 s
    private static final Map<UUID, ArrayDeque<double[]>> PATHS = new HashMap<>(); // {time, x, y, z, yRot, xRot, onGround}
    private static final Map<UUID, Long> LAST_ECHO = new HashMap<>();
    private static final Set<UUID> SPRINTING = new HashSet<>();

    private EndEffects() {}

    static void tick(ServerLevel level, StormManager m, Storm s, List<ServerPlayer> near) {
        if (s.age % SEAM_EPOCH == 1 || s.marks.isEmpty() && s.age % 100 == 0) placeSeams(level, m, s);
        if (near.isEmpty()) return;
        if (s.age % 2 == 0) seams(level, m, s);
        if (s.age % 2 == 0) gravityWave(level, m, s, near);
        if (s.age % 10 == 0) for (ServerPlayer p : near) shortcut(level, m, s, p);
    }

    /** The player's recorded position at a game time, or null when it isn't kept (any more). */
    public static double[] pathAt(UUID id, long time) {
        ArrayDeque<double[]> path = PATHS.get(id);
        if (path == null) return null;
        for (double[] p : path) if ((long) p[0] == time) return p;
        return null;
    }

    // ---- seams -------------------------------------------------------------------------------------------------

    /** New pairs of seams beside the column: each pair a short walk apart, standing on the ground. */
    private static void placeSeams(ServerLevel level, StormManager m, Storm s) {
        s.marks.removeIf(k -> k.kind() == Storm.Mark.SEAM);
        RandomSource rnd = level.random;
        for (int i = 0; i < SEAM_PAIRS; i++) {
            float ang = rnd.nextFloat() * Mth.TWO_PI, d = s.radius() * (1.05f + rnd.nextFloat() * .9f);
            BlockPos a = StormEffects.ground(level, Mth.floor(s.x + Mth.cos(ang) * d), Mth.floor(s.z + Mth.sin(ang) * d));
            if (a == null) continue;
            float ang2 = rnd.nextFloat() * Mth.TWO_PI, d2 = 10 + rnd.nextFloat() * 18;
            BlockPos b = StormEffects.ground(level, Mth.floor(a.getX() + Mth.cos(ang2) * d2), Mth.floor(a.getZ() + Mth.sin(ang2) * d2));
            if (b == null) continue;
            s.marks.add(new Storm.Mark(Storm.Mark.SEAM, a.getX(), a.getY() + 1, a.getZ(), i));
            s.marks.add(new Storm.Mark(Storm.Mark.SEAM, b.getX(), b.getY() + 1, b.getZ(), i));
        }
        m.resync();
    }

    /** Whatever walks into a seam comes out of its partner, a step past it. */
    private static void seams(ServerLevel level, StormManager m, Storm s) {
        long now = level.getGameTime();
        for (Storm.Mark a : s.marks) {
            if (a.kind() != Storm.Mark.SEAM) continue;
            Storm.Mark b = partner(s, a);
            if (b == null) continue;
            AABB box = new AABB(a.x() + .1, a.y(), a.z() + .1, a.x() + .9, a.y() + 2.6, a.z() + .9);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
                CompoundTag d = com.horizonsunbound.platform.Services.STORAGE.data(e);
                // riders and their mounts stay put: teleporting one half of a pair breaks it
                if (d.getLong("ds_seam") > now || e.isPassenger() || e.isVehicle() || m.anchored(e.getX(), e.getY(), e.getZ())) continue;
                d.putLong("ds_seam", now + 40);
                Vec3 look = Vec3.directionFromRotation(0, e.getYRot()).scale(1.2);
                Vec3 to = new Vec3(b.x() + .5, b.y(), b.z() + .5);
                if (level.noCollision(e, e.getBoundingBox().move(to.add(look).subtract(e.position())))) to = to.add(look);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, e.getX(), e.getY() + 1, e.getZ(), 30, .3, .8, .3, .05);
                e.teleportTo(to.x, to.y, to.z);
                e.resetFallDistance();
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, to.x, to.y + 1, to.z, 30, .3, .8, .3, .05);
                level.playSound(null, to.x, to.y, to.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.AMBIENT, .7f, 1.4f);
            }
        }
    }

    private static Storm.Mark partner(Storm s, Storm.Mark a) {
        for (Storm.Mark b : s.marks) if (b != a && b.kind() == Storm.Mark.SEAM && b.a() == a.a()) return b;
        return null;
    }

    // ---- gravity waves and shortcuts -----------------------------------------------------------------------------

    /** A ring rolls out of the column and lifts everything it passes for a moment (slow falling brings it down). */
    private static void gravityWave(ServerLevel level, StormManager m, Storm s, List<ServerPlayer> near) {
        if (s.age % s.wavePeriod() == 0)
            for (ServerPlayer p : near) level.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.AMBIENT, 1, .5f);
        float f = s.waveFront(s.age), prev = Math.max(0, s.waveFront(s.age - 2));
        if (f < 0) return;
        for (LivingEntity e : StormEffects.around(level, near, LivingEntity.class, 48)) {
            double d = s.dist(e.getX(), e.getZ());
            if (d <= prev || d > f || e.isSpectator() || s.inside(e.getX(), e.getY(), e.getZ()) || m.anchored(e.getX(), e.getY(), e.getZ())) continue;
            e.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 30, 1));
            e.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 140, 0));
            e.push((e.getX() - s.x) / d * .4, 0, (e.getZ() - s.z) / d * .4);
            e.hurtMarked = true;
            level.playSound(null, e.blockPosition(), SoundEvents.SHULKER_SHOOT, SoundSource.AMBIENT, .8f, .6f);
        }
    }

    /** Near the column a walking player sometimes skips a few blocks ahead, always onto free, solid ground. */
    private static void shortcut(ServerLevel level, StormManager m, Storm s, ServerPlayer p) {
        double d = s.dist(p.getX(), p.getZ());
        if (d < s.radius() * .8 || d > s.radius() * 1.8 || !p.onGround() || p.isPassenger() || level.random.nextFloat() > .06f) return;
        // players tick after the level, so the newest entry is from the previous tick
        long now = level.getGameTime();
        double[] a = pathAt(p.getUUID(), now - 6), b = pathAt(p.getUUID(), now - 1);
        if (a == null || b == null || m.anchored(p.getX(), p.getY(), p.getZ())) return;
        double dx = b[1] - a[1], dz = b[3] - a[3], speed = Math.sqrt(dx * dx + dz * dz);
        if (speed < .6) return; // standing or sneaking
        double jump = 3 + level.random.nextDouble() * 3, mx = dx / speed * jump, mz = dz / speed * jump;
        for (int dy : new int[] {0, 1, -1, 2, -2}) {
            AABB box = p.getBoundingBox().move(mx, dy, mz);
            if (!level.noCollision(p, box) || level.noCollision(p, box.move(0, -.25, 0))) continue;
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 20, .3, .7, .3, .03);
            p.teleportTo(p.getX() + mx, p.getY() + dy, p.getZ() + mz);
            p.resetFallDistance();
            level.playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, .4f, 1.6f);
            return;
        }
    }

    // ---- echoes: paths, jumps, sprints ---------------------------------------------------------------------------

    public static void onPlayerTick(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        boolean endStorm = false;
        for (Storm s : StormManager.get(level).storms()) endStorm |= s.type == StormType.END;
        if (!endStorm) {
            if (PATHS.remove(p.getUUID()) != null) SPRINTING.remove(p.getUUID());
            return;
        }
        ArrayDeque<double[]> path = PATHS.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
        double[] last = path.peekLast();
        // a jump: on the ground last tick, off it and rising now (the server sees no jump event for players)
        boolean ground = p.onGround();
        double rise = last == null ? 0 : p.getY() - last[2];
        if (last != null && last[6] > 0 && !ground && rise > .05 && rise < 1) echo(level, p); // a teleport up is no jump
        path.addLast(new double[] {level.getGameTime(), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot(), ground ? 1 : 0});
        while (path.size() > KEEP) path.removeFirst();
        boolean sprint = p.isSprinting();
        if (sprint && SPRINTING.add(p.getUUID())) echo(level, p); // a dash
        else if (!sprint) SPRINTING.remove(p.getUUID());
    }

    private static void echo(ServerLevel level, ServerPlayer p) {
        if (p.isSpectator() || StormEffects.stormAt(level, p.getX(), p.getZ(), StormType.END) == null || StormEffects.anchored(level, p)) return;
        long now = level.getGameTime();
        Long last = LAST_ECHO.get(p.getUUID());
        if (last != null && now - last < EchoEntity.DELAY + EchoEntity.REPLAY) return; // one echo at a time
        LAST_ECHO.put(p.getUUID(), now);
        level.addFreshEntity(EchoEntity.of(p));
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 15, .3, .7, .3, .01);
    }

    public static void onLogout(net.minecraft.world.entity.player.Player player) {
        UUID id = player.getUUID();
        PATHS.remove(id);
        LAST_ECHO.remove(id);
        SPRINTING.remove(id);
    }

    // ---- stalling projectiles ------------------------------------------------------------------------------------

    /** Some projectiles fired under the storm stop dead for a second or so, then fly on exactly as before. */

    public static void onEntityTick(Entity e) {
        if (!(e instanceof Projectile) || !(e.level() instanceof ServerLevel level)) return;
        CompoundTag d = com.horizonsunbound.platform.Services.STORAGE.data(e);
        int frozen = d.getInt("ds_freeze");
        if (frozen > 0) {
            if (--frozen == 0) {
                e.setDeltaMovement(d.getDouble("ds_vx"), d.getDouble("ds_vy"), d.getDouble("ds_vz"));
                e.setNoGravity(d.getBoolean("ds_ng"));
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, e.getX(), e.getY(), e.getZ(), 8, .1, .1, .1, .05);
            } else e.setDeltaMovement(Vec3.ZERO);
            e.hasImpulse = true;
            d.putInt("ds_freeze", frozen);
            return;
        }
        int at = d.getInt("ds_at");
        if (at > 0 && e.tickCount == at && e.getDeltaMovement().lengthSqr() > .04) {
            Vec3 v = e.getDeltaMovement();
            d.putDouble("ds_vx", v.x);
            d.putDouble("ds_vy", v.y);
            d.putDouble("ds_vz", v.z);
            d.putBoolean("ds_ng", e.isNoGravity());
            d.putInt("ds_freeze", 10 + level.random.nextInt(20));
            e.setNoGravity(true);
            e.setDeltaMovement(Vec3.ZERO);
            e.hasImpulse = true;
            level.playSound(null, e.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1, .5f);
            return;
        }
        if (e.tickCount != 1) return; // decided once per projectile
        if (StormEffects.stormAt(level, e.getX(), e.getZ(), StormType.END) == null || StormEffects.anchored(level, e)) return;
        if (level.random.nextFloat() < .4f) d.putInt("ds_at", 3 + level.random.nextInt(8));
    }
}
