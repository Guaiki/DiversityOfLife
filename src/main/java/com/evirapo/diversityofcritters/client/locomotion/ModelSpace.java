package com.evirapo.diversityofcritters.client.locomotion;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.List;

/**
 * Maps points on posed bones to world coordinates, following the same chain of transforms a
 * {@code LivingEntityRenderer} applies when it draws the model:
 *
 * <pre>
 *   entity position
 *   rotateY(180 - bodyYaw)
 *   scale(-1, -1, 1)
 *   renderer scale
 *   translate(0, -1.501, 0)
 *   model root transform        ({@link ILocomotionModel#applyRootTransform})
 *   bone chain                  (each part's translateAndRotate)
 * </pre>
 *
 * <p>The matrices are kept relative to the entity and the position is added in double precision
 * at the end, so accuracy does not degrade far from the world origin. One instance per rig,
 * reused every frame; allocates nothing after construction.
 *
 * <p>Skipped on purpose: the renderer's sleeping, dying, spin-attack and shaking adjustments. The
 * rig stands down in all of those.
 */
@OnlyIn(Dist.CLIENT)
public final class ModelSpace {

    /** The renderer's vertical offset that puts model y = 24 units at the entity's feet. */
    static final float RENDER_OFFSET_Y = -1.501f;

    private final PoseStack base = new PoseStack();
    private final Matrix4f work = new Matrix4f();
    private final Vector3f point = new Vector3f();
    private double originX;
    private double originY;
    private double originZ;

    /** Prepares the transform for one entity this frame. */
    public <T extends LivingEntity> void begin(T entity, ILocomotionModel<T> model, float partialTick, float bodyYawDeg) {
        this.begin(Mth.lerp(partialTick, entity.xOld, entity.getX()),
                Mth.lerp(partialTick, entity.yOld, entity.getY()),
                Mth.lerp(partialTick, entity.zOld, entity.getZ()),
                bodyYawDeg, model.rendererScale(entity), model);
    }

    /** As {@link #begin(LivingEntity, ILocomotionModel, float, float)}, from raw values. */
    public void begin(double x, double y, double z, float bodyYawDeg, float rendererScale, ILocomotionModel<?> model) {
        this.originX = x;
        this.originY = y;
        this.originZ = z;

        // Built in place: PoseStack.scale/translate mutate the top pose without allocating.
        final PoseStack.Pose pose = this.base.last();
        pose.pose().identity().rotateY((float) Math.toRadians(180.0f - bodyYawDeg));
        pose.normal().identity();
        this.base.scale(-1.0f, -1.0f, 1.0f);
        this.base.scale(rendererScale, rendererScale, rendererScale);
        this.base.translate(0.0f, RENDER_OFFSET_Y, 0.0f);
        model.applyRootTransform(this.base);
    }

    /**
     * Model units per world block at the model root, for the entity passed to the last
     * {@link #begin}: 32 for the adult civet (drawn at half size), more for smaller growth stages.
     */
    public float modelUnitsPerBlock() {
        final Matrix4f m = this.base.last().pose();
        final float blocksPerRootUnit = (float) Math.sqrt(m.m00() * m.m00() + m.m01() * m.m01() + m.m02() * m.m02());
        return ModelParts.UNITS_PER_BLOCK / blocksPerRootUnit;
    }

    /**
     * World position of a point given in the frame of the last part of {@code chain}, in model
     * units. The chain must start at the model's {@code root()}.
     */
    public Vector3d toWorld(List<ModelPart> chain, float x, float y, float z, Vector3d out) {
        this.work.set(this.base.last().pose());
        for (int i = 0; i < chain.size(); i++) {
            applyPart(this.work, chain.get(i));
        }
        this.point.set(x / ModelParts.UNITS_PER_BLOCK, y / ModelParts.UNITS_PER_BLOCK, z / ModelParts.UNITS_PER_BLOCK);
        this.work.transformPosition(this.point);
        return out.set(this.originX + this.point.x, this.originY + this.point.y, this.originZ + this.point.z);
    }

    /**
     * The transform from the frame of the last part of {@code chain} to the world, relative to the
     * entity, as posed right now. Invert it to bring world points into that part's frame with
     * {@link #worldToFrame}. The chain must start at the model's {@code root()}.
     */
    public Matrix4f frameToWorld(List<ModelPart> chain, Matrix4f out) {
        out.set(this.base.last().pose());
        for (int i = 0; i < chain.size(); i++) {
            applyPart(out, chain.get(i));
        }
        return out;
    }

    /**
     * A world point in a part's frame, in model units. {@code worldToFrame} is the inverse of a
     * {@link #frameToWorld} matrix taken since the last {@link #begin}.
     */
    public Vector3f worldToFrame(Matrix4f worldToFrame, double x, double y, double z, Vector3f out) {
        out.set((float) (x - this.originX), (float) (y - this.originY), (float) (z - this.originZ));
        worldToFrame.transformPosition(out);
        return out.mul(ModelParts.UNITS_PER_BLOCK);
    }

    /** The same as {@link ModelPart#translateAndRotate}, applied to a bare matrix. */
    public static void applyPart(Matrix4f matrix, ModelPart part) {
        matrix.translate(part.x / ModelParts.UNITS_PER_BLOCK, part.y / ModelParts.UNITS_PER_BLOCK,
                part.z / ModelParts.UNITS_PER_BLOCK);
        if (part.xRot != 0.0f || part.yRot != 0.0f || part.zRot != 0.0f) {
            matrix.rotateZYX(part.zRot, part.yRot, part.xRot);
        }
        if (part.xScale != 1.0f || part.yScale != 1.0f || part.zScale != 1.0f) {
            matrix.scale(part.xScale, part.yScale, part.zScale);
        }
    }
}
