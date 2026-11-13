package com.horizonsunbound.neoforge;
import com.horizonsunbound.platform.Network;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
public final class NeoNetwork implements Network {
    public void toPlayer(ServerPlayer player, CustomPacketPayload payload) { PacketDistributor.sendToPlayer(player, payload); }
    public void toDimension(ServerLevel level, CustomPacketPayload payload) { PacketDistributor.sendToPlayersInDimension(level, payload); }
    public void toServer(CustomPacketPayload payload) { PacketDistributor.sendToServer(payload); }
}
