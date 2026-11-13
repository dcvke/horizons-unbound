package com.horizonsunbound.effect;

import com.horizonsunbound.HorizonsUnbound;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * Remembers, per chunk, which blocks players placed, so storms never reshape or overwrite them (a stone house is
 * "stone" too). Saved with the chunk; a broken block is forgotten again. Blocks placed before the mod was
 * installed are unknown: those only have the spawn/bed protection.
 */

public final class PlacedBlocks {
    private PlacedBlocks() {}

    /** True if a player put this block there. Only call for loaded positions. */
    public static boolean placed(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        LongOpenHashSet placed = com.horizonsunbound.platform.Services.STORAGE.placed(chunk, false);
        return placed != null && placed.contains(pos.asLong());
    }

    /** True if any player-placed block lies in the box (its chunks must be loaded). */
    public static boolean anyIn(ServerLevel level, BoundingBox box) {
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                LongOpenHashSet placed = com.horizonsunbound.platform.Services.STORAGE.placed(chunk, false);
                if (placed == null) continue;
                for (long p : placed) if (box.isInside(BlockPos.of(p))) return true;
            }
        }
        return false;
    }

    public static void mark(ServerLevel level, BlockPos pos, boolean add) {
        LevelChunk chunk = level.getChunkAt(pos);
        LongOpenHashSet set = com.horizonsunbound.platform.Services.STORAGE.placed(chunk, add);
        if (set == null) return;
        if (add ? set.add(pos.asLong()) : set.remove(pos.asLong())) chunk.setUnsaved(true);
    }

}
