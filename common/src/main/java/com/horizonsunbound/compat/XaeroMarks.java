package com.horizonsunbound.compat;

import com.horizonsunbound.platform.Services;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.hud.minimap.info.render.compile.InfoDisplayCompiler;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Storms on Xaero's maps, as highlighted chunks (the way Xaero draws land claims): the storm's circle filled with its
 * glow, a brighter edge. With Xaero's World Map installed it is registered there and the minimap shows the World
 * Map's highlights; with the minimap alone, in the minimap. Registered from XaeroMinimapHighlightsMixin and
 * XaeroWorldMapHighlightsMixin while Xaero sets up its highlighters; touched only when Xaero is installed.
 */
public final class XaeroMarks {
    private static final int FILL = 0x50, EDGE = 0xC0; // opacity of the inside and of the circle's edge
    private static boolean minimapListening, worldMapListening;

    private XaeroMarks() {}

    /** True when the World Map draws the storms (and the minimap takes them from it). */
    public static boolean worldMapPresent() { return Services.PLATFORM.isModLoaded("xaeroworldmap"); }

    /** Xaero's color order (as for claims): blue, green, red, alpha from the top byte down. */
    private static int xaeroColor(int rgb, int alpha) {
        return (rgb & 255) << 24 | (rgb >> 8 & 255) << 16 | (rgb >> 16 & 255) << 8 | alpha;
    }

    /** Center, then the four sides: the edge where the neighbouring chunk is outside the storm. */
    static int[] colors(ResourceKey<Level> dimension, int chunkX, int chunkZ) { // a fresh array: Xaero asks from its own threads
        MapMarks.Mark m = MapMarks.at(dimension, chunkX, chunkZ);
        if (m == null) return null;
        int[] out = new int[5];
        int fill = xaeroColor(m.rgb(), FILL), edge = xaeroColor(m.rgb(), EDGE);
        out[0] = fill;
        out[1] = m.covers(chunkX, chunkZ - 1) ? fill : edge;
        out[2] = m.covers(chunkX + 1, chunkZ) ? fill : edge;
        out[3] = m.covers(chunkX, chunkZ + 1) ? fill : edge;
        out[4] = m.covers(chunkX - 1, chunkZ) ? fill : edge;
        return out;
    }

    private static Component tooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        MapMarks.Mark m = MapMarks.at(dimension, chunkX, chunkZ);
        return m == null ? null : m.name();
    }

    /** Xaero's Minimap without the World Map. */
    public static final class Minimap extends xaero.common.minimap.highlight.ChunkHighlighter {
        public Minimap() {
            super(true); // also over ground not yet explored: a storm ahead shows on the map
            if (!minimapListening) {
                minimapListening = true;
                com.mojang.logging.LogUtils.getLogger().info("Horizons Unbound: storms shown on Xaero's Minimap");
                MapMarks.listen((before, now) -> {
                    var session = xaero.hud.minimap.BuiltInHudModules.MINIMAP.getCurrentSession();
                    if (session == null) return;
                    var handler = session.getProcessor().getMinimapWriter().getDimensionHighlightHandler();
                    if (handler != null) handler.requestRefresh();
                });
            }
        }

        @Override public boolean regionHasHighlights(ResourceKey<Level> dimension, int regionX, int regionZ) { return MapMarks.inRegion(dimension, regionX, regionZ); }
        @Override public boolean chunkIsHighlit(ResourceKey<Level> dimension, int chunkX, int chunkZ) { return MapMarks.at(dimension, chunkX, chunkZ) != null; }
        @Override protected int[] getColors(ResourceKey<Level> dimension, int chunkX, int chunkZ) { return colors(dimension, chunkX, chunkZ); }
        @Override public void addChunkHighlightTooltips(InfoDisplayCompiler compiler, ResourceKey<Level> dimension, int chunkX, int chunkZ, int width) {}
    }

    /** Xaero's World Map (and through it the minimap). */
    public static final class WorldMap extends xaero.map.highlight.ChunkHighlighter {
        public WorldMap() {
            super(true);
            if (!worldMapListening) {
                worldMapListening = true;
                com.mojang.logging.LogUtils.getLogger().info("Horizons Unbound: storms shown on Xaero's World Map");
                MapMarks.listen((before, now) -> {
                    var session = xaero.map.WorldMapSession.getCurrentSession();
                    if (session == null) return;
                    Set<Long> regions = new HashSet<>();
                    for (List<MapMarks.Mark> list : List.of(before, now)) {
                        for (MapMarks.Mark m : list) {
                            var dimension = session.getMapProcessor().getMapWorld().getDimension(m.dimension());
                            if (dimension == null) continue;
                            for (int rx = m.minRegionX(); rx <= m.maxRegionX(); rx++)
                                for (int rz = m.minRegionZ(); rz <= m.maxRegionZ(); rz++)
                                    if (regions.add(((long) rx << 32) ^ (rz & 0xFFFFFFFFL))) dimension.getHighlightHandler().clearCachedHash(rx, rz);
                        }
                    }
                });
            }
        }

        @Override public int calculateRegionHash(ResourceKey<Level> dimension, int regionX, int regionZ) { return MapMarks.regionHash(dimension, regionX, regionZ); }
        @Override public boolean regionHasHighlights(ResourceKey<Level> dimension, int regionX, int regionZ) { return MapMarks.inRegion(dimension, regionX, regionZ); }
        @Override public boolean chunkIsHighlit(ResourceKey<Level> dimension, int chunkX, int chunkZ) { return MapMarks.at(dimension, chunkX, chunkZ) != null; }
        @Override protected int[] getColors(ResourceKey<Level> dimension, int chunkX, int chunkZ) { return colors(dimension, chunkX, chunkZ); }
        @Override public Component getChunkHighlightSubtleTooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) { return tooltip(dimension, chunkX, chunkZ); }
        @Override public Component getChunkHighlightBluntTooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) { return null; }
        @Override public void addMinimapBlockHighlightTooltips(List<Component> list, ResourceKey<Level> dimension, int x, int z, int width) {}
    }
}
