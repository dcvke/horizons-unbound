package com.horizonsunbound.mixin;

import com.horizonsunbound.Storm;
import com.horizonsunbound.effect.OverworldEffects;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Rain pockets under Overworld storms, no rain round Nether storms: in a dry spot vanilla treats it as not rained on
 * (fire, farmland, cauldrons, lightning, blazes), and on the client the rain fades where the camera stands in one.
 */
@Mixin(Level.class)
public abstract class LevelMixin {
    @Inject(method = "isRainingAt", at = @At("HEAD"), cancellable = true)
    private void horizonsUnbound$dryPocket(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        Level level = (Level) (Object) this;
        // vanilla answers false anyway when it isn't raining: don't look for storms then (this is called a lot)
        if (level.isRaining() && OverworldEffects.dryAt(level, pos)) cir.setReturnValue(false);
    }

    @ModifyReturnValue(method = "getRainLevel", at = @At("RETURN"))
    private float horizonsUnbound$localRain(float rain) {
        return ((Level) (Object) this).isClientSide ? rain * Storm.clientRainFactor : rain;
    }
}
