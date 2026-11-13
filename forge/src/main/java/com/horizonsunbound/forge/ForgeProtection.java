package com.horizonsunbound.forge;
import com.horizonsunbound.platform.Protection;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.level.BlockEvent;
import java.util.UUID;
public final class ForgeProtection implements Protection {
    private static final GameProfile STORM = new GameProfile(UUID.fromString("5b0f6d2e-7a1c-4c51-9a5e-3f2a6c9d1e47"), "[Horizons Unbound storm]");
    public boolean canBreak(ServerLevel level, BlockPos pos) {
        var fake = FakePlayerFactory.get(level, STORM);
        fake.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
        return !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), fake));
    }
}
