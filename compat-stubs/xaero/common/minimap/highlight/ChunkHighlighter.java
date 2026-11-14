package xaero.common.minimap.highlight;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.hud.minimap.info.render.compile.InfoDisplayCompiler;

public abstract class ChunkHighlighter extends AbstractHighlighter {
    protected ChunkHighlighter(boolean coveringOutsideDiscovered) { super(coveringOutsideDiscovered); }
    protected abstract int[] getColors(ResourceKey<Level> dimension, int chunkX, int chunkZ);
    @Override public int[] getChunkHighlitColor(ResourceKey<Level> dimension, int chunkX, int chunkZ) { return null; }
    @Override public void addBlockHighlightTooltips(InfoDisplayCompiler compiler, ResourceKey<Level> dimension, int x, int z, int width) {}
    public abstract void addChunkHighlightTooltips(InfoDisplayCompiler compiler, ResourceKey<Level> dimension, int chunkX, int chunkZ, int width);
}
