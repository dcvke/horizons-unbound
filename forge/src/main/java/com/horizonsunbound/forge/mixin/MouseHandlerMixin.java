package com.horizonsunbound.forge.mixin;
import com.horizonsunbound.client.Cutscene;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void horizonsUnbound$noTurn(CallbackInfo ci) { if (Cutscene.active()) ci.cancel(); }
}
