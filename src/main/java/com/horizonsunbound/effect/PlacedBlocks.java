package com.horizonsunbound.effect;

import com.horizonsunbound.HorizonsUnbound;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * Remembers, per chunk, which blocks players placed, so storms never reshape or overwrite them (a stone house is
 * "stone" too). Saved with the chunk; a broken block is forgotten again. Blocks placed before the mod was
 * installed are unknown: those only have the spawn/bed protection.
 */
@EventBusSubscriber(modid = HorizonsUnbound.MODID)
public final class PlacedBlocks {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, HorizonsUnbound.MODID);
    private static final Codec<LongOpenHashSet> CODEC = Codec.LONG_STREAM.xmap(s -> new LongOpenHashSet(s.toArray()), set -> Arrays.stream(set.toLongArray()));
    private static final Supplier<AttachmentType<LongOpenHashSet>> PLACED = ATTACHMENTS.register("player_placed",
            () -> AttachmentType.builder(() -> new LongOpenHashSet()).serialize(CODEC, set -> !set.isEmpty()).build());

    private PlacedBlocks() {}

    public static void register(IEventBus modBus) { ATTACHMENTS.register(modBus); }

    /** True if a player put this block there. Only call for loaded positions. */
    public static boolean placed(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        return chunk.hasData(PLACED) && chunk.getData(PLACED).contains(pos.asLong());
    }

    /** True if any player-placed block lies in the box (its chunks must be loaded). */
    public static boolean anyIn(ServerLevel level, BoundingBox box) {
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                if (!chunk.hasData(PLACED)) continue;
                for (long p : chunk.getData(PLACED)) if (box.isInside(BlockPos.of(p))) return true;
            }
        }
        return false;
    }

    private static void mark(ServerLevel level, BlockPos pos, boolean add) {
        LevelChunk chunk = level.getChunkAt(pos);
        if (!add && !chunk.hasData(PLACED)) return;
        LongOpenHashSet set = chunk.getData(PLACED);
        if (add ? set.add(pos.asLong()) : set.remove(pos.asLong())) chunk.setUnsaved(true);
    }

    // last, so a placement another mod cancels (claims, region protection) isn't counted
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof Player) || !(event.getLevel() instanceof ServerLevel level)) return;
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multi) { // beds, doors: every part
            for (BlockSnapshot s : multi.getReplacedBlockSnapshots()) mark(level, s.getPos(), true);
        } else mark(level, event.getPos(), true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level) mark(level, event.getPos(), false);
    }
}
