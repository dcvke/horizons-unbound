package com.horizonsunbound.neoforge;
import com.horizonsunbound.HorizonsUnbound;
import com.horizonsunbound.platform.Storage;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import java.util.Arrays;
import java.util.function.Supplier;
public final class NeoStorage implements Storage {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, HorizonsUnbound.MODID);
    private static final Codec<LongOpenHashSet> CODEC = Codec.LONG_STREAM.xmap(s -> new LongOpenHashSet(s.toArray()), set -> Arrays.stream(set.toLongArray()));
    private static final Supplier<AttachmentType<LongOpenHashSet>> PLACED = ATTACHMENTS.register("player_placed",
            () -> AttachmentType.builder(() -> new LongOpenHashSet()).serialize(CODEC, set -> !set.isEmpty()).build());
    static void register(IEventBus bus) { ATTACHMENTS.register(bus); }
    public CompoundTag data(Entity entity) { return entity.getPersistentData(); }
    public LongOpenHashSet placed(LevelChunk chunk, boolean create) { return !create && !chunk.hasData(PLACED) ? null : chunk.getData(PLACED); }
}
