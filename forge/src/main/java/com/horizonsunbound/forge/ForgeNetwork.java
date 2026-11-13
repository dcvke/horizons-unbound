package com.horizonsunbound.forge;

import com.horizonsunbound.*;
import com.horizonsunbound.platform.Network;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.Optional;

public final class ForgeNetwork implements Network {
    private static final SimpleChannel SYNC = NetworkRegistry.newSimpleChannel(StormSyncPayload.ID, () -> "1", "1"::equals, "1"::equals);
    private static final SimpleChannel CUTSCENE = NetworkRegistry.newSimpleChannel(StormCutscenes.Payload.ID, () -> "1", "1"::equals, "1"::equals);
    static void register() {
        SYNC.registerMessage(0, StormSyncPayload.class, StormSyncPayload::write, StormSyncPayload::read, (packet, context) -> {
            var ctx = context.get();
            ctx.enqueueWork(() -> ForgeClient.sync(packet));
            ctx.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CUTSCENE.registerMessage(0, StormCutscenes.Payload.class, StormCutscenes.Payload::write, StormCutscenes.Payload::read, (packet, context) -> {
            var ctx = context.get();
            ctx.enqueueWork(() -> {
                if (ctx.getDirection().getReceptionSide().isClient()) ForgeClient.cutscene(packet);
                else if (ctx.getSender() != null) StormCutscenes.stopped(ctx.getSender());
            });
            ctx.setPacketHandled(true);
        });
    }
    private static SimpleChannel channel(StormPacket packet) { return packet instanceof StormSyncPayload ? SYNC : CUTSCENE; }
    public void toPlayer(ServerPlayer player, StormPacket packet) { channel(packet).send(PacketDistributor.PLAYER.with(() -> player), packet); }
    public void toDimension(ServerLevel level, StormPacket packet) { channel(packet).send(PacketDistributor.DIMENSION.with(level::dimension), packet); }
    public void toServer(StormPacket packet) { channel(packet).sendToServer(packet); }
}
