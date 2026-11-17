package io.github.tryrodave.jsonextended.client;

import io.github.tryrodave.jsonextended.JsonExtended;
import io.github.tryrodave.jsonextended.dev.DevContent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class JsonExtendedClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (JsonExtended.devContentEnabled())
            DevContent.registerModels();
    }
}
