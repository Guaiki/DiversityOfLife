package com.evirapo.diversityofcritters.datagen.providers;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.misc.tags.DoCTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.BiomeTagsProvider;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biomes;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

public class DoCBiomeTagGenerator extends BiomeTagsProvider {
    public DoCBiomeTagGenerator(PackOutput pOutput, CompletableFuture<HolderLookup.Provider> pProvider, ExistingFileHelper existingFileHelper) {
        super(pOutput, pProvider, DiversityOfCritters.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider pProvider) {
        this.addTags();
    }

    protected void addTags() {
        // Banded palm civet: jungle.
        this.tag(DoCTags.Biomes.CIVET_BIOMES).addTags(BiomeTags.IS_JUNGLE);

        // Verreaux's sifaka: savanna.
        this.tag(DoCTags.Biomes.SIFAKA_BIOMES).addTags(BiomeTags.IS_SAVANNA);

        // Red and white giant flying squirrel: temperate forest and taiga.
        this.tag(DoCTags.Biomes.SQUIRREL_BIOMES).addTags(BiomeTags.IS_FOREST, BiomeTags.IS_TAIGA);
    }
}
