package com.horizonsunbound.platform;

public interface Network {
    void toPlayer(net.minecraft.server.level.ServerPlayer player, com.horizonsunbound.StormPacket payload);
    void toDimension(net.minecraft.server.level.ServerLevel level, com.horizonsunbound.StormPacket payload);
    void toServer(com.horizonsunbound.StormPacket payload);
}
