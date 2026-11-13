package com.horizonsunbound.mixin;

import com.horizonsunbound.client.Cutscene;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No toasts (advancements, tutorial hints) over a storm's cutscene; they wait and show afterwards. */
@Mixin(ToastComponent.class)
public abstract class ToastComponentMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void horizonsUnbound$noToastsInCutscene(CallbackInfo ci) {
        if (Cutscene.active()) ci.cancel();
    }
}
