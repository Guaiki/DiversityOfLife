package com.evirapo.diversityofcritters.client.locomotion;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * What a model must tell the locomotion rig so it can place bones in the world exactly where they
 * are drawn.
 */
@OnlyIn(Dist.CLIENT)
public interface ILocomotionModel<T extends Entity> {

    /**
     * The transform {@code renderToBuffer} applies before drawing {@code root()}. The model must
     * call this very method from {@code renderToBuffer}, so the rig and the renderer can never
     * disagree. Must not push or pop.
     */
    void applyRootTransform(PoseStack poseStack);

    /** The uniform scale the renderer's {@code scale(...)} applies for this entity. */
    float rendererScale(T entity);
}
