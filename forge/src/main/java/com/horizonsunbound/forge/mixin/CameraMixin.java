package com.horizonsunbound.forge.mixin;

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

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow private boolean detached;
    @Shadow protected abstract void setPosition(Vec3 pos);
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Inject(method = "setup", at = @At("TAIL"))
    private void horizonsUnbound$camera(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partial, CallbackInfo ci) {
        Cutscene.View view = Cutscene.view(partial);
        if (view == null) return;
        setRotation(view.yaw(), view.pitch());
        setPosition(view.pos());
        this.detached = view.detached();
    }
}
