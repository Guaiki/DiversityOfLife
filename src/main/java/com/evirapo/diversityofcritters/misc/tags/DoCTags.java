package com.evirapo.diversityofcritters.misc.tags;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;

public class DoCTags {

    public static class Items {

        public static final TagKey<Item> MEATS = create("meats");

        public static final TagKey<Item> SQUIRREL_FOOD = create("squirrel_food");

        public static final TagKey<Item> SIFAKA_FOOD = create("sifaka_food");

        private static TagKey<Item> create(String pName) {
            return TagKey.create(Registries.ITEM, new ResourceLocation(DiversityOfCritters.MODID, pName));
        }
    }

    public static class Blocks {

        public static final TagKey<Block> CIVET_CLIMBABLE = create("civet_climbable");

        /**
         * Ground blocks the mod's animals are allowed to spawn on.
         *
         * It exists because the vanilla tag {@code #minecraft:animals_spawnable_on} only contains
         * grass_block, which would keep the squirrel from spawning in podzol taigas.
         */
        public static final TagKey<Block> CRITTERS_SPAWNABLE_ON = create("critters_spawnable_on");

        private static TagKey<Block> create(String pName) {
            return TagKey.create(Registries.BLOCK, new ResourceLocation(DiversityOfCritters.MODID, pName));
        }
    }

    public static class Biomes {

        public static final TagKey<Biome> CIVET_BIOMES = create("is_civet_biome");

        public static final TagKey<Biome> SIFAKA_BIOMES = create("is_sifaka_biome");

        public static final TagKey<Biome> SQUIRREL_BIOMES = create("is_squirrel_biome");

        private static TagKey<Biome> create(String pName) {
            return TagKey.create(Registries.BIOME, new ResourceLocation(DiversityOfCritters.MODID, pName));
        }
    }

}
