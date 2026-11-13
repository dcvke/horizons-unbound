package com.horizonsunbound.fabric.mixin;

import com.horizonsunbound.client.InsideStorm;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class ClientPlayerTickMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void horizonsUnbound$air(CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer player) InsideStorm.onPlayerTick(player);
    }
}
