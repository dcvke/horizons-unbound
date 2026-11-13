package com.horizonsunbound.fabric.mixin;

import com.horizonsunbound.client.Cutscene;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @ModifyReturnValue(method = "getFov", at = @At("RETURN"))
    private double horizonsUnbound$fov(double fov, Camera camera, float partial, boolean useSetting) { return Cutscene.onFov(fov, partial); }
    @ModifyExpressionValue(method = "renderLevel", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;rotation(Lorg/joml/Quaternionfc;)Lorg/joml/Matrix4f;"))
    private Matrix4f horizonsUnbound$roll(Matrix4f matrix, DeltaTracker delta) {
        Cutscene.View view = Cutscene.view(delta.getGameTimeDeltaPartialTick(true));
        // The inverse of NeoForge's Y-X-Z camera quaternion has the roll before pitch and yaw.
        return view == null ? matrix : new Matrix4f().rotationZ(view.roll() * Mth.DEG_TO_RAD).mul(matrix);
    }
}
