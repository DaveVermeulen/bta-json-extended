package io.github.tryrodave.jsonextended.animation;

import io.github.tryrodave.jsonextended.JsonExtended;
import io.github.tryrodave.jsonextended.model.Models;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Loads Bedrock animation files through the texture packs: {@code ns:path} is
 * {@code /assets/ns/animations/path.animation.json}. Load again after a pack change (tile entity renderers are
 * made new on every reload, so loading in a renderer does that).
 */
@Environment(EnvType.CLIENT)
public final class Animations {
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    private Animations() {}

    /** The file, or an empty file (and one error line per session) when it's missing or broken. */
    public static AnimationFile load(String id) {
        String text = Models.readAsset(id, "animations", ".animation.json");
        if (text == null) {
            logOnce("Animation file " + id + " not found (assets/<namespace>/animations/<path>.animation.json)");
            return AnimationFile.parse(id, "{\"animations\": {}}");
        }
        try {
            AnimationFile file = AnimationFile.parse(id, text);
            for (String warning : file.warnings())
                logOnce("Animation " + warning);
            return file;
        } catch (AnimationException e) {
            logOnce("Animation " + e.getMessage());
            return AnimationFile.parse(id, "{\"animations\": {}}");
        }
    }

    private static void logOnce(String line) {
        if (LOGGED.add(line))
            JsonExtended.LOGGER.warn(line);
    }
}
