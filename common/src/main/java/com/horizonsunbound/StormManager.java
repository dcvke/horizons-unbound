package com.horizonsunbound;

import com.horizonsunbound.effect.StormEffects;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import com.horizonsunbound.platform.StormHooks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The storms of one dimension, saved with the world. Ticked every server tick whatever chunks are loaded,
 * and sent to every player in the dimension once a second (a few dozen bytes per storm) or right away on a change.
 */

public final class StormManager extends SavedData {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    public static final int ANCHOR_RADIUS = 8;
    public static final int OMEN_TICKS = 1200; // a natural storm's omens grow for a minute, then it waits to be seen
    private static final int HOLD = 6000;      // ...for up to five minutes; if nobody came, it rises unseen
    private final List<Storm> storms = new ArrayList<>();
    private final List<Storm> omens = new ArrayList<>(); // storms about to rise
    private final LongOpenHashSet anchors = new LongOpenHashSet();  // reality anchors in this dimension
    private final List<long[]> streams = new ArrayList<>();         // {water source pos, game time it dries up}
    private boolean resync;
    int calm = -1;   // seconds until the next storm may come (StormSpawner); -1: not set yet
    int twinIn = -1; // seconds until a second storm comes to meet the first; -1: none

    public static StormManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(StormManager::load, StormManager::new, "horizons_unbound");
    }

    public List<Storm> storms() { return Collections.unmodifiableList(storms); }

    public void add(ServerLevel level, Storm storm) {
        storms.add(storm);
        StormHooks.born(level, storm);
        if (level.dimension() == Level.OVERWORLD) startThunder(level);
        setDirty();
        sync(level);
    }

    /**
     * A natural storm: first its omens (dark sky, rising wind, nervous animals), then it rises once someone comes close
     * (tickOmens). False if an addon cancelled it (StormEvent.Announce).
     */
    public boolean announce(ServerLevel level, Storm storm) {
        if (!StormHooks.announce(level, storm)) return false;
        storm.omen = OMEN_TICKS;
        omens.add(storm);
        if (StormsConfig.WARNINGS.get()) StormSpawner.warn(level, storm);
        setDirty();
        sync(level);
        return true;
    }

    /** How many storms are coming (omens showing). */
    public int omenCount() { return omens.size(); }

    /** The storm most recently announced (its omens showing), or null. */
    public Storm lastAnnounced() { return omens.isEmpty() ? null : omens.get(omens.size() - 1); }

    public boolean quiet() { return storms.isEmpty() && omens.isEmpty(); }

    /** A reality anchor was set or broken here. */
    public void anchor(BlockPos pos, boolean add) {
        if (add ? anchors.add(pos.asLong()) : anchors.remove(pos.asLong())) setDirty();
    }

    /** True within ANCHOR_RADIUS of a reality anchor: storm effects leave this spot alone. */
    public boolean anchored(double x, double y, double z) {
        if (anchors.isEmpty()) return false;
        for (long a : anchors) {
            double dx = BlockPos.getX(a) + .5 - x, dy = BlockPos.getY(a) + .5 - y, dz = BlockPos.getZ(a) + .5 - z;
            if (dx * dx + dy * dy + dz * dz < ANCHOR_RADIUS * ANCHOR_RADIUS) return true;
        }
        return false;
    }

    /** A short-lived stream source; removed again at game time until. */
    public void addStream(BlockPos pos, long until) {
        streams.add(new long[] {pos.asLong(), until});
        setDirty();
    }

    /** Clients need the storms' marks right away (a seam moved, a strike is coming). */
    public void resync() { resync = true; }

    public int clear(ServerLevel level) {
        int n = storms.size() + omens.size();
        for (Storm s : storms) com.horizonsunbound.effect.StormMobs.leave(level, s);
        storms.clear();
        omens.clear();
        setDirty();
        sync(level);
        return n;
    }

    public static void onLevelTick(ServerLevel level) {
        StormManager m = get(level);
        if (!m.streams.isEmpty() && level.getGameTime() % 20 == 0) m.dryStreams(level);
        if (m.quiet()) return;
        boolean ended = m.tickOmens(level);
        steer(m.storms);
        ended |= m.storms.removeIf(s -> {
            if (s.tick(level)) return false;
            StormHooks.end(level, s);
            return true;
        });
        for (Storm s : m.storms) {
            if (s.life() != Storm.DEATH) continue;
            StormHooks.dying(level, s);
            com.horizonsunbound.effect.StormMobs.leave(level, s);
            StormCutscenes.dying(level, s); // it starts to come apart
        }
        ended |= m.merge(level);
        for (Storm s : m.storms) effects(level, m, s);
        if (ended || m.resync || level.getGameTime() % 20 == 0) {
            m.resync = false;
            if (!m.storms.isEmpty() && level.dimension() == Level.OVERWORLD) startThunder(level);
            m.setDirty();
            m.sync(level);
        }
    }

    /**
     * One storm's effects. If they throw (a bug here, or another mod's block or structure in the way) it must not take
     * the server down: the error is logged and that storm stops acting on the world; it stays in the sky.
     */
    private static void effects(ServerLevel level, StormManager m, Storm s) {
        if (s.broken) return;
        long t0 = System.nanoTime();
        try {
            StormEffects.tick(level, m, s);
        } catch (RuntimeException | LinkageError e) {
            s.broken = true;
            LOGGER.error("Horizons Unbound: a {} storm at {} {} {} stopped acting on the world after an error (it stays in the sky)",
                    s.type.id, (int) s.x, (int) s.y, (int) s.z, e);
        }
        s.cost = s.cost * .95f + (System.nanoTime() - t0) * .05f;
    }

    /**
     * Grows the omens, then holds them until a player comes close: then the storm's cutscene starts and it rises as
     * the camera arrives (StormCutscenes). Meanwhile animals near the coming storm get restless.
     */
    private boolean tickOmens(ServerLevel level) {
        if (omens.isEmpty()) return false;
        boolean rose = false;
        for (java.util.Iterator<Storm> it = omens.iterator(); it.hasNext(); ) {
            Storm o = it.next();
            boolean watched = StormsConfig.CUTSCENES.get();
            if (watched && o.cue < 0 && level.getGameTime() % 5 == 0 && StormCutscenes.trigger(level, o)) o.cue = StormCutscenes.PREROLL;
            boolean rise;
            if (o.cue >= 0) rise = --o.cue <= 0;
            else if (o.omen > 1) { o.omen--; rise = false; }
            else rise = !watched || ++o.held > HOLD; // without cutscenes nothing waits to be seen
            if (rise) {
                it.remove();
                o.omen = 0;
                storms.add(o);
                StormHooks.born(level, o);
                if (level.dimension() == Level.OVERWORLD) startThunder(level);
                rose = true;
            } else if (level.getGameTime() % 20 == 0) StormEffects.restless(level, o);
        }
        return rose;
    }

    /** A storm heading for another to merge turns towards it (the other one's position as it is now). */
    private static void steer(List<Storm> storms) {
        for (Storm s : storms) {
            if (s.target == null) continue;
            Storm t = null;
            for (Storm o : storms) if (o.id.equals(s.target)) t = o;
            if (t == null) s.target = null;
            else s.setHeading((float) Math.atan2(t.z - s.z, t.x - s.x));
        }
    }

    /**
     * Two storms that run into each other become one: of different kinds, a mixed storm whose bands alternate their
     * colors and that does what both do; of one kind, a bigger storm. Rare by nature (see StormSpawner).
     */
    private boolean merge(ServerLevel level) {
        for (int i = 0; i < storms.size(); i++) {
            for (int j = i + 1; j < storms.size(); j++) {
                Storm a = storms.get(i), b = storms.get(j);
                if (a.mix != null || b.mix != null || a.grand || b.grand || a.dist(b.x, b.z) > (a.radius() + b.radius()) * .45f) continue;
                Storm big = a.diameter >= b.diameter ? a : b, small = big == a ? b : a;
                Storm m = new Storm(big.id, big.type, Math.min(Storm.MAX_DIAMETER, big.diameter + small.diameter / 3),
                        (big.x * big.diameter + small.x * small.diameter) / (big.diameter + small.diameter), Math.min(big.y, small.y),
                        (big.z * big.diameter + small.z * small.diameter) / (big.diameter + small.diameter));
                m.mix = small.type == big.type ? null : small.type;
                m.age = big.age;
                m.structures = big.structures + small.structures;
                m.life(Math.max(big.life(), small.life())); // a merged storm doesn't outlast the longer-lived of the two
                m.setHeading(level.random.nextFloat() * net.minecraft.util.Mth.TWO_PI);
                storms.remove(j);
                storms.set(i, m);
                StormHooks.merge(level, m, a, b);
                for (ServerPlayer p : level.players())
                    if (m.dist(p.getX(), p.getZ()) < m.zone() * 2) level.playSound(null, p.blockPosition(), ModSounds.MERGE.get(), net.minecraft.sounds.SoundSource.AMBIENT, 1.5f, 1);
                return true;
            }
        }
        return false;
    }

    private void dryStreams(ServerLevel level) {
        long now = level.getGameTime();
        streams.removeIf(st -> {
            if (st[1] > now) return false;
            BlockPos pos = BlockPos.of(st[0]);
            if (!level.isLoaded(pos)) return false; // later, once it's loaded again
            if (level.getFluidState(pos).isSource() && level.getBlockState(pos).is(Blocks.WATER)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            return true;
        });
        setDirty();
    }

    /** A storm brings vanilla thunder weather; it clears up a few minutes after the last storm is gone. */
    private static void startThunder(ServerLevel level) {
        level.setWeatherParameters(0, 6000, true, true);
    }







    public static void send(net.minecraft.world.entity.player.Player player) {
        if (player instanceof ServerPlayer p) com.horizonsunbound.platform.Services.NETWORK.toPlayer(p, payload(get(p.serverLevel())));
    }

    private void sync(ServerLevel level) {
        com.horizonsunbound.platform.Services.NETWORK.toDimension(level, payload(this));
    }

    // copies: in single player the packet object reaches the client as is, and these keep ticking on the server
    private static StormSyncPayload payload(StormManager m) {
        List<Storm> all = new ArrayList<>(m.storms.size() + m.omens.size());
        for (Storm s : m.storms) all.add(s.copy());
        for (Storm s : m.omens) all.add(s.copy());
        return new StormSyncPayload(all);
    }

    private static StormManager load(CompoundTag tag) {
        StormManager m = new StormManager();
        for (Tag t : tag.getList("Storms", Tag.TAG_COMPOUND)) {
            Storm st = Storm.load((CompoundTag) t);
            (st.omen > 0 ? m.omens : m.storms).add(st); // omens are saved with their countdown
        }
        for (long a : tag.getLongArray("Anchors")) m.anchors.add(a);
        m.calm = tag.contains("Calm") ? tag.getInt("Calm") : -1;
        m.twinIn = tag.contains("TwinIn") ? tag.getInt("TwinIn") : -1;
        for (Tag t : tag.getList("Streams", Tag.TAG_LONG_ARRAY)) {
            long[] st = ((LongArrayTag) t).getAsLongArray();
            if (st.length == 2) m.streams.add(st);
        }
        return m;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Storm s : storms) list.add(s.save());
        for (Storm s : omens) list.add(s.save());
        tag.put("Storms", list);
        tag.putLongArray("Anchors", anchors.toLongArray());
        tag.putInt("Calm", calm);
        tag.putInt("TwinIn", twinIn);
        ListTag st = new ListTag();
        for (long[] s : streams) st.add(new LongArrayTag(s));
        tag.put("Streams", st);
        return tag;
    }
}
