package com.horizonsunbound.platform;

public interface Network {
    void toPlayer(net.minecraft.server.level.ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload);
    void toDimension(net.minecraft.server.level.ServerLevel level, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload);
    void toServer(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload);
}
