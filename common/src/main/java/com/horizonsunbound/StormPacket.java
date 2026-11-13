package com.horizonsunbound;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
public interface StormPacket {
    ResourceLocation id();
    void write(FriendlyByteBuf buf);
}
