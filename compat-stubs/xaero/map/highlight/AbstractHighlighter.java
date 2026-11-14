package xaero.map.highlight;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;

public abstract class AbstractHighlighter {
    protected AbstractHighlighter(boolean coveringOutsideDiscovered) {}
    public abstract int calculateRegionHash(ResourceKey<Level> dimension, int regionX, int regionZ);
    public abstract boolean regionHasHighlights(ResourceKey<Level> dimension, int regionX, int regionZ);
    public abstract boolean chunkIsHighlit(ResourceKey<Level> dimension, int chunkX, int chunkZ);
    public abstract int[] getChunkHighlitColor(ResourceKey<Level> dimension, int chunkX, int chunkZ);
    public abstract Component getBlockHighlightSubtleTooltip(ResourceKey<Level> dimension, int x, int z);
    public abstract Component getBlockHighlightBluntTooltip(ResourceKey<Level> dimension, int x, int z);
    public abstract void addMinimapBlockHighlightTooltips(List<Component> list, ResourceKey<Level> dimension, int x, int z, int width);
}
