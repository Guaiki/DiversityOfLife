package com.evirapo.diversityofcritters.datagen.providers;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.common.block.DOCBlocks;
import com.evirapo.diversityofcritters.misc.tags.DoCTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class DoCBlockTagGenerator extends BlockTagsProvider {


    public DoCBlockTagGenerator(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, DiversityOfCritters.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        this.tag(DoCTags.Blocks.CIVET_CLIMBABLE)
                .addTag(BlockTags.LOGS);           // tree logs, the natural climbing surface

        // Spawn ground. Vanilla only allows grass_block for animals, which would leave out the
        // podzol of taigas and the coarse dirt of savannas.
        this.tag(DoCTags.Blocks.CRITTERS_SPAWNABLE_ON)
                .add(Blocks.GRASS_BLOCK,
                     Blocks.PODZOL,
                     Blocks.COARSE_DIRT,
                     Blocks.DIRT,
                     Blocks.ROOTED_DIRT,
                     Blocks.MOSS_BLOCK);

        this.tag(BlockTags.MINEABLE_WITH_AXE)
                .add(DOCBlocks.BOWL.get(),
                     DOCBlocks.DIG_BOX.get());

        // Machines break like a furnace: pickaxe, any tier.
        this.tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(DOCBlocks.AIR_CONDITIONER.get(),
                     DOCBlocks.HUMIDIFIER.get());
    }
}
