package io.github.tryrodave.jsonextended.model;

import io.github.tryrodave.jsonextended.JsonExtended;
import io.github.tryrodave.jsonextended.obj.ObjException;
import io.github.tryrodave.jsonextended.obj.ObjModelJson;
import io.github.tryrodave.jsonextended.obj.ObjModels;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.block.model.BlockModelDispatcher;
import org.useless.dragonfly.data.block.mojang.BlockModelMojangData;
import org.useless.dragonfly.models.block.StaticBlockModel;

/**
 * Loads block and item model JSON for this mod's blocks and items: checks the file and its parents first
 * ({@link ModelCheck}) and logs each problem once, in one line naming the model and key, then lets BTA's own reader
 * build the model. Files BTA's reader would crash on (broken JSON, a face without a texture) or that don't exist
 * give the missing model instead.
 */
@Environment(EnvType.CLIENT)
public final class Models {
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();
    private static final ModelCheck CHECK = new ModelCheck(Models::read);

    private Models() {}

    /** A model by id ({@code ns:block/name} or {@code ns:item/name}), or BTA's missing model when it can't be read. */
    public static StaticBlockModel load(String id) {
        String json = read(id);
        if (json != null && ObjModelJson.isObj(json)) {
            try {
                return ObjModels.load(id, json, Models::readFile, line -> log(new ModelCheck.Problem(false, line)));
            } catch (ObjException | UncheckedIOException e) {
                log(new ModelCheck.Problem(true, e.getMessage()));
                log(new ModelCheck.Problem(true, id + ": drawing the missing model instead"));
                return missing();
            }
        }
        List<ModelCheck.Problem> problems = CHECK.check(id);
        for (ModelCheck.Problem problem : problems)
            log(problem);
        if (ModelCheck.unreadable(problems, id)) {
            log(new ModelCheck.Problem(true, id + ": drawing the missing model instead"));
            return missing();
        }
        return BlockModelDispatcher.loadDataModel(id).asModel();
    }

    /**
     * BTA's missing model, freshly compiled. Call it only when a model is actually missing, and keep the result
     * for that model: each compile logs eight "does not have an assigned texture" lines from BTA, because its
     * missing model has no textures map. A fresh one per reload picks up the rebuilt atlas.
     */
    public static StaticBlockModel missing() {
        return BlockModelMojangData.Cache.getMissingModel().asModel();
    }

    /**
     * BTA's chunk render pass for a model's {@code render_type}: 0 for {@code solid} and the {@code cutout} kinds
     * (BTA's first pass already handles cut-out textures), 1 for {@code translucent} (the blended pass); -1 when
     * the model and its parents don't set one.
     */
    public static int renderLayer(String id) {
        String type = CHECK.renderType(id);
        if (type == null)
            return -1;
        return type.equals("translucent") ? 1 : 0;
    }

    /** {@code ns:path} to the text of {@code /assets/ns/models/path.json}, highest texture pack first; null when absent. */
    public static String read(String id) {
        return readAsset(id, "models", ".json");
    }

    /** {@code ns:path/file.ext} to the text of {@code /assets/ns/path/file.ext} through the texture packs; null when absent. */
    public static String readFile(String id) {
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        return readPath("/assets/" + namespace + "/" + id.substring(colon + 1));
    }

    /** {@code ns:path} to the text of {@code /assets/ns/<folder>/path<suffix>} through the texture packs; null when absent. */
    public static String readAsset(String id, String folder, String suffix) {
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        return readPath("/assets/" + namespace + "/" + folder + "/" + id.substring(colon + 1) + suffix);
    }

    /** A resource path through the texture packs, highest first, then the mod jars; null when absent. */
    private static String readPath(String path) {
        InputStream in = null;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.texturePackList != null)
            in = mc.texturePackList.getResourceAsStream(path);
        if (in == null)
            in = Models.class.getResourceAsStream(path);
        if (in == null)
            return null;
        try (InputStream stream = in) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** One error or warning line, once per game session. */
    public static void logOnce(boolean error, String line) {
        log(new ModelCheck.Problem(error, line));
    }

    /** Each distinct line once per game session, so reloads don't repeat it. */
    private static void log(ModelCheck.Problem problem) {
        if (!LOGGED.add(problem.toString()))
            return;
        if (problem.error())
            JsonExtended.LOGGER.error("Model {}", problem.message());
        else
            JsonExtended.LOGGER.warn("Model {}", problem.message());
    }
}
