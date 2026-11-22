package io.github.tryrodave.jsonextended.gltf;

import io.github.tryrodave.jsonextended.obj.ObjQuad;
import io.github.tryrodave.jsonextended.obj.ObjStaticModel;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.useless.dragonfly.models.block.StaticBlockModel;

/** Builds the block model for a model JSON with {@code "loader": "jsonextended:gltf"}; drawn like an OBJ model. */
@Environment(EnvType.CLIENT)
public final class GltfModels {
    private GltfModels() {}

    /**
     * @param id     the model JSON's id, for messages
     * @param json   its text
     * @param source file id ({@code ns:path/file.glb}) to bytes through the texture packs, or null when absent
     * @param log    gets each warning line
     * @throws GltfException when the model can't be built; the message names the file and key
     */
    public static StaticBlockModel load(String id, String json, Function<String, byte[]> source, Consumer<String> log) {
        GltfModelJson settings = GltfModelJson.parse(id, json);
        settings.warnings().forEach(log);
        GltfModel model = GltfModel.load(settings.model(), source);
        model.warnings().forEach(log);
        List<ObjQuad> quads = settings.place(model.quads());
        for (String material : settings.unsetMaterials(quads))
            log.accept(id + ": textures." + material + ": not set, so material '" + material + "' draws the particle texture");
        if (settings.particle() == null)
            log.accept(id + ": textures.particle: not set, so particles and faces without a texture draw nothing");
        return new ObjStaticModel(settings, quads);
    }
}
