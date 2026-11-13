package com.horizonsunbound.platform;

public interface Protection {
    boolean canBreak(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos);
}
