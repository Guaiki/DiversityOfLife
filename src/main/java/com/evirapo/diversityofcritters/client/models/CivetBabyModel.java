package com.evirapo.diversityofcritters.client.models;

import com.evirapo.diversityofcritters.common.entity.custom.CivetEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.darkblade.deluxelib.client.anim.AnimBlend;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

public class CivetBabyModel<T extends CivetEntity> extends HierarchicalModel<T> {

    private final ModelPart Bandedpalmcivetkit;

    public CivetBabyModel(ModelPart root) {
        this.Bandedpalmcivetkit = root.getChild("Bandedpalmcivetkit");
    }

    public static LayerDefinition createBodyLayer() {

        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition Bandedpalmcivetkit = partdefinition.addOrReplaceChild("Bandedpalmcivetkit", CubeListBuilder.create(), PartPose.offset(-0.0333F, 22.9167F, 0.1106F));

        PartDefinition Torse = Bandedpalmcivetkit.addOrReplaceChild("Torse", CubeListBuilder.create(), PartPose.offset(-0.1333F, -1.3333F, -0.0578F));

        PartDefinition Head = Torse.addOrReplaceChild("Head", CubeListBuilder.create().texOffs(0, 7).addBox(-1.5F, -1.5F, -2.9083F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
                .texOffs(12, 11).addBox(-0.5F, 0.5F, -4.9083F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.1667F, -0.5833F, -2.6444F));

        PartDefinition bone = Head.addOrReplaceChild("bone", CubeListBuilder.create(), PartPose.offset(1.5F, -0.5F, -1.4083F));
        bone.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(12, 16).addBox(0.0F, -1.0F, -0.5F, 0.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.7854F));

        PartDefinition bone3 = Head.addOrReplaceChild("bone3", CubeListBuilder.create(), PartPose.offset(-1.5F, -0.5F, -1.4083F));
        bone3.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(14, 16).addBox(0.0F, -1.0F, -0.5F, 0.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.7854F));

        Head.addOrReplaceChild("eye1", CubeListBuilder.create().texOffs(14, 4).addBox(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(1.025F, 0.0F, -2.4333F));
        Head.addOrReplaceChild("eye2", CubeListBuilder.create().texOffs(12, 14).addBox(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-1.025F, 0.0F, -2.4333F));

        Torse.addOrReplaceChild("bone4", CubeListBuilder.create().texOffs(12, 7).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.1667F, -0.0833F, 2.4472F));
        Torse.addOrReplaceChild("bone2", CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.1667F, -0.0833F, -0.0528F));

        Bandedpalmcivetkit.addOrReplaceChild("bone5", CubeListBuilder.create().texOffs(0, 13).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.7833F, -1.9167F, -1.1106F));
        Bandedpalmcivetkit.addOrReplaceChild("bone6", CubeListBuilder.create().texOffs(4, 13).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.7167F, -1.9167F, -1.1106F));
        Bandedpalmcivetkit.addOrReplaceChild("bone7", CubeListBuilder.create().texOffs(14, 0).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.7167F, -1.9167F, 0.8894F));
        Bandedpalmcivetkit.addOrReplaceChild("bone8", CubeListBuilder.create().texOffs(8, 13).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.7833F, -1.9167F, 0.8894F));

        return LayerDefinition.create(meshdefinition, 32, 32);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);

        // Una sola llamada, a la CAPA 1. El bebé tiene su propia jerarquía de huesos (la raíz se
        // llama Bandedpalmcivetkit), así que sus clips viven en una capa aparte de los del adulto y
        // cada modelo lee sólo la suya: cero contaminación cruzada y ningún gate isNewborn()
        // repartido por el modelo. La prioridad decide qué se ve — dormir gana al llanto, y el
        // llanto a gatear.
        AnimBlend.applyBlended(this, entity.animator(), ageInTicks, 200L, 1);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        Bandedpalmcivetkit.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public ModelPart root() {
        return Bandedpalmcivetkit;
    }
}