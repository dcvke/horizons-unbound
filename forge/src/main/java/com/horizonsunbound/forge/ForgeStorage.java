package com.horizonsunbound.forge;

import com.horizonsunbound.HorizonsUnbound;
import com.horizonsunbound.platform.Storage;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HorizonsUnbound.MODID)
public final class ForgeStorage implements Storage {
    public static final class PlacedData { final LongOpenHashSet blocks = new LongOpenHashSet(); }
    private static final Capability<PlacedData> PLACED = CapabilityManager.get(new CapabilityToken<>() {});
    public CompoundTag data(Entity entity) { return entity.getPersistentData(); }
    public LongOpenHashSet placed(LevelChunk chunk, boolean create) {
        return chunk.getCapability(PLACED).map(value -> value.blocks).orElse(null);
    }
    @SubscribeEvent public static void attach(AttachCapabilitiesEvent<LevelChunk> e) {
        Provider provider = new Provider();
        e.addCapability(new ResourceLocation(HorizonsUnbound.MODID, "player_placed"), provider);
        e.addListener(provider.value::invalidate);
    }
    private static final class Provider implements ICapabilitySerializable<LongArrayTag> {
        private final PlacedData data = new PlacedData();
        private final LazyOptional<PlacedData> value = LazyOptional.of(() -> data);
        public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) { return capability == PLACED ? value.cast() : LazyOptional.empty(); }
        public LongArrayTag serializeNBT() { return new LongArrayTag(data.blocks.toLongArray()); }
        public void deserializeNBT(LongArrayTag tag) {
            data.blocks.clear();
            for (long block : tag.getAsLongArray()) data.blocks.add(block);
        }
    }
}
