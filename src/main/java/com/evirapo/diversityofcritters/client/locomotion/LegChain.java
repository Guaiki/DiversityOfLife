package com.evirapo.diversityofcritters.client.locomotion;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * The bones of one leg, from hip to tip, plus where its sole is.
 *
 * <p>The sole is the lowest point of the tip bone's geometry, measured once at bind time in the
 * tip's own frame: {@link #soleOffset()} units down the tip's local Y. The footprint is the extent
 * of that geometry across X and Z, which is the patch of ground the foot stands on.
 */
@OnlyIn(Dist.CLIENT)
public final class LegChain {

    private final List<ModelPart> bones;
    private final List<ModelPart> fromRoot;
    private final float soleOffset;
    private final float footMinX;
    private final float footMaxX;
    private final float footMinZ;
    private final float footMaxZ;

    private LegChain(List<ModelPart> bones, List<ModelPart> fromRoot, float[] tipBounds) {
        this.bones = Collections.unmodifiableList(bones);
        this.fromRoot = Collections.unmodifiableList(fromRoot);
        this.footMinX = tipBounds[0];
        this.footMaxX = tipBounds[1];
        this.soleOffset = tipBounds[3];
        this.footMinZ = tipBounds[4];
        this.footMaxZ = tipBounds[5];
    }

    /**
     * Resolves a leg by bone names, hip first. Null if any bone is missing, unreachable from the
     * model root, or the tip has no geometry to stand on.
     */
    public static @Nullable LegChain of(HierarchicalModel<?> model, String... boneNames) {
        if (boneNames.length == 0) {
            return null;
        }
        final List<ModelPart> bones = new ArrayList<>(boneNames.length);
        for (String name : boneNames) {
            final Optional<ModelPart> bone = model.getAnyDescendantWithName(name);
            if (bone.isEmpty()) {
                return null;
            }
            bones.add(bone.get());
        }

        final ModelPart tip = bones.get(bones.size() - 1);
        final List<ModelPart> fromRoot = ModelParts.chain(model.root(), tip);
        final float[] tipBounds = ModelParts.localBounds(tip);
        if (fromRoot == null || tipBounds == null) {
            return null;
        }
        return new LegChain(bones, fromRoot, tipBounds);
    }

    public ModelPart hip() {
        return this.bones.get(0);
    }

    public ModelPart tip() {
        return this.bones.get(this.bones.size() - 1);
    }

    public List<ModelPart> bones() {
        return this.bones;
    }

    /** Model root down to the tip, both included. */
    public List<ModelPart> fromRoot() {
        return this.fromRoot;
    }

    /** Distance from the tip pivot to the sole, along the tip's local +Y, in model units. */
    public float soleOffset() {
        return this.soleOffset;
    }

    public float footMinX() {
        return this.footMinX;
    }

    public float footMaxX() {
        return this.footMaxX;
    }

    public float footMinZ() {
        return this.footMinZ;
    }

    public float footMaxZ() {
        return this.footMaxZ;
    }

    /**
     * Model-space Y of the sole as currently posed, in model units (+Y down), measured from the
     * model root. Allocates nothing: {@code scratch} is pushed and popped.
     */
    public float soleModelY(PoseStack scratch) {
        return modelY(scratch, this.fromRoot, this.soleOffset);
    }

    /**
     * Model-space Y, in model units, of the point {@code localY} units down the last part of
     * {@code chain}, with every part in its current pose.
     */
    public static float modelY(PoseStack scratch, List<ModelPart> chain, float localY) {
        scratch.pushPose();
        for (int i = 0; i < chain.size(); i++) {
            chain.get(i).translateAndRotate(scratch);
        }
        final Matrix4f pose = scratch.last().pose();
        // Transform (0, localY, 0) and keep only Y: column 1 times localY, plus the translation.
        final float y = pose.m11() * (localY / ModelParts.UNITS_PER_BLOCK) + pose.m31();
        scratch.popPose();
        return y * ModelParts.UNITS_PER_BLOCK;
    }
}
