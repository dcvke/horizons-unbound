package com.horizonsunbound.api;

import com.horizonsunbound.Storm;
import net.minecraft.server.level.ServerLevel;

/** Loader-neutral server events. Return false from announce to prevent a storm. */
public interface StormListener {
    default boolean announce(ServerLevel level, Storm storm) { return true; }
    default void born(ServerLevel level, Storm storm) {}
    default void dying(ServerLevel level, Storm storm) {}
    default void end(ServerLevel level, Storm storm) {}
    default void merge(ServerLevel level, Storm storm, Storm first, Storm second) {}
}
