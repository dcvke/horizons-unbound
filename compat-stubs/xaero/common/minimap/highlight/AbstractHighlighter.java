package xaero.common.minimap.highlight;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.hud.minimap.info.render.compile.InfoDisplayCompiler;

public abstract class AbstractHighlighter {
    protected AbstractHighlighter(boolean coveringOutsideDiscovered) {}
    public abstract boolean regionHasHighlights(ResourceKey<Level> dimension, int regionX, int regionZ);
    public abstract boolean chunkIsHighlit(ResourceKey<Level> dimension, int chunkX, int chunkZ);
    public abstract int[] getChunkHighlitColor(ResourceKey<Level> dimension, int chunkX, int chunkZ);
    public abstract void addBlockHighlightTooltips(InfoDisplayCompiler compiler, ResourceKey<Level> dimension, int x, int z, int width);
}
