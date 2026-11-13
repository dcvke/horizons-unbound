package com.horizonsunbound.client;

import com.horizonsunbound.*;
import com.horizonsunbound.api.HorizonsUnboundApi;
import com.horizonsunbound.effect.PlacedBlocks;
import com.horizonsunbound.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Consumer;

/** Scripted save/reopen checks, active only when ClientTest dispatches a persist step. */
public final class PersistenceTest {
    private PersistenceTest() {}
    public static void run(String operation, ServerPlayer player, Path directory, Consumer<String> log) {
        try {
            if (operation.equals("seed")) {
                BlockPos pos = placeStone(player);
                var data = Services.STORAGE.data(player);
                data.putString("horizons_unbound_met", "end");
                data.putString("horizons_unbound_finale", "pending");
                data.putBoolean("horizons_unbound_deep_dark", true);
                Storm storm = HorizonsUnboundApi.spawn(player.serverLevel(), StormType.END, pos.offset(220, 0, 0), Storm.MIN_DIAMETER, false);
                require(storm != null, "storm was cancelled");
                storm.age = 900;
                Properties checkpoint = new Properties();
                checkpoint.setProperty("position", Long.toString(pos.asLong()));
                checkpoint.setProperty("storm", storm.id.toString());
                try (var writer = Files.newBufferedWriter(directory.resolve("persistence.properties"))) { checkpoint.store(writer, "Save/reopen checkpoint"); }
            } else if (operation.equals("check")) {
                Properties checkpoint = new Properties();
                try (var reader = Files.newBufferedReader(directory.resolve("persistence.properties"))) { checkpoint.load(reader); }
                BlockPos pos = BlockPos.of(Long.parseLong(checkpoint.getProperty("position")));
                player.serverLevel().getChunkAt(pos);
                require(player.serverLevel().getBlockState(pos).is(Blocks.STONE), "placed stone was lost");
                require(PlacedBlocks.placed(player.serverLevel(), pos), "placed-block protection was lost");
                UUID id = UUID.fromString(checkpoint.getProperty("storm"));
                require(HorizonsUnboundApi.storms(player.serverLevel()).stream().anyMatch(storm -> storm.id.equals(id) && storm.type == StormType.END), "saved storm was lost");
                memory(player);
            } else if (operation.equals("memory")) memory(player);
            else if (operation.equals("old")) {
                Properties expected = new Properties();
                try (var reader = Files.newBufferedReader(directory.resolve("old-world.properties"))) { expected.load(reader); }
                for (String key : expected.stringPropertyNames()) {
                    if (key.startsWith("storm.")) {
                        UUID id = UUID.fromString(key.substring(6));
                        require(HorizonsUnboundApi.storms(player.serverLevel()).stream().anyMatch(storm -> storm.id.equals(id) && storm.type.id.equals(expected.getProperty(key))), "old storm was lost: " + id);
                    } else if (key.equals("horizons_unbound_deep_dark")) {
                        require(Services.STORAGE.data(player).getBoolean(key) == Boolean.parseBoolean(expected.getProperty(key)), "old memory changed: " + key);
                    } else require(Services.STORAGE.data(player).getString(key).equals(expected.getProperty(key)), "old memory changed: " + key);
                }
            } else throw new IllegalArgumentException("unknown persistence operation: " + operation);
            log.accept("PASS persistence " + operation);
        } catch (Exception | LinkageError error) { log.accept("FAIL persistence " + operation + ": " + error); }
    }
    private static void memory(ServerPlayer player) {
        var data = Services.STORAGE.data(player);
        require(data.getString("horizons_unbound_met").equals("end"), "encounter memory was lost");
        require(data.getString("horizons_unbound_finale").equals("pending"), "finale memory was lost");
        require(data.getBoolean("horizons_unbound_deep_dark"), "Deep Dark memory was lost");
    }
    private static BlockPos placeStone(ServerPlayer player) {
        BlockPos base = player.blockPosition().offset(3, -2, 0), pos = base.above();
        var level = player.serverLevel();
        level.setBlockAndUpdate(base, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        var mode = player.gameMode.getGameModeForPlayer();
        var hand = player.getMainHandItem();
        try {
            player.setGameMode(GameType.CREATIVE);
            var stack = new ItemStack(Blocks.STONE);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            var hit = new BlockHitResult(Vec3.atCenterOf(base).add(0, .5, 0), Direction.UP, base, false);
            var result = player.gameMode.useItemOn(player, level, stack, InteractionHand.MAIN_HAND, hit);
            require(result.consumesAction() && level.getBlockState(pos).is(Blocks.STONE), "vanilla item placement failed");
            require(PlacedBlocks.placed(level, pos), "player-placement hook did not protect the block");
            return pos;
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, hand);
            player.setGameMode(mode);
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
