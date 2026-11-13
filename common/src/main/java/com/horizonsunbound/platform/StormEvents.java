package com.horizonsunbound.platform;

public interface StormEvents {
    boolean announce(net.minecraft.server.level.ServerLevel level, com.horizonsunbound.Storm storm);
    void born(net.minecraft.server.level.ServerLevel level, com.horizonsunbound.Storm storm);
    void dying(net.minecraft.server.level.ServerLevel level, com.horizonsunbound.Storm storm);
    void end(net.minecraft.server.level.ServerLevel level, com.horizonsunbound.Storm storm);
    void merge(net.minecraft.server.level.ServerLevel level, com.horizonsunbound.Storm storm, com.horizonsunbound.Storm first, com.horizonsunbound.Storm second);
}
