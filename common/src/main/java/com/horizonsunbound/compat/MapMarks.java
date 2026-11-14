package com.horizonsunbound.compat;

import com.horizonsunbound.Storm;
import com.horizonsunbound.StormType;
import com.horizonsunbound.client.ClientStorms;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * The storms as map mods show them (JourneyMap, Xaero's Minimap and World Map): each risen storm's footprint, the
 * column's circle, in its kind's glow color. Built from the client's storms once a second; when a footprint changes
 * (a storm rose, moved a few blocks, merged or died) the map integrations are told, so they redraw only then.
 * Nothing here touches a map mod's classes: each integration registers itself when its mod loads it.
 */
public final class MapMarks {
    /** One storm on the map: centre and radius in blocks, glow color (0xRRGGBB), the name shown with it. */
    public record Mark(UUID id, ResourceKey<Level> dimension, int x, int z, int radius, int rgb, StormType type, boolean grand) {
        public Component name() {
            return Component.translatable("map.horizons_unbound.storm." + (grand ? "grand" : type.id));
        }

        /** True if the chunk's centre lies within the storm's circle. */
        public boolean covers(int chunkX, int chunkZ) {
            long dx = chunkX * 16L + 8 - x, dz = chunkZ * 16L + 8 - z;
            return dx * dx + dz * dz <= (long) radius * radius;
        }

        public int minRegionX() { return Math.floorDiv(x - radius, 512); }
        public int maxRegionX() { return Math.floorDiv(x + radius, 512); }
        public int minRegionZ() { return Math.floorDiv(z - radius, 512); }
        public int maxRegionZ() { return Math.floorDiv(z + radius, 512); }
    }

    private static final int STEP = 4;   // positions are snapped to this many blocks: a slow storm redraws now and then, not every second
    private static volatile List<Mark> marks = List.of();
    // Where storms were before the last change, for a while: maps redraw a region only while it reports highlights
    // (Xaero's Minimap), so a storm that moved on or died is wiped out there too.
    private static volatile List<Mark> left = List.of();
    private static volatile int leftUntil;
    private static final List<BiConsumer<List<Mark>, List<Mark>>> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile int ticks;

    private MapMarks() {}

    /** The storms now (read from map mods' own threads too: an immutable snapshot). */
    public static List<Mark> marks() { return marks; }

    /** Called with (before, now) on the client thread whenever the footprints change. */
    public static void listen(BiConsumer<List<Mark>, List<Mark>> listener) { LISTENERS.add(listener); }

    /** The storm whose circle covers that chunk in that dimension, or null. */
    public static Mark at(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        for (Mark m : marks) if (m.dimension().equals(dimension) && m.covers(chunkX, chunkZ)) return m;
        return null;
    }

    /** True if any storm reaches into that 512-block region (or did until a few seconds ago). */
    public static boolean inRegion(ResourceKey<Level> dimension, int regionX, int regionZ) {
        return reaches(marks, dimension, regionX, regionZ) || ticks < leftUntil && reaches(left, dimension, regionX, regionZ);
    }

    private static boolean reaches(List<Mark> list, ResourceKey<Level> dimension, int regionX, int regionZ) {
        for (Mark m : list)
            if (m.dimension().equals(dimension) && regionX >= m.minRegionX() && regionX <= m.maxRegionX()
                    && regionZ >= m.minRegionZ() && regionZ <= m.maxRegionZ()) return true;
        return false;
    }

    /** Changes whenever what is drawn in that region changes. */
    public static int regionHash(ResourceKey<Level> dimension, int regionX, int regionZ) {
        int h = 1;
        for (Mark m : marks)
            if (m.dimension().equals(dimension) && regionX >= m.minRegionX() && regionX <= m.maxRegionX()
                    && regionZ >= m.minRegionZ() && regionZ <= m.maxRegionZ()) h = h * 31 + m.hashCode();
        return h;
    }

    /** Client tick: rebuilds the marks once a second, tells the integrations if they changed. */
    public static void tick() {
        if (LISTENERS.isEmpty() || ++ticks % 20 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        List<Mark> now = new ArrayList<>();
        if (mc.level != null) {
            for (Storm s : ClientStorms.all()) {
                int x = Math.round((float) s.x / STEP) * STEP, z = Math.round((float) s.z / STEP) * STEP;
                now.add(new Mark(s.id, mc.level.dimension(), x, z, Math.round(s.radius()), s.type.glow, s.type, s.grand));
            }
        }
        List<Mark> before = marks;
        if (now.equals(before)) return;
        marks = List.copyOf(now);
        left = before;
        leftUntil = ticks + 200;
        for (BiConsumer<List<Mark>, List<Mark>> l : LISTENERS) {
            try {
                l.accept(before, marks);
            } catch (RuntimeException | LinkageError e) { // a map mod changed under us: stop telling it, never break the game
                LISTENERS.remove(l);
                LogUtils.getLogger().warn("Horizons Unbound: a map integration failed and was turned off", e);
            }
        }
    }
}
