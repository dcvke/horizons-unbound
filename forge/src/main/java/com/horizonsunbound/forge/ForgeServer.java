package com.horizonsunbound.forge;

import com.horizonsunbound.*;
import com.horizonsunbound.effect.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.VanillaGameEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HorizonsUnbound.MODID)
public final class ForgeServer {
    @SubscribeEvent public static void level(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel level)) return;
        StormManager.onLevelTick(level);
        StormSpawner.onLevelTick(level);
    }
    @SubscribeEvent public static void player(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer player)) return;
        StormCutscenes.onPlayerTick(player);
        StormSpawner.onPlayerTick(player);
        EndEffects.onPlayerTick(player);
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) {
        StormCutscenes.onClone(e.getOriginal(), e.getEntity());
        StormSpawner.onClone(e.getOriginal(), e.getEntity());
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) { StormManager.send(e.getEntity()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { StormManager.send(e.getEntity()); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e) { StormManager.send(e.getEntity()); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        StormCutscenes.onLogout(e.getEntity()); EndEffects.onLogout(e.getEntity());
    }
    @SubscribeEvent public static void damage(LivingAttackEvent e) {
        if (e.getEntity() instanceof ServerPlayer player && StormCutscenes.invulnerable(player, e.getSource())) e.setCanceled(true);
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
