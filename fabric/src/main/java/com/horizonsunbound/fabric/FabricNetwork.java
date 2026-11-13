package com.horizonsunbound.fabric;

import com.horizonsunbound.platform.Network;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class FabricNetwork implements Network {
    public void toPlayer(ServerPlayer player, CustomPacketPayload payload) { ServerPlayNetworking.send(player, payload); }
    public void toDimension(ServerLevel level, CustomPacketPayload payload) {
        for (ServerPlayer player : level.players()) toPlayer(player, payload);
    }
    public void toServer(CustomPacketPayload payload) { FabricClient.send(payload); }
}
