package com.horizonsunbound;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A natural storm isn't born until someone is there to see it: once its omens are up it waits, and when a player
 * comes within TRIGGER blocks of where it will stand, they get the cutscene of its birth (client: Cutscene). The
 * camera needs PREROLL ticks to glide over, so the storm rises right as it arrives.
 * <p>
 * A storm already out there gets a cutscene of its own the first time a player finds one of its kind: coming within
 * DISCOVER blocks of its edge under open sky. Each kind once per player (witnessing a birth counts as meeting that
 * kind too); the kinds met are kept with the player, through death. Watchers take no damage meanwhile.
 */
@EventBusSubscriber(modid = HorizonsUnbound.MODID)
public final class StormCutscenes {
    public static final int PREROLL = 200;      // ticks from the trigger to the storm rising
    public static final int TRIGGER = 100;      // blocks between the player and the storm's edge-to-be
    public static final int LENGTH = PREROLL + 470; // the whole birth cutscene, ticks (client: Cutscene)
    public static final int DISCOVER = 120;     // blocks between the player and a storm's edge for it to count as found
    public static final int DISCOVER_LENGTH = 450; // the whole discovery cutscene, ticks (client: Cutscene)
    public static final int BIRTH = 0;          // Payload mode; a discovery is 1 + the kind's ordinal
    public static final int DEATH = 99;         // Payload mode: watch it come apart
    public static final int DEATH_LENGTH = 250;  // the whole death cutscene, ticks (client: Cutscene)
    private static final String FINALE = "horizons_unbound_finale"; // persistent data: "pending" once every kind is met, then "came"
    private static final String MET = "horizons_unbound_met"; // persistent data: the kinds of storm met, "end,nether"
    private static final Map<UUID, Long> WATCHING = new HashMap<>(); // player -> game time their cutscene ends

    private StormCutscenes() {}

    /**
     * Server to client: "watch this storm" - be born (mode BIRTH, it rises in PREROLL ticks) or be found (mode 1 + the
     * kind found: a merged storm may be new by its second kind). Client to server: "I stopped watching".
     */
    public record Payload(UUID storm, int mode) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HorizonsUnbound.MODID, "cutscene"));
        public static final StreamCodec<FriendlyByteBuf, Payload> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Payload::storm, ByteBufCodecs.VAR_INT, Payload::mode, Payload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Starts the cutscene for the players around this coming storm if one of them is close enough and steady;
     * returns true if it started (the storm then rises in PREROLL ticks).
     */
    static boolean trigger(ServerLevel level, Storm s) {
        List<ServerPlayer> watchers = new ArrayList<>();
        boolean close = false;
        for (ServerPlayer p : level.players()) {
            double d = s.dist(p.getX(), p.getZ());
            if (d > s.radius() + TRIGGER + 50 || !p.isAlive() || !steady(p) || watching(p)) continue;
            close |= d <= s.radius() + TRIGGER;
            watchers.add(p);
        }
        if (!close) return false;
        for (ServerPlayer p : watchers) {
            WATCHING.put(p.getUUID(), level.getGameTime() + LENGTH);
            meet(p, s.type);
            PacketDistributor.sendToPlayer(p, new Payload(s.id, BIRTH));
        }
        return true;
    }

    /** Twice a second: has this player just found a kind of storm they have never met? */
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.tickCount % 10 != 0 || !StormsConfig.CUTSCENES.get() || !p.isAlive() || !steady(p) || watching(p)) return;
        ServerLevel level = p.serverLevel();
        if (!level.dimensionType().hasCeiling() && !level.canSeeSky(p.blockPosition())) return; // not found from a cave
        for (Storm s : StormManager.get(level).storms()) {
            if (s.newborn() || s.grand || s.dying() > 0) continue; // being born or dying: those have their own cutscenes
            double d = s.dist(p.getX(), p.getZ());
            if (d < s.radius() + 8 || d > s.radius() + DISCOVER) continue; // too far, or already in it
            StormType kind = !met(p, s.type) ? s.type : s.mix != null && !met(p, s.mix) ? s.mix : null;
            if (kind == null) continue;
            meet(p, s.type);
            if (s.mix != null) meet(p, s.mix);
            WATCHING.put(p.getUUID(), level.getGameTime() + DISCOVER_LENGTH);
            PacketDistributor.sendToPlayer(p, new Payload(s.id, 1 + kind.ordinal()));
            return;
        }
    }

    private static boolean watching(ServerPlayer p) {
        Long end = WATCHING.get(p.getUUID());
        return end != null && end > p.level().getGameTime();
    }

    /** A storm begins to come apart: the players close by and under the sky watch it go. */
    static void dying(ServerLevel level, Storm s) {
        if (!StormsConfig.CUTSCENES.get()) return;
        for (ServerPlayer p : level.players()) {
            double d = s.dist(p.getX(), p.getZ());
            if (d < s.radius() + 8 || d > s.radius() + DISCOVER || !p.isAlive() || !steady(p) || watching(p) || !level.canSeeSky(p.blockPosition())) continue;
            WATCHING.put(p.getUUID(), level.getGameTime() + DEATH_LENGTH);
            PacketDistributor.sendToPlayer(p, new Payload(s.id, DEATH));
        }
    }

    /** True once this player has met every kind and the great storm has yet to come to them. */
    static boolean finalePending(ServerPlayer p) { return "pending".equals(p.getPersistentData().getString(FINALE)); }

    static void finaleCame(ServerPlayer p) { p.getPersistentData().putString(FINALE, "came"); }

    static boolean met(ServerPlayer p, StormType kind) { return List.of(p.getPersistentData().getString(MET).split(",")).contains(kind.id); }

    private static void meet(ServerPlayer p, StormType kind) {
        if (met(p, kind)) return;
        String was = p.getPersistentData().getString(MET);
        p.getPersistentData().putString(MET, was.isEmpty() ? kind.id : was + "," + kind.id);
        boolean all = true;
        for (StormType t : StormType.values()) all &= met(p, t);
        if (all && p.getPersistentData().getString(FINALE).isEmpty()) p.getPersistentData().putString(FINALE, "pending");
    }

    /** The kinds met outlive death (persistent data isn't copied to the new player by itself). */
    @SubscribeEvent
    static void onClone(PlayerEvent.Clone e) {
        String met = e.getOriginal().getPersistentData().getString(MET);
        if (!met.isEmpty()) e.getEntity().getPersistentData().putString(MET, met);
        String finale = e.getOriginal().getPersistentData().getString(FINALE);
        if (!finale.isEmpty()) e.getEntity().getPersistentData().putString(FINALE, finale);
    }

    /** Safe to take the camera away from: standing, flying or riding; not falling, gliding or in lava. */
    private static boolean steady(ServerPlayer p) {
        return p.isSpectator() || p.getAbilities().flying || p.isPassenger() || p.onGround() && !p.isInLava() && !p.isFallFlying();
    }

    /** The client skipped the cutscene or turned cutscenes off. */
    public static void stopped(ServerPlayer p) { WATCHING.remove(p.getUUID()); }

    @SubscribeEvent
    static void onDamage(LivingIncomingDamageEvent e) {
        if (!WATCHING.isEmpty() && e.getEntity() instanceof ServerPlayer p && !e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY) && watching(p)) e.setCanceled(true);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { WATCHING.remove(e.getEntity().getUUID()); }
}
