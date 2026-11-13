package com.horizonsunbound.neoforge;

import com.horizonsunbound.*;
import com.horizonsunbound.effect.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = HorizonsUnbound.MODID)
public final class NeoServer {
    @SubscribeEvent public static void manager(LevelTickEvent.Post e) {
        if (e.getLevel() instanceof ServerLevel level) StormManager.onLevelTick(level);
    }
    @SubscribeEvent public static void spawner(LevelTickEvent.Post e) {
        if (e.getLevel() instanceof ServerLevel level) StormSpawner.onLevelTick(level);
    }
    @SubscribeEvent public static void discovery(PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer player) StormCutscenes.onPlayerTick(player);
    }
    @SubscribeEvent public static void unlock(PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer player) StormSpawner.onPlayerTick(player);
    }
    @SubscribeEvent public static void paths(PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer player) EndEffects.onPlayerTick(player);
    }
    @SubscribeEvent public static void projectile(EntityTickEvent.Pre e) { EndEffects.onEntityTick(e.getEntity()); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) {
        StormCutscenes.onClone(e.getOriginal(), e.getEntity());
        StormSpawner.onClone(e.getOriginal(), e.getEntity());
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) { StormManager.send(e.getEntity()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { StormManager.send(e.getEntity()); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e) { StormManager.send(e.getEntity()); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        StormCutscenes.onLogout(e.getEntity());
        EndEffects.onLogout(e.getEntity());
    }
    @SubscribeEvent public static void damage(LivingIncomingDamageEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && StormCutscenes.invulnerable(p, e.getSource())) e.setCanceled(true);
    }
    @SubscribeEvent public static void vibration(VanillaGameEvent e) {
        if (e.getLevel() instanceof ServerLevel level) SculkEffects.onGameEvent(level, e.getCause(), e.getVanillaEvent());
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void place(BlockEvent.EntityPlaceEvent e) {
        if (e.isCanceled() || !(e.getEntity() instanceof Player) || !(e.getLevel() instanceof ServerLevel level)) return;
        if (e instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            for (var snapshot : multi.getReplacedBlockSnapshots()) PlacedBlocks.mark(level, snapshot.getPos(), true);
        } else PlacedBlocks.mark(level, e.getPos(), true);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void breakBlock(BlockEvent.BreakEvent e) {
        if (!e.isCanceled() && e.getLevel() instanceof ServerLevel level) PlacedBlocks.mark(level, e.getPos(), false);
    }
}
