package com.horizonsunbound.neoforge;
import com.horizonsunbound.Storm;
import com.horizonsunbound.api.StormEvent;
import com.horizonsunbound.platform.StormEvents;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
public final class NeoStormEvents implements StormEvents {
    public boolean announce(ServerLevel level, Storm storm) { return !NeoForge.EVENT_BUS.post(new StormEvent.Announce(level, storm)).isCanceled(); }
    public void born(ServerLevel level, Storm storm) { NeoForge.EVENT_BUS.post(new StormEvent.Born(level, storm)); }
    public void dying(ServerLevel level, Storm storm) { NeoForge.EVENT_BUS.post(new StormEvent.Dying(level, storm)); }
    public void end(ServerLevel level, Storm storm) { NeoForge.EVENT_BUS.post(new StormEvent.End(level, storm)); }
    public void merge(ServerLevel level, Storm storm, Storm first, Storm second) { NeoForge.EVENT_BUS.post(new StormEvent.Merge(level, storm, first, second)); }
}
