package com.horizonsunbound.mixin;

import com.horizonsunbound.client.StormAtmosphere;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NeoForge has no event for sky and cloud color, so tint them on the way out. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void horizonsUnbound$tintSky(Vec3 pos, float partialTick, CallbackInfoReturnable<Vec3> cir) {
        cir.setReturnValue(StormAtmosphere.tint(cir.getReturnValue(), .8f));
    }

    @Inject(method = "getCloudColor", at = @At("RETURN"), cancellable = true)
    private void horizonsUnbound$tintClouds(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        cir.setReturnValue(StormAtmosphere.tint(cir.getReturnValue(), .8f)); // close to the sky so vanilla cloud blocks don't clutter the storm
    }
}
