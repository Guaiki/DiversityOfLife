package com.evirapo.diversityofcritters.client.renderer.layer;

import com.evirapo.diversityofcritters.client.models.SifakaModel;
import com.evirapo.diversityofcritters.common.entity.custom.SifakaEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class SifakaLeafLayer<T extends SifakaEntity, M extends HierarchicalModel<T>>
        extends RenderLayer<T, M> {

    public SifakaLeafLayer(RenderLayerParent<T, M> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int combinedLight,
                       T entity, float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {

        if (entity.isNewborn()) return;
        if (!entity.isEatingPlant()) return;

        ItemStack stack = entity.getEatingItem();
        if (stack.isEmpty()) return;

        M model = this.getParentModel();
        if (!(model instanceof SifakaModel<?> sifakaModel)) return;

        poseStack.pushPose();

        sifakaModel.translateToRightHand(poseStack);
        poseStack.scale(0.8f, 0.8f, 0.8f);

        Minecraft.getInstance().getItemRenderer().renderStatic(
                entity, stack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                false, poseStack, bufferSource,
                entity.level(), combinedLight, OverlayTexture.NO_OVERLAY,
                entity.getId()
        );

        poseStack.popPose();
    }
}
