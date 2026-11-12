package com.evirapo.diversityofcritters.datagen.providers;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.common.item.DOCItems;
import com.evirapo.diversityofcritters.misc.tags.DoCTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class DoCItemTagGenerator extends ItemTagsProvider {
    public DoCItemTagGenerator(PackOutput p_275343_, CompletableFuture<HolderLookup.Provider> p_275729_, CompletableFuture<TagLookup<Block>> p_275322_, @Nullable ExistingFileHelper existingFileHelper) {
        super(p_275343_, p_275729_, p_275322_, DiversityOfCritters.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {

        this.tag(DoCTags.Items.MEATS).add(
                Items.RABBIT,
                Items.COOKED_RABBIT,
                Items.CHICKEN,
                Items.COOKED_CHICKEN,
                Items.BEEF,
                Items.COOKED_BEEF,
                Items.MUTTON,
                Items.COOKED_MUTTON,
                Items.PORKCHOP,
                Items.COOKED_PORKCHOP,
                DOCItems.SMALL_RODENT_MEAT.get(),
                DOCItems.SMALL_CARNIVORA_MEAT.get(),
                DOCItems.SMALL_PRIMATE_MEAT.get(),
                DOCItems.LAGOMORPH_MEAT.get(),
                DOCItems.SMALL_BIRD_MEAT.get()
        );

        this.tag(DoCTags.Items.SQUIRREL_FOOD).add(
                Items.APPLE,
                Items.SWEET_BERRIES,
                Items.GLOW_BERRIES,
                Items.MELON_SLICE,
                Items.PUMPKIN,
                Items.WHEAT_SEEDS,
                Items.PUMPKIN_SEEDS,
                Items.MELON_SEEDS,
                Items.BEETROOT_SEEDS,
                Items.BROWN_MUSHROOM,
                Items.RED_MUSHROOM
        );

        this.tag(DoCTags.Items.SIFAKA_FOOD).add(
                Items.OAK_LEAVES,
                Items.BIRCH_LEAVES,
                Items.SPRUCE_LEAVES,
                Items.JUNGLE_LEAVES,
                Items.ACACIA_LEAVES,
                Items.DARK_OAK_LEAVES,
                Items.AZALEA_LEAVES,
                Items.FLOWERING_AZALEA_LEAVES,
                Items.CHERRY_LEAVES,
                Items.MANGROVE_LEAVES,
                Items.APPLE,
                Items.SWEET_BERRIES,
                Items.GLOW_BERRIES,
                Items.MELON_SLICE,
                Items.DANDELION,
                Items.POPPY,
                Items.BLUE_ORCHID,
                Items.ALLIUM,
                Items.AZURE_BLUET,
                Items.RED_TULIP,
                Items.ORANGE_TULIP,
                Items.WHITE_TULIP,
                Items.PINK_TULIP,
                Items.OXEYE_DAISY,
                Items.CORNFLOWER,
                Items.LILY_OF_THE_VALLEY,
                Items.CARROT,
                Items.BEETROOT
        );
    }
}
