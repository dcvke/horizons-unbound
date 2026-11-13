package com.horizonsunbound.forge;

import com.horizonsunbound.*;
import com.horizonsunbound.client.StormConfig;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;

@Mod(HorizonsUnbound.MODID)
public final class ForgeMod {
    public ForgeMod() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, StormsConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, StormConfig.SPEC);
        if (FMLEnvironment.dist.isClient()) ForgeClient.configure();
        DeferredRegister<Block> blocks = DeferredRegister.create(Registries.BLOCK, HorizonsUnbound.MODID);
        blocks.register("reality_anchor", ModContent.REALITY_ANCHOR);
        blocks.register("hot_crust", ModContent.HOT_CRUST);
        blocks.register(bus);
        DeferredRegister<Item> items = DeferredRegister.create(Registries.ITEM, HorizonsUnbound.MODID);
        items.register("reality_anchor", ModContent.REALITY_ANCHOR_ITEM);
        items.register(bus);
        DeferredRegister<EntityType<?>> entities = DeferredRegister.create(Registries.ENTITY_TYPE, HorizonsUnbound.MODID);
        entities.register("echo", ModContent.ECHO);
        entities.register(bus);
        DeferredRegister<SoundEvent> sounds = DeferredRegister.create(Registries.SOUND_EVENT, HorizonsUnbound.MODID);
        ModSounds.ALL.forEach((name, sound) -> sounds.register(name, sound));
        sounds.register(bus);
        DeferredRegister<ParticleType<?>> particles = DeferredRegister.create(Registries.PARTICLE_TYPE, HorizonsUnbound.MODID);
        particles.register("thaw_smoke", ModContent.THAW_SMOKE);
        particles.register(bus);
        bus.addListener((RegisterCapabilitiesEvent e) -> e.register(ForgeStorage.PlacedData.class));
        bus.addListener((EntityAttributeCreationEvent e) -> e.put(ModContent.ECHO.get(), Mob.createMobAttributes().build()));
        bus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) e.accept(ModContent.REALITY_ANCHOR_ITEM.get());
        });
        ForgeNetwork.register();
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.RegisterCommandsEvent e) -> HorizonsUnbound.registerCommands(e.getDispatcher()));
    }
}
