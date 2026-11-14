package com.horizonsunbound.mixin;

import com.horizonsunbound.compat.XaeroMarks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Xaero's World Map closes its list of map highlighters once its session has filled it: storms join just before (see XaeroMinimapHighlightsMixin). */
@Pseudo
@Mixin(targets = "xaero.map.highlight.HighlighterRegistry", remap = false)
public abstract class XaeroWorldMapHighlightsMixin {
    @Inject(method = "end", at = @At("HEAD"), require = 0)
    private void horizonsUnbound$storms(CallbackInfo ci) {
        ((xaero.map.highlight.HighlighterRegistry) (Object) this).register(new XaeroMarks.WorldMap());
    }
}
