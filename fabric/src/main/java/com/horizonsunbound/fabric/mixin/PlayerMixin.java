package com.horizonsunbound.fabric.mixin;

import com.horizonsunbound.StormCutscenes;
import com.horizonsunbound.StormSpawner;
import com.horizonsunbound.effect.EndEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void horizonsUnbound$serverTick(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) {
            StormCutscenes.onPlayerTick(player);
            StormSpawner.onPlayerTick(player);
            EndEffects.onPlayerTick(player);
        }
    }
}
