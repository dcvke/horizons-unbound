package com.horizonsunbound.api;

import com.horizonsunbound.Storm;
import com.horizonsunbound.StormManager;
import com.horizonsunbound.StormType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

/**
 * What addons may use. Server side only. Everything else in the mod may change between versions; this and
 * {@link StormEvent} stay. See docs/ADDONS.md.
 */
public final class HorizonsUnboundApi {
    private static final EnumMap<StormType, List<StormEffect>> EFFECTS = new EnumMap<>(StormType.class);

    private HorizonsUnboundApi() {}

    /** Something a storm of some kind does to the world, every tick, once it is born (a merged storm does both kinds'). */
    @FunctionalInterface
    public interface StormEffect {
        /** @param near the players within the storm's zone (its effects only matter where someone is); may be empty */
        void tick(ServerLevel level, Storm storm, List<ServerPlayer> near);
    }

    /** Adds an effect to every storm of this kind (call it once, e.g. from your mod's constructor). Errors in it are caught and logged. */
    public static synchronized void addEffect(StormType kind, StormEffect effect) {
        EFFECTS.computeIfAbsent(kind, k -> new ArrayList<>()).add(effect);
    }

    /** The effects added for this kind (the mod calls them; addons need not). */
    public static List<StormEffect> effects(StormType kind) { return EFFECTS.getOrDefault(kind, List.of()); }

    /** The storms of a level that have risen (read-only). */
    public static List<Storm> storms(ServerLevel level) { return StormManager.get(level).storms(); }

    /**
     * Brings a storm to a spot. With omens it waits to be seen like a natural one (and gets its birth cutscene);
     * without, it rises right away. Returns null if a listener cancelled it ({@link StormEvent.Announce}).
     */
    public static Storm spawn(ServerLevel level, StormType kind, BlockPos at, int diameter, boolean omens) {
        Storm s = new Storm(UUID.randomUUID(), kind, diameter, at.getX() + .5, at.getY(), at.getZ() + .5);
        StormManager m = StormManager.get(level);
        if (omens) return m.announce(level, s) ? s : null;
        m.add(level, s);
        return s;
    }
}
