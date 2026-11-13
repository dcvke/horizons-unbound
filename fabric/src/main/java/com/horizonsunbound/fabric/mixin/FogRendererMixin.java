package com.horizonsunbound.fabric.mixin;

import com.horizonsunbound.client.ClientEffects;
import com.horizonsunbound.client.StormAtmosphere;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @Shadow private static float fogRed;
    @Shadow private static float fogGreen;
    @Shadow private static float fogBlue;
    @Inject(method = "setupColor", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clearColor(FFFF)V", ordinal = 1))
    private static void horizonsUnbound$tint(CallbackInfo ci) {
        Vec3 color = StormAtmosphere.tint(new Vec3(fogRed, fogGreen, fogBlue), .8f);
        fogRed = (float) color.x; fogGreen = (float) color.y; fogBlue = (float) color.z;
    }
    @Inject(method = "setupFog", at = @At("TAIL"))
    private static void horizonsUnbound$distance(Camera camera, FogRenderer.FogMode mode, float distance, boolean thick, float partial, CallbackInfo ci) {
        if (mode != FogRenderer.FogMode.FOG_TERRAIN) return;
        float far = ClientEffects.fogDistance(RenderSystem.getShaderFogEnd());
        if (far >= RenderSystem.getShaderFogEnd() - .5f) return;
        RenderSystem.setShaderFogEnd(far);
        RenderSystem.setShaderFogStart(Math.min(RenderSystem.getShaderFogStart(), far * .15f));
    }
}
