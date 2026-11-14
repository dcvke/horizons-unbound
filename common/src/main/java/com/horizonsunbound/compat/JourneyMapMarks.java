package com.horizonsunbound.compat;

import com.horizonsunbound.HorizonsUnbound;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.model.MapPolygon;
import journeymap.api.v2.client.model.ShapeProperties;
import journeymap.api.v2.common.JourneyMapPlugin;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Storms on JourneyMap (minimap, full map, web map): each storm's circle in its glow color, named on hover.
 * JourneyMap finds this class itself (the annotation on NeoForge and Forge, the "journeymap" entrypoint on Fabric);
 * without JourneyMap it is never loaded.
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public final class JourneyMapMarks implements IClientPlugin {
    private static final int SIDES = 48;
    private final Map<UUID, PolygonOverlay> shown = new HashMap<>();

    @Override public String getModId() { return HorizonsUnbound.MODID; }

    @Override
    public void initialize(IClientAPI api) {
        MapMarks.listen((before, now) -> {
            List<UUID> gone = new ArrayList<>(shown.keySet());
            for (MapMarks.Mark m : now) {
                gone.remove(m.id());
                PolygonOverlay old = shown.remove(m.id());
                if (old != null) api.remove(old);
                ShapeProperties shape = new ShapeProperties().setStrokeColor(m.rgb()).setStrokeOpacity(.9f).setStrokeWidth(2)
                        .setFillColor(m.rgb()).setFillOpacity(.25f);
                PolygonOverlay overlay = new PolygonOverlay(HorizonsUnbound.MODID, m.dimension(), shape, circle(m));
                overlay.setTitle(m.name().getString());
                try {
                    api.show(overlay);
                    shown.put(m.id(), overlay);
                } catch (Exception e) {
                    throw new IllegalStateException("JourneyMap refused a storm overlay", e);
                }
            }
            for (UUID id : gone) api.remove(shown.remove(id));
        });
    }

    private static MapPolygon circle(MapMarks.Mark m) {
        List<BlockPos> points = new ArrayList<>(SIDES);
        for (int i = 0; i < SIDES; i++) {
            float a = i * Mth.TWO_PI / SIDES;
            points.add(new BlockPos(m.x() + Math.round(m.radius() * Mth.cos(a)), 64, m.z() + Math.round(m.radius() * Mth.sin(a))));
        }
        return new MapPolygon(points);
    }
}
