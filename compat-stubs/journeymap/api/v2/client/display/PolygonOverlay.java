package journeymap.api.v2.client.display;

import journeymap.api.v2.client.model.MapPolygon;
import journeymap.api.v2.client.model.ShapeProperties;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class PolygonOverlay extends Overlay {
    public PolygonOverlay(String modId, ResourceKey<Level> dimension, ShapeProperties shapeProperties, MapPolygon polygon) { super(modId); }
    public PolygonOverlay setOuterArea(MapPolygon polygon) { return this; }
    public PolygonOverlay setShapeProperties(ShapeProperties shapeProperties) { return this; }
}
