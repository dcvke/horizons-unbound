package xaero.hud.minimap.module;

import xaero.common.minimap.MinimapProcessor;
import xaero.hud.module.ModuleSession;

public abstract class MinimapSession extends ModuleSession<MinimapSession> {
    public MinimapProcessor getProcessor() { return null; }
}
