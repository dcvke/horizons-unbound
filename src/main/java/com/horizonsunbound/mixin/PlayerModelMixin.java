package com.horizonsunbound.mixin;

import com.horizonsunbound.client.Cutscene;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The player acts in a storm's cutscene: their arms are posed after the model's own animation. */
@Mixin(value = PlayerModel.class, priority = 1500) // after animation mods' own touches to the model (Not Enough Animations and the like)
public abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void horizonsUnbound$act(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float headYaw, float headPitch, CallbackInfo ci) {
        Cutscene.pose((PlayerModel<?>) (Object) this, entity);
    }
}
