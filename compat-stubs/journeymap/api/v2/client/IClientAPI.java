package journeymap.api.v2.client;

import journeymap.api.v2.client.display.Displayable;
import journeymap.api.v2.common.CommonAPI;

public interface IClientAPI extends CommonAPI {
    void show(Displayable displayable) throws Exception;
    void remove(Displayable displayable);
    void removeAll(String modId);
}
