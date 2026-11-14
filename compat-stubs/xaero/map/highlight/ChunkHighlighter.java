package xaero.map.highlight;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public abstract class ChunkHighlighter extends AbstractHighlighter {
    protected ChunkHighlighter(boolean coveringOutsideDiscovered) { super(coveringOutsideDiscovered); }
    protected abstract int[] getColors(ResourceKey<Level> dimension, int chunkX, int chunkZ);
    @Override public int[] getChunkHighlitColor(ResourceKey<Level> dimension, int chunkX, int chunkZ) { return null; }
    @Override public Component getBlockHighlightBluntTooltip(ResourceKey<Level> dimension, int x, int z) { return null; }
    @Override public Component getBlockHighlightSubtleTooltip(ResourceKey<Level> dimension, int x, int z) { return null; }
    public abstract Component getChunkHighlightSubtleTooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ);
    public abstract Component getChunkHighlightBluntTooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ);
}
