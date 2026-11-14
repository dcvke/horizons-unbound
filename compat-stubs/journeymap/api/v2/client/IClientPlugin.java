package journeymap.api.v2.client;

import journeymap.api.v2.common.IJourneyMapPlugin;

public interface IClientPlugin extends IJourneyMapPlugin<IClientAPI> {
    @Override void initialize(IClientAPI api);
}
