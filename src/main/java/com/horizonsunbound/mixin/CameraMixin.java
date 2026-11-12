package com.horizonsunbound.mixin;

import com.horizonsunbound.client.Cutscene;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** During a storm's cutscene the camera goes where the cutscene puts it (the world, sound and culling follow it). */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow private boolean detached;

    @Shadow protected abstract void setPosition(Vec3 pos);

    @Shadow protected abstract void setRotation(float yRot, float xRot, float roll);

    @Inject(method = "setup", at = @At("TAIL"))
    private void horizonsUnbound$cutscene(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
        Cutscene.View v = Cutscene.view(partialTick);
        if (v == null) return;
        setRotation(v.yaw(), v.pitch(), v.roll());
        setPosition(v.pos());
        this.detached = v.detached(); // so the player is drawn when the camera is away from their eyes
    }
}
