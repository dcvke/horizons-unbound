package com.horizonsunbound.fabric.mixin;
import com.horizonsunbound.client.ClientTest;
import com.horizonsunbound.client.InsideStorm;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    @Unique private boolean horizonsUnbound$replacing;
    @Shadow public abstract void play(SoundInstance sound);
    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void horizonsUnbound$insideSound(SoundInstance sound, CallbackInfo ci) {
        if (horizonsUnbound$replacing) return;
        SoundInstance replacement = InsideStorm.onSound(sound);
        ClientTest.onSound(replacement);
        if (replacement == sound) return;
        ci.cancel();
        if (replacement == null) return;
        horizonsUnbound$replacing = true;
        try { play(replacement); }
        finally { horizonsUnbound$replacing = false; }
    }
}
