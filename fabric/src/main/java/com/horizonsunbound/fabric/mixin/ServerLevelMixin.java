package com.horizonsunbound.fabric.mixin;

import com.horizonsunbound.effect.EndEffects;
import com.horizonsunbound.effect.SculkEffects;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Inject(method = "tickNonPassenger", at = @At("HEAD"))
    private void horizonsUnbound$projectile(Entity entity, CallbackInfo ci) { EndEffects.onEntityTick(entity); }
    @Inject(method = "tickPassenger", at = @At("HEAD"))
    private void horizonsUnbound$passenger(Entity vehicle, Entity passenger, CallbackInfo ci) { EndEffects.onEntityTick(passenger); }
    @Inject(method = "gameEvent", at = @At("HEAD"))
    private void horizonsUnbound$vibration(GameEvent event, Vec3 pos, GameEvent.Context context, CallbackInfo ci) {
        SculkEffects.onGameEvent((ServerLevel) (Object) this, context.sourceEntity(), event);
    }
}
