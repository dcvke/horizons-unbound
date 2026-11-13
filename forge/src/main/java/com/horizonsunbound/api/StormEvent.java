package com.horizonsunbound.api;

import com.horizonsunbound.Storm;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.Cancelable;

/**
 * A storm's life, for addons: posted on {@code MinecraftForge.EVENT_BUS}, server side. Listen with
 * {@code MinecraftForge.EVENT_BUS.addListener((StormEvent.Born e) -> ...)}, or from KubeJS with
 * {@code ForgeEvents.onEvent('com.horizonsunbound.api.StormEvent$Born', e => ...)} in startup scripts.
 * The storm is the live object: read it freely, change it only where a method says so.
 */
public abstract class StormEvent extends Event {
    private final ServerLevel level;
    private final Storm storm;

    protected StormEvent(ServerLevel level, Storm storm) {
        this.level = level;
        this.storm = storm;
    }

    public ServerLevel getLevel() { return level; }

    public Storm getStorm() { return storm; }

    /** A storm is about to come (its omens start). Cancel it and it never comes. */
    @Cancelable
    public static class Announce extends StormEvent {
        public Announce(ServerLevel level, Storm storm) { super(level, storm); }
    }

    /** A storm rises: its birth begins (after its omens, or right away from /storm spawn). */
    public static class Born extends StormEvent {
        public Born(ServerLevel level, Storm storm) { super(level, storm); }
    }

    /** A storm starts to come apart: its last {@link Storm#DEATH} ticks. */
    public static class Dying extends StormEvent {
        public Dying(ServerLevel level, Storm storm) { super(level, storm); }
    }

    /** A storm is gone (its life is over; not posted for /storm clear). */
    public static class End extends StormEvent {
        public End(ServerLevel level, Storm storm) { super(level, storm); }
    }

    /** Two storms ran into each other and became one: {@link #getStorm()} is the new storm. */
    public static class Merge extends StormEvent {
        private final Storm first, second;

        public Merge(ServerLevel level, Storm merged, Storm first, Storm second) {
            super(level, merged);
            this.first = first;
            this.second = second;
        }

        public Storm getFirst() { return first; }

        public Storm getSecond() { return second; }
    }
}
