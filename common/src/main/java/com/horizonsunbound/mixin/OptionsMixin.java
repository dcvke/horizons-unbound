package com.horizonsunbound.mixin;

import com.horizonsunbound.client.Cutscene;
import net.minecraft.client.CameraType;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** While a cutscene films the player from outside, the camera counts as third person for everyone (Cutscene.thirdPerson()). */
@Mixin(Options.class)
public abstract class OptionsMixin {
    @Inject(method = "getCameraType", at = @At("HEAD"), cancellable = true)
    private void horizonsUnbound$cutsceneCamera(CallbackInfoReturnable<CameraType> cir) {
        if (Cutscene.thirdPerson()) cir.setReturnValue(CameraType.THIRD_PERSON_BACK);
    }
}
