package com.horizonsunbound.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Adds the mixins into other mods (Xaero's maps) only when those mods are installed, so a game without them
 * doesn't log missing classes. Asks whichever loader is running, by reflection: this runs before the mod's own
 * services are available.
 */
public final class CompatMixins implements IMixinConfigPlugin {
    @Override
    public List<String> getMixins() {
        List<String> out = new ArrayList<>();
        if (loaded("xaerominimap") || loaded("xaerominimapfair")) out.add("XaeroMinimapHighlightsMixin");
        if (loaded("xaeroworldmap")) out.add("XaeroWorldMapHighlightsMixin");
        return out;
    }

    static boolean loaded(String id) {
        try {
            Object fabric = Class.forName("net.fabricmc.loader.api.FabricLoader").getMethod("getInstance").invoke(null);
            return (boolean) fabric.getClass().getMethod("isModLoaded", String.class).invoke(fabric, id);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        for (String list : new String[] {"net.neoforged.fml.loading.LoadingModList", "net.minecraftforge.fml.loading.LoadingModList"}) {
            try {
                Object mods = Class.forName(list).getMethod("get").invoke(null);
                return mods.getClass().getMethod("getModFileById", String.class).invoke(mods, id) != null;
            } catch (ReflectiveOperationException | LinkageError ignored) {
            }
        }
        return false;
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return true; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
