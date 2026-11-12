package com.evirapo.diversityofcritters.datagen.providers;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.common.entity.DOCEntities;
import com.evirapo.diversityofcritters.misc.tags.DoCTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.EntityTypeTagsProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.common.data.ExistingFileHelper;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

public class DoCEntityTagGenerator extends EntityTypeTagsProvider {

    public DoCEntityTagGenerator(PackOutput p_256095_, CompletableFuture<HolderLookup.Provider> p_256572_, @Nullable ExistingFileHelper existingFileHelper) {
        super(p_256095_, p_256572_, DiversityOfCritters.MODID, existingFileHelper);
    }

    protected void addTags(HolderLookup.Provider pProvider) {
        this.tag(DoCTags.EntityTypes.LIVESTOCK)
                .add(EntityType.COW, EntityType.PIG, EntityType.SHEEP, EntityType.CHICKEN);

        this.tag(DoCTags.EntityTypes.SMALL_RODENT).add(DOCEntities.SQUIRREL.get());
        this.tag(DoCTags.EntityTypes.SMALL_CARNIVORA).add(DOCEntities.CIVET.get());
        this.tag(DoCTags.EntityTypes.SMALL_PRIMATE).add(DOCEntities.SIFAKA.get());
        this.tag(DoCTags.EntityTypes.LAGOMORPH).add(EntityType.RABBIT);
        this.tag(DoCTags.EntityTypes.SMALL_BIRD).add(EntityType.CHICKEN, EntityType.PARROT);
    }
}
