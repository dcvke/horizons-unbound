package com.horizonsunbound;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Server to client: every storm of the player's current dimension (the full list each time, there are only a few). */
public record StormSyncPayload(List<Storm> storms) implements CustomPacketPayload {
    public static final Type<StormSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HorizonsUnbound.MODID, "sync"));
    public static final StreamCodec<FriendlyByteBuf, StormSyncPayload> CODEC =
            Storm.STREAM_CODEC.apply(ByteBufCodecs.list()).map(StormSyncPayload::new, StormSyncPayload::storms);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
