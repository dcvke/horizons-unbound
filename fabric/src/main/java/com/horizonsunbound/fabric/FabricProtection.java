package com.horizonsunbound.fabric;

import com.horizonsunbound.platform.Protection;
import com.mojang.authlib.GameProfile;
import eu.pb4.common.protection.api.CommonProtection;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.UUID;

public final class FabricProtection implements Protection {
    private static final GameProfile STORM = new GameProfile(UUID.fromString("5b0f6d2e-7a1c-4c51-9a5e-3f2a6c9d1e47"), "[Horizons Unbound storm]");
    public boolean canBreak(ServerLevel level, BlockPos pos) {
        FakePlayer fake = FakePlayer.get(level, STORM);
        fake.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
        boolean event = PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, fake, pos, level.getBlockState(pos), level.getBlockEntity(pos));
        return event && (!FabricLoader.getInstance().isModLoaded("common-protection-api") || CommonProtection.canBreakBlock(level, pos, STORM, fake));
    }
}
