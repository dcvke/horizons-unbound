package com.horizonsunbound.client;

import com.horizonsunbound.effect.StormEffects;
import com.horizonsunbound.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import java.util.UUID;
import java.util.function.Consumer;

/** Optional scripted integration check against an installed Open Parties and Claims jar. */
public final class ClaimTest {
    private ClaimTest() {}
    public static void run(ServerPlayer player, Consumer<String> log) {
        try {
            var level = player.serverLevel();
            BlockPos pos = player.blockPosition().offset(512, -2, 512);
            level.getChunkAt(pos);
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            Class<?> apiType = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
            Object api = apiType.getMethod("get", MinecraftServer.class).invoke(null, player.getServer());
            Object claims = apiType.getMethod("getServerClaimsManager").invoke(api);
            Class<?> claimsType = Class.forName("xaero.pac.common.server.claims.api.IServerClaimsManagerAPI");
            ResourceLocation dimension = level.dimension().location();
            int x = pos.getX() >> 4, z = pos.getZ() >> 4;
            if (!Services.PROTECTION.canBreak(level, pos)) throw new IllegalStateException("Unclaimed control denied");
            Object claim = claimsType.getMethod("claim", ResourceLocation.class, UUID.class, int.class, int.class, int.class, boolean.class)
                    .invoke(claims, dimension, player.getUUID(), 0, x, z, false);
            if (claim == null) throw new IllegalStateException("OPAC did not create the claim");
            try {
                if (Services.PROTECTION.canBreak(level, pos)) throw new IllegalStateException("Storm fake player may break another player's claim");
                var free = StormEffects.class.getDeclaredMethod("free", net.minecraft.server.level.ServerLevel.class, BlockPos.class);
                free.setAccessible(true);
                if (Boolean.TRUE.equals(free.invoke(null, level, pos))) throw new IllegalStateException("Storm terrain effect accepted a protected block");
                if (!level.getBlockState(pos).is(Blocks.STONE)) throw new IllegalStateException("Permission check changed the block");
            } finally { claimsType.getMethod("unclaim", ResourceLocation.class, int.class, int.class).invoke(claims, dimension, x, z); }
            log.accept("PASS claim protection: unclaimed control allowed, foreign claim denied, terrain effect rejected, stone retained");
        } catch (Exception | LinkageError error) { log.accept("FAIL claim protection: " + error); }
    }
}
