package com.horizonsunbound.fabric;
import com.horizonsunbound.client.ConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
public final class ModMenuIntegration implements ModMenuApi {
    public ConfigScreenFactory<?> getModConfigScreenFactory() { return ConfigScreen::new; }
}
