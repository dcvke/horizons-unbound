package com.horizonsunbound.platform;

public interface Storage {
    net.minecraft.nbt.CompoundTag data(net.minecraft.world.entity.Entity entity);
    it.unimi.dsi.fastutil.longs.LongOpenHashSet placed(net.minecraft.world.level.chunk.LevelChunk chunk, boolean create);
}
