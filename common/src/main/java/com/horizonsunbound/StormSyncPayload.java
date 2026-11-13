package com.horizonsunbound;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
public record StormSyncPayload(List<Storm> storms) implements StormPacket {
    public static final ResourceLocation ID = new ResourceLocation(HorizonsUnbound.MODID, "sync");
    public ResourceLocation id() { return ID; }
    public void write(FriendlyByteBuf buf) { buf.writeCollection(storms, Storm::write); }
    public static StormSyncPayload read(FriendlyByteBuf buf) { return new StormSyncPayload(buf.readList(Storm::read)); }
}
