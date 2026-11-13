package com.horizonsunbound.fabric.mixin;
import com.horizonsunbound.client.Cutscene;
import com.horizonsunbound.client.InsideStorm;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void horizonsUnbound$attack(CallbackInfoReturnable<Boolean> cir) {
        if (Cutscene.active()) cir.setReturnValue(false);
        else InsideStorm.onAttack();
    }
    @Inject(method = {"continueAttack", "startUseItem", "pickBlock"}, at = @At("HEAD"), cancellable = true)
    private void horizonsUnbound$noInteraction(CallbackInfo ci) { if (Cutscene.active()) ci.cancel(); }
}
