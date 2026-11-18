package io.github.tryrodave.jsonextended.obj;

import java.util.function.Consumer;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.useless.dragonfly.models.block.StaticBlockModel;

/** Builds the OBJ model for a model JSON with an OBJ {@code loader}. */
@Environment(EnvType.CLIENT)
public final class ObjModels {
    private ObjModels() {}

    /**
     * @param id     the model JSON's id, for messages
     * @param json   its text
     * @param source file id ({@code ns:path/file.obj}) to text through the texture packs, or null when absent
     * @param log    gets each warning line
     * @throws ObjException when the model can't be built; the message names the file and key or line
     */
    public static StaticBlockModel load(String id, String json, Function<String, String> source, Consumer<String> log) {
        ObjModelJson settings = ObjModelJson.parse(id, json);
        settings.warnings().forEach(log);
        ObjModel model = ObjModel.load(settings.model(), source, settings.mtlOverride());
        model.warnings().forEach(log);
        if (settings.particle() == null)
            log.accept(id + ": textures.particle: not set, so particles and faces without a texture draw nothing");
        return new ObjStaticModel(settings, model);
    }
}
