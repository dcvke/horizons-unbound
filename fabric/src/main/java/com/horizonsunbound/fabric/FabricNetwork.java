package com.horizonsunbound.fabric;

import com.horizonsunbound.platform.Network;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.horizonsunbound.StormPacket;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class FabricNetwork implements Network {
    public void toPlayer(ServerPlayer player, StormPacket payload) { var buffer = PacketByteBufs.create(); payload.write(buffer); ServerPlayNetworking.send(player, payload.id(), buffer); }
    public void toDimension(ServerLevel level, StormPacket payload) {
        for (ServerPlayer player : level.players()) toPlayer(player, payload);
    }
    public void toServer(StormPacket payload) { FabricClient.send(payload); }
}
