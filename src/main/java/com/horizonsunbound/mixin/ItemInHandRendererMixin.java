package com.horizonsunbound.mixin;

import com.horizonsunbound.client.Cutscene;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No first-person hand during a storm's cutscene. Here rather than RenderHandEvent: shader packs (Iris) draw the
 * hand from their own passes, and every path ends up in this method.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void horizonsUnbound$noHandInCutscene(CallbackInfo ci) {
        if (Cutscene.active()) ci.cancel();
    }
}
