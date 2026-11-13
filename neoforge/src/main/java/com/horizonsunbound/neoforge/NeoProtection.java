package com.horizonsunbound.neoforge;
import com.horizonsunbound.platform.Protection;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import java.util.UUID;
public final class NeoProtection implements Protection {
    private static final GameProfile STORM = new GameProfile(UUID.fromString("5b0f6d2e-7a1c-4c51-9a5e-3f2a6c9d1e47"), "[Horizons Unbound storm]");
    public boolean canBreak(ServerLevel level, BlockPos pos) {
        var fake = FakePlayerFactory.get(level, STORM);
        fake.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
        return !NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), fake)).isCanceled();
    }
}
