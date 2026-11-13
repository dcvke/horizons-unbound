package com.horizonsunbound.fabric;

import com.horizonsunbound.platform.Platform;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

public final class FabricPlatform implements Platform {
    public boolean isModLoaded(String id) { return FabricLoader.getInstance().isModLoaded(id); }
    public boolean isClient() { return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT; }
    public boolean isDevelopment() { return FabricLoader.getInstance().isDevelopmentEnvironment(); }
}
