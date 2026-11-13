package com.horizonsunbound.fabric;

import com.horizonsunbound.*;
import com.horizonsunbound.client.StormConfig;
import com.horizonsunbound.effect.*;
import fuzs.forgeconfigapiport.api.config.v2.ForgeConfigRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.fml.config.ModConfig;

public final class FabricMod implements ModInitializer {
    public static ResourceLocation id(String path) { return new ResourceLocation(HorizonsUnbound.MODID, path); }

    @Override public void onInitialize() {
        ForgeConfigRegistry.INSTANCE.register(HorizonsUnbound.MODID, ModConfig.Type.COMMON, StormsConfig.SPEC);
        ForgeConfigRegistry.INSTANCE.register(HorizonsUnbound.MODID, ModConfig.Type.CLIENT, StormConfig.SPEC);
        Registry.register(BuiltInRegistries.BLOCK, id("reality_anchor"), ModContent.REALITY_ANCHOR.get());
        Registry.register(BuiltInRegistries.BLOCK, id("hot_crust"), ModContent.HOT_CRUST.get());
        Registry.register(BuiltInRegistries.ITEM, id("reality_anchor"), ModContent.REALITY_ANCHOR_ITEM.get());
        Registry.register(BuiltInRegistries.ENTITY_TYPE, id("echo"), ModContent.ECHO.get());
        ModSounds.ALL.forEach((name, sound) -> Registry.register(BuiltInRegistries.SOUND_EVENT, id(name), sound.get()));
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, id("thaw_smoke"), ModContent.THAW_SMOKE.get());
        FabricStorage.initialize();
        FabricDefaultAttributeRegistry.register(ModContent.ECHO.get(), Mob.createMobAttributes());
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> entries.accept(ModContent.REALITY_ANCHOR_ITEM.get()));
        ServerPlayNetworking.registerGlobalReceiver(StormCutscenes.Payload.ID, (server, player, handler, buffer, response) -> {
            StormCutscenes.Payload packet = StormCutscenes.Payload.read(buffer);
            server.execute(() -> StormCutscenes.stopped(player));
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> HorizonsUnbound.registerCommands(dispatcher));
        ServerTickEvents.END_WORLD_TICK.register(StormManager::onLevelTick);
        ServerTickEvents.END_WORLD_TICK.register(StormSpawner::onLevelTick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> StormManager.send(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            StormCutscenes.onLogout(handler.player);
            EndEffects.onLogout(handler.player);
        });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, from, to) -> StormManager.send(player));
        ServerPlayerEvents.AFTER_RESPAWN.register((original, player, alive) -> StormManager.send(player));
        ServerPlayerEvents.COPY_FROM.register((original, player, alive) -> {
            FabricStorage.resetAfterClone(player);
            StormCutscenes.onClone(original, player);
            StormSpawner.onClone(original, player);
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, damage) -> !(entity instanceof ServerPlayer player) || !StormCutscenes.invulnerable(player, source));
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) PlacedBlocks.mark(serverLevel, pos, false);
        });
    }
}
