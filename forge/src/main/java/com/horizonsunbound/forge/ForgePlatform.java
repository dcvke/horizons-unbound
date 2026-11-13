package com.horizonsunbound.forge;
import com.horizonsunbound.platform.Platform;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
public final class ForgePlatform implements Platform {
    public boolean isModLoaded(String id) { return ModList.get().isLoaded(id); }
    public boolean isClient() { return FMLEnvironment.dist.isClient(); }
    public boolean isDevelopment() { return !FMLEnvironment.production; }
}
