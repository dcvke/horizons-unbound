package com.horizonsunbound.effect;

import com.horizonsunbound.ModContent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * A see-through copy of a player left behind under an End storm. It stands still for {@link #DELAY} ticks, then
 * walks the player's own path from that moment; monsters near it may lose interest in the player and go for it.
 */
public class EchoEntity extends Mob {
    public static final int DELAY = 40, REPLAY = 70;
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(EchoEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private long start;

    public EchoEntity(EntityType<? extends EchoEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public static EchoEntity of(Player owner) {
        EchoEntity e = new EchoEntity(ModContent.ECHO.get(), owner.level());
        e.entityData.set(OWNER, Optional.of(owner.getUUID()));
        e.start = owner.level().getGameTime();
        e.moveTo(owner.getX(), owner.getY(), owner.getZ(), owner.getYRot(), owner.getXRot());
        e.setYHeadRot(owner.getYHeadRot());
        e.yBodyRot = owner.yBodyRot;
        return e;
    }

    public UUID owner() { return entityData.get(OWNER).orElse(null); }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER, Optional.empty());
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        UUID id = owner();
        double[] p = id == null ? null : EndEffects.pathAt(id, now - DELAY);
        if (p == null || now - start > DELAY + REPLAY) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1, getZ(), 20, .3, .6, .3, .02);
            discard();
            return;
        }
        if (now - start >= DELAY) {
            setPos(p[1], p[2], p[3]); // p = {time, x, y, z, yRot, xRot}
            setYRot((float) p[4]);
            setXRot((float) p[5]);
            setYHeadRot((float) p[4]);
            yBodyRot = (float) p[4];
        }
        if (tickCount % 10 == 0) { // pull monsters off the player onto the copy
            Player player = level.getPlayerByUUID(id);
            for (Monster m : level.getEntitiesOfClass(Monster.class, getBoundingBox().inflate(16)))
                if (m.getTarget() == player && random.nextFloat() < .4f) m.setTarget(this);
        }
    }

    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean removeWhenFarAway(double dist) { return false; }
    @Override protected void doPush(Entity e) {}
}
