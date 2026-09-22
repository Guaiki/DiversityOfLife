package com.evirapo.diversityofcritters.client.renderer;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.client.models.CivetBabyModel;
import com.evirapo.diversityofcritters.client.models.CivetModel;
import com.evirapo.diversityofcritters.client.models.ModelLayers;
import com.evirapo.diversityofcritters.common.entity.custom.CivetEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class CivetRenderer<T extends CivetEntity> extends MobRenderer<T, HierarchicalModel<T>> {

    private static final ResourceLocation ADULT_TEXTURE = new ResourceLocation(DiversityOfCritters.MODID,"textures/entity/civet/civet_gray.png");
    private static final ResourceLocation BABY_TEXTURE = new ResourceLocation(DiversityOfCritters.MODID,"textures/entity/civet/baby_civet.png");

    private final HierarchicalModel<T> adultModel;
    private final HierarchicalModel<T> babyModel;

    public CivetRenderer(EntityRendererProvider.Context pContext) {
        super(pContext, new CivetModel<>(pContext.bakeLayer(ModelLayers.CIVET_LAYER)), 0.25F);
        this.adultModel = this.getModel();
        this.babyModel = new CivetBabyModel<>(pContext.bakeLayer(ModelLayers.CIVET_BABY_LAYER));
    }

    @Override
    public void render(@NotNull T entity, float entityYaw, float partialTicks, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight) {
        if (entity.isNewborn()) {
            this.model = this.babyModel;
            this.shadowRadius = 0.15f;
        } else {
            this.model = this.adultModel;
            this.shadowRadius = 0.25f;
        }

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    protected void scale(@NotNull T entity, PoseStack poseStack, float partialTickTime) {
        final float scale = scaleFor(entity);
        poseStack.scale(scale, scale, scale);
        super.scale(entity, poseStack, partialTickTime);
    }

    /** Growth-stage scale; also read by the locomotion rig to place bones in the world. */
    public static float scaleFor(CivetEntity entity) {
        if (entity.isNewborn()) {
            return 0.45F;
        }
        return entity.isJuvenile() ? 0.65F : 1.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(@NotNull CivetEntity entity) {
        if (entity.isNewborn()) {
            return BABY_TEXTURE;
        }
        return ADULT_TEXTURE;
    }
}