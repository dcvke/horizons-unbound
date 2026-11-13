package com.horizonsunbound.platform;

import net.minecraft.server.level.ServerPlayer;
import java.util.Map;
import java.util.WeakHashMap;

/** Vanilla 1.20.1 receives the client distance but does not keep it. */
public final class ViewDistances {
    private static final Map<ServerPlayer, Integer> DISTANCES = new WeakHashMap<>();
    private ViewDistances() {}
    public static void set(ServerPlayer player, int distance) { DISTANCES.put(player, Math.max(2, distance)); }
    public static int get(ServerPlayer player) { return DISTANCES.getOrDefault(player, player.server.getPlayerList().getViewDistance()); }
}
