package com.horizonsunbound.fabric;

import com.horizonsunbound.platform.Storage;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;
import java.util.Arrays;

public final class FabricStorage implements Storage {
    private static final Codec<LongOpenHashSet> CODEC = Codec.LONG_STREAM.xmap(s -> new LongOpenHashSet(s.toArray()), set -> Arrays.stream(set.toLongArray()));
    private static final AttachmentType<LongOpenHashSet> PLACED = AttachmentRegistry.create(FabricMod.id("player_placed"), b -> b.initializer(LongOpenHashSet::new).persistent(CODEC));
    private static final AttachmentType<CompoundTag> DATA = AttachmentRegistry.create(FabricMod.id("persistent_data"), b -> b.initializer(CompoundTag::new).persistent(CompoundTag.CODEC).copyOnDeath());
    static void initialize() {}
    static void resetAfterClone(Entity entity) {
        // Match (Neo)Forge: only the three remembered storm flags are copied by the common clone hooks.
        ((AttachmentTarget) entity).setAttached(DATA, new CompoundTag());
    }
    public CompoundTag data(Entity entity) { return ((AttachmentTarget) entity).getAttachedOrCreate(DATA); }
    public LongOpenHashSet placed(LevelChunk chunk, boolean create) {
        AttachmentTarget target = (AttachmentTarget) chunk;
        return create ? target.getAttachedOrCreate(PLACED) : target.getAttached(PLACED);
    }
}
