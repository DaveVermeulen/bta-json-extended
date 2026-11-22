package io.github.tryrodave.jsonextended.animation;

import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.MobRenderer;
import net.minecraft.core.entity.Mob;
import org.useless.dragonfly.models.entity.BoneTransform;
import org.useless.dragonfly.models.entity.StaticEntityModel;

/**
 * A mob renderer for a Bedrock geo model (BTA's own, from {@code models/entity/models.json}) that poses its bones
 * from a Bedrock animation file: an idle animation on the mob's age clock, blended into a walk animation by how
 * fast the mob walks. The walk animation's time follows the distance walked, so feet keep pace with the ground.
 * <p>
 * Values go into BTA's {@link BoneTransform}s as the file writes them (degrees to radians): BTA's geo renderer
 * applies Bedrock's axes itself. Override {@link #pose} to add or replace animations.
 */
@Environment(EnvType.CLIENT)
public class AnimatedMobRenderer<T extends Mob> extends MobRenderer<T> {
    /** Limb swing per vanilla walk cycle (BTA's legs use {@code cos(limbSwing * 0.6662)}). */
    public static final float VANILLA_WALK_CYCLE = (float) (2 * Math.PI / 0.6662);

    private final String animationFile;
    private final String idle;
    private final String walk;
    private final float walkCycle;
    private AnimationFile file;

    /**
     * @param animationFile id for {@link Animations#load}, e.g. {@code mymod:my_mob}
     * @param idle animation name, or null
     * @param walk animation name, or null; one loop of it plays per {@code walkCycle} of limb swing
     */
    public AnimatedMobRenderer(float shadowSize, String animationFile, String idle, String walk, float walkCycle) {
        super(shadowSize);
        this.animationFile = animationFile;
        this.idle = idle;
        this.walk = walk;
        this.walkCycle = walkCycle;
    }

    @Override
    protected StaticEntityModel getAndSetupModelForLayer(T mob, float brightness, float partialTick, int layer) {
        StaticEntityModel model = getModel("main");
        if (model == null)
            return null;
        model.resetBones();
        for (Map.Entry<String, float[]> e : pose(mob, partialTick).entrySet()) {
            BoneTransform t = model.getTransform(e.getKey());
            float[] v = e.getValue();
            t.rotX = Math.toRadians(v[0]);
            t.rotY = Math.toRadians(v[1]);
            t.rotZ = Math.toRadians(v[2]);
            t.posX = v[3];
            t.posY = v[4];
            t.posZ = v[5];
            t.scaleX = v[6];
            t.scaleY = v[7];
            t.scaleZ = v[8];
        }
        return model;
    }

    /** Bone values in the animation file's axes and units, as {@link Animation#raw} gives them. */
    protected Map<String, float[]> pose(T mob, float partialTick) {
        AnimationFile anims = animations();
        Animation idleAnim = idle == null ? null : anims.get(idle);
        Animation walkAnim = walk == null ? null : anims.get(walk);
        Map<String, float[]> still = idleAnim == null ? Map.of() : idleAnim.rawPose(getLimbPitch(mob, partialTick) / 20f);
        if (walkAnim == null)
            return still;
        float amount = Math.min(1f, getLimbYaw(mob, partialTick));
        if (amount <= 0)
            return still;
        Map<String, float[]> moving = walkAnim.rawPose(getLimbSwing(mob, partialTick) / walkCycle * walkAnim.length());
        return Animation.mix(still, moving, amount);
    }

    /** Read on first use; renderers are made new on every pack reload, so pack changes reach it. */
    protected AnimationFile animations() {
        if (file == null)
            file = Animations.load(animationFile);
        return file;
    }
}
