package com.horizonsunbound.mixin;

import com.horizonsunbound.compat.XaeroMarks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Xaero's Minimap closes its list of map highlighters once its session has filled it: storms join just before
 * (unless Xaero's World Map draws them; the minimap then shows those). Skipped without Xaero; if Xaero changes this
 * code the storms just don't show on its map (require = 0).
 */
@Pseudo
@Mixin(targets = "xaero.common.minimap.highlight.HighlighterRegistry", remap = false)
public abstract class XaeroMinimapHighlightsMixin {
    @Inject(method = "end", at = @At("HEAD"), require = 0)
    private void horizonsUnbound$storms(CallbackInfo ci) {
        if (!XaeroMarks.worldMapPresent()) ((xaero.common.minimap.highlight.HighlighterRegistry) (Object) this).register(new XaeroMarks.Minimap());
    }
}
