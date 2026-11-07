package com.horizonsunbound;

import com.horizonsunbound.effect.StormEffects;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The storms of one dimension, saved with the world. Ticked every server tick whatever chunks are loaded,
 * and sent to every player in the dimension once a second (a few dozen bytes per storm) or right away on a change.
 */
@EventBusSubscriber(modid = HorizonsUnbound.MODID)
public final class StormManager extends SavedData {
    public static final int ANCHOR_RADIUS = 8;
    public static final int OMEN_TICKS = 1200; // a natural storm's omens show for a minute before it rises
    private final List<Storm> storms = new ArrayList<>();
    private final List<Storm> omens = new ArrayList<>(); // storms about to rise
    private final LongOpenHashSet anchors = new LongOpenHashSet();  // reality anchors in this dimension
    private final List<long[]> streams = new ArrayList<>();         // {water source pos, game time it dries up}
    private boolean resync;

    public static StormManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(StormManager::new, StormManager::load), "horizons_unbound");
    }

    public List<Storm> storms() { return Collections.unmodifiableList(storms); }

    public void add(ServerLevel level, Storm storm) {
        storms.add(storm);
        if (level.dimension() == Level.OVERWORLD) startThunder(level);
        setDirty();
        sync(level);
    }

    /** A natural storm: first a minute of omens (dark sky, rising wind, nervous animals), then it rises. */
    public void announce(ServerLevel level, Storm storm) {
        storm.omen = OMEN_TICKS;
        omens.add(storm);
        setDirty();
        sync(level);
    }

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
        storms.clear();
        omens.clear();
        setDirty();
        sync(level);
        return n;
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        StormManager m = get(level);
        if (!m.streams.isEmpty() && level.getGameTime() % 20 == 0) m.dryStreams(level);
        if (m.quiet()) return;
        boolean ended = m.tickOmens(level);
        steer(m.storms);
        ended |= m.storms.removeIf(s -> !s.tick(level));
        ended |= m.merge(level);
        for (Storm s : m.storms) StormEffects.tick(level, m, s);
        if (ended || m.resync || level.getGameTime() % 20 == 0) {
            m.resync = false;
            if (!m.storms.isEmpty() && level.dimension() == Level.OVERWORLD) startThunder(level);
            m.setDirty();
            m.sync(level);
        }
    }

    /** Counts omens down and lets them rise; meanwhile animals near the coming storm get restless. */
    private boolean tickOmens(ServerLevel level) {
        if (omens.isEmpty()) return false;
        boolean rose = false;
        for (java.util.Iterator<Storm> it = omens.iterator(); it.hasNext(); ) {
            Storm o = it.next();
            if (--o.omen <= 0) {
                it.remove();
                o.omen = 0;
                storms.add(o);
                if (level.dimension() == Level.OVERWORLD) startThunder(level);
                rose = true;
            } else if (o.omen % 20 == 0) StormEffects.restless(level, o);
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
                if (a.mix != null || b.mix != null || a.dist(b.x, b.z) > (a.radius() + b.radius()) * .45f) continue;
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

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) { send(event.getEntity()); }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) { send(event.getEntity()); }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) { send(event.getEntity()); }

    private static void send(net.minecraft.world.entity.player.Player player) {
        if (player instanceof ServerPlayer p) PacketDistributor.sendToPlayer(p, payload(get(p.serverLevel())));
    }

    private void sync(ServerLevel level) {
        PacketDistributor.sendToPlayersInDimension(level, payload(this));
    }

    // copies: in single player the packet object reaches the client as is, and these keep ticking on the server
    private static StormSyncPayload payload(StormManager m) {
        List<Storm> all = new ArrayList<>(m.storms.size() + m.omens.size());
        for (Storm s : m.storms) all.add(s.copy());
        for (Storm s : m.omens) all.add(s.copy());
        return new StormSyncPayload(all);
    }

    private static StormManager load(CompoundTag tag, HolderLookup.Provider registries) {
        StormManager m = new StormManager();
        for (Tag t : tag.getList("Storms", Tag.TAG_COMPOUND)) {
            Storm st = Storm.load((CompoundTag) t);
            (st.omen > 0 ? m.omens : m.storms).add(st); // omens are saved with their countdown
        }
        for (long a : tag.getLongArray("Anchors")) m.anchors.add(a);
        for (Tag t : tag.getList("Streams", Tag.TAG_LONG_ARRAY)) {
            long[] st = ((LongArrayTag) t).getAsLongArray();
            if (st.length == 2) m.streams.add(st);
        }
        return m;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Storm s : storms) list.add(s.save());
        for (Storm s : omens) list.add(s.save());
        tag.put("Storms", list);
        tag.putLongArray("Anchors", anchors.toLongArray());
        ListTag st = new ListTag();
        for (long[] s : streams) st.add(new LongArrayTag(s));
        tag.put("Streams", st);
        return tag;
    }
}
