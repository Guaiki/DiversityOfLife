package com.evirapo.diversityofcritters.client.locomotion;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Every part's pose under a root, saved so an animation applied on top can be faded back out.
 *
 * <p>Animations that blend on their own, with no weight to hand them, cannot be played at part
 * strength. Keyframes add to the pose, so capturing it just before and lerping back toward it
 * just after is the same as having played them at {@code 1 - amount}. Built once per model; the
 * capture and fade allocate nothing.
 */
@OnlyIn(Dist.CLIENT)
public final class PoseSnapshot {

    /** Offset, rotation and scale: x, y, z, xRot, yRot, zRot, xScale, yScale, zScale. */
    private static final int STRIDE = 9;

    private final List<ModelPart> parts;
    private final float[] saved;

    public PoseSnapshot(ModelPart root) {
        this.parts = root.getAllParts().toList();
        this.saved = new float[this.parts.size() * STRIDE];
    }

    public void capture() {
        for (int i = 0, o = 0; i < this.parts.size(); i++, o += STRIDE) {
            final ModelPart part = this.parts.get(i);
            this.saved[o] = part.x;
            this.saved[o + 1] = part.y;
            this.saved[o + 2] = part.z;
            this.saved[o + 3] = part.xRot;
            this.saved[o + 4] = part.yRot;
            this.saved[o + 5] = part.zRot;
            this.saved[o + 6] = part.xScale;
            this.saved[o + 7] = part.yScale;
            this.saved[o + 8] = part.zScale;
        }
    }

    /** Moves every part {@code amount} of the way back to its captured pose; 1 undoes all since. */
    public void fadeBack(float amount) {
        if (amount <= 0.0f) {
            return;
        }
        final float t = Math.min(amount, 1.0f);
        for (int i = 0, o = 0; i < this.parts.size(); i++, o += STRIDE) {
            final ModelPart part = this.parts.get(i);
            part.x = Mth.lerp(t, part.x, this.saved[o]);
            part.y = Mth.lerp(t, part.y, this.saved[o + 1]);
            part.z = Mth.lerp(t, part.z, this.saved[o + 2]);
            part.xRot = Mth.lerp(t, part.xRot, this.saved[o + 3]);
            part.yRot = Mth.lerp(t, part.yRot, this.saved[o + 4]);
            part.zRot = Mth.lerp(t, part.zRot, this.saved[o + 5]);
            part.xScale = Mth.lerp(t, part.xScale, this.saved[o + 6]);
            part.yScale = Mth.lerp(t, part.yScale, this.saved[o + 7]);
            part.zScale = Mth.lerp(t, part.zScale, this.saved[o + 8]);
        }
    }
}
