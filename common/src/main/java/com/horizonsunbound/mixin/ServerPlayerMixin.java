package com.horizonsunbound.mixin;

import com.horizonsunbound.platform.ViewDistances;
import net.minecraft.network.protocol.game.ServerboundClientInformationPacket;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Inject(method = "updateOptions", at = @At("HEAD"))
    private void horizonsUnbound$viewDistance(ServerboundClientInformationPacket packet, CallbackInfo ci) {
        ViewDistances.set((ServerPlayer) (Object) this, packet.viewDistance());
    }
}
