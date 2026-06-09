package com.evirapo.diversityofcritters.client.renderer;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.client.models.*;
import com.evirapo.diversityofcritters.common.entity.custom.CivetEntity;
import com.evirapo.diversityofcritters.common.entity.custom.SquirrelEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class SquirrelRenderer <T extends SquirrelEntity> extends MobRenderer<T, HierarchicalModel<T>> {

    private static final ResourceLocation ADULT_TEXTURE = new ResourceLocation(DiversityOfCritters.MODID,"textures/entity/squirrel/squirrel.png");
    private static final ResourceLocation BABY_TEXTURE = new ResourceLocation(DiversityOfCritters.MODID,"textures/entity/squirrel/baby_squirrel.png");

    private final HierarchicalModel<T> adultModel;
    private final HierarchicalModel<T> babyModel;

    public SquirrelRenderer(EntityRendererProvider.Context pContext) {
        super(pContext, new SquirrelModel<>(pContext.bakeLayer(ModelLayers.SQUIRREL_LAYER)), 0.25F);
        this.adultModel = this.getModel();
        this.babyModel = new SquirrelBabyModel<>(pContext.bakeLayer(ModelLayers.SQUIRREL_BABY_LAYER));
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
        if (entity.isNewborn()) {
            poseStack.scale(0.45F, 0.45F, 0.45F);
        } else if (entity.isJuvenile()) {
            poseStack.scale(0.65F, 0.65F, 0.65F);
        } else {
            poseStack.scale(1.0F, 1.0F, 1.0F);
        }
        super.scale(entity, poseStack, partialTickTime);
    }

    @Override
    public ResourceLocation getTextureLocation(@NotNull SquirrelEntity entity) {
        if (entity.isNewborn()) {
            return BABY_TEXTURE;
        }
        return ADULT_TEXTURE;
    }
}
