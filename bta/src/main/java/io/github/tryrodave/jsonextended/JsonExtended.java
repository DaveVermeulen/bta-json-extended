package io.github.tryrodave.jsonextended;

import io.github.tryrodave.jsonextended.dev.DevContent;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import turniplabs.halplibe.HalpLibe;

/**
 * BTA JSON Extended: modern-Minecraft-style models on top of BTA's built-in JSON block models.
 */
public final class JsonExtended implements ModInitializer {
    /** {@code true} stitches {@code assets/jsonextended/textures/block/*} into the block atlas. */
    public static final String MOD_ID = HalpLibe.registerMod("jsonextended", true);
    public static final Logger LOGGER = LoggerFactory.getLogger("BTA JSON Extended");

    /** Test blocks are only registered in the development client. */
    public static boolean devContentEnabled() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public void onInitialize() {
        if (devContentEnabled())
            DevContent.registerBlocks();
    }
}
