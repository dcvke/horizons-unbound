package com.horizonsunbound.neoforge;
import com.horizonsunbound.platform.Platform;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
public final class NeoPlatform implements Platform {
    public boolean isModLoaded(String id) { return ModList.get().isLoaded(id); }
    public boolean isClient() { return FMLEnvironment.dist.isClient(); }
    public boolean isDevelopment() { return !FMLEnvironment.production; }
}
