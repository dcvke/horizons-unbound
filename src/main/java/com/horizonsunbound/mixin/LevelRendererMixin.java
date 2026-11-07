package com.horizonsunbound.mixin;

import com.horizonsunbound.client.StormAtmosphere;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Clouds write depth, so they punch holes in the storm drawn after them. Under a storm's sky the cloud pass is
 * skipped and the storm's own cloud cap takes its place. The call itself is skipped (not cancelled inside
 * renderClouds), because cloud mods such as Cirrus draw their clouds from the start of renderClouds too.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @WrapWithCondition(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderClouds(Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FDDD)V"))
    private boolean horizonsUnbound$skipCloudsUnderStorm(LevelRenderer self, PoseStack pose, Matrix4f frustum, Matrix4f projection,
                                                          float partialTick, double camX, double camY, double camZ) {
        return !StormAtmosphere.active();
    }
}
