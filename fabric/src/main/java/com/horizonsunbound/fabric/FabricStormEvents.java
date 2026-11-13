package com.horizonsunbound.fabric;

import com.horizonsunbound.Storm;
import com.horizonsunbound.platform.StormEvents;
import net.minecraft.server.level.ServerLevel;

/** Fabric has no native event bus; StormHooks dispatches the shared listener API. */
public final class FabricStormEvents implements StormEvents {
    public boolean announce(ServerLevel level, Storm storm) { return true; }
    public void born(ServerLevel level, Storm storm) {}
    public void dying(ServerLevel level, Storm storm) {}
    public void end(ServerLevel level, Storm storm) {}
    public void merge(ServerLevel level, Storm storm, Storm first, Storm second) {}
}
