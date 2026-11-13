package com.horizonsunbound.neoforge;

import com.horizonsunbound.*;
import com.horizonsunbound.client.StormConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.server.level.ServerPlayer;

@Mod(HorizonsUnbound.MODID)
public final class NeoForgeMod {
    public NeoForgeMod(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, StormsConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, StormConfig.SPEC);
        if (FMLEnvironment.dist.isClient()) NeoClient.configure(container);

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
        NeoStorage.register(bus);
        bus.addListener((EntityAttributeCreationEvent e) -> e.put(ModContent.ECHO.get(), Mob.createMobAttributes().build()));
        bus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) e.accept(ModContent.REALITY_ANCHOR_ITEM.get());
        });
        bus.addListener(NeoForgeMod::payloads);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent e) -> HorizonsUnbound.registerCommands(e.getDispatcher()));
    }

    private static void payloads(RegisterPayloadHandlersEvent e) {
        e.registrar("1").playToClient(StormSyncPayload.TYPE, StormSyncPayload.CODEC, (packet, context) -> NeoClient.sync(packet))
                .playBidirectional(StormCutscenes.Payload.TYPE, StormCutscenes.Payload.CODEC, new DirectionalPayloadHandler<>(
                        (packet, context) -> NeoClient.cutscene(packet),
                        (packet, context) -> StormCutscenes.stopped((ServerPlayer) context.player())));
    }
}
