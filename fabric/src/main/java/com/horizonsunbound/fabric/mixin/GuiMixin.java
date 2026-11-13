package com.horizonsunbound.fabric.mixin;
import com.horizonsunbound.client.Cutscene;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void horizonsUnbound$dialogue(GuiGraphics graphics, float partial, CallbackInfo ci) {
        if (Cutscene.onGui(graphics, partial)) ci.cancel();
    }
}
