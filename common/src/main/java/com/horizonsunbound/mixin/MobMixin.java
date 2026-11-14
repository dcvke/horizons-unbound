package com.horizonsunbound.mixin;

import com.horizonsunbound.effect.StormMobs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A storm's creatures don't despawn while it is near, and go once it isn't (StormMobs.stays). */
@Mixin(Mob.class)
public abstract class MobMixin {
    @Inject(method = "checkDespawn", at = @At("HEAD"), cancellable = true)
    private void horizonsUnbound$storm(CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        if (self.level() instanceof ServerLevel level && StormMobs.stays(level, self)) ci.cancel();
    }
}
