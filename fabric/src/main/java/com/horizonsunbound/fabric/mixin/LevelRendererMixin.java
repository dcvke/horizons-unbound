package com.horizonsunbound.fabric.mixin;

import com.horizonsunbound.fabric.FabricClient;
import net.minecraft.client.Camera;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleEngine;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;F)V", shift = At.Shift.AFTER))
    private void horizonsUnbound$particles(PoseStack pose, float partial, long finishNanoTime, boolean outline, Camera camera, GameRenderer renderer, LightTexture light, Matrix4f projection, CallbackInfo ci) {
        FabricClient.render(true, camera, partial, pose);
    }
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V", shift = At.Shift.AFTER))
    private void horizonsUnbound$weather(PoseStack pose, float partial, long finishNanoTime, boolean outline, Camera camera, GameRenderer renderer, LightTexture light, Matrix4f projection, CallbackInfo ci) {
        FabricClient.render(false, camera, partial, pose);
    }
}
