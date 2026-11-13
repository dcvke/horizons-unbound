package com.horizonsunbound.fabric.mixin;

import com.horizonsunbound.client.Cutscene;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Camera;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @ModifyReturnValue(method = "getFov", at = @At("RETURN"))
    private double horizonsUnbound$fov(double fov, Camera camera, float partial, boolean useSetting) { return Cutscene.onFov(fov, partial); }
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V", shift = At.Shift.AFTER))
    private void horizonsUnbound$roll(float partial, long finishNanoTime, PoseStack pose, CallbackInfo ci) {
        Cutscene.View view = Cutscene.view(partial);
        if (view != null) pose.mulPose(Axis.ZP.rotationDegrees(view.roll()));
    }
}
