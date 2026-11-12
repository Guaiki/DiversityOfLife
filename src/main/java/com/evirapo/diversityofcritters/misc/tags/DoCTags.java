package com.evirapo.diversityofcritters.misc.tags;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.List;

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

        /** Blocks critters drink from besides water, e.g. the waterhole. */
        public static final TagKey<Block> DRINKABLE = create("drinkable");

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

    public static class EntityTypes {

        /** Vanilla livestock that self-breeds under the per-area cap: cow, pig, sheep, chicken. */
        public static final TagKey<EntityType<?>> LIVESTOCK = create("livestock");

        // Prey categories: every animal belongs to one, and each predator hunts some of them.
        // Datapacks can move animals between categories by editing these tags.
        public static final TagKey<EntityType<?>> SMALL_RODENT = create("category/small_rodent");
        public static final TagKey<EntityType<?>> SMALL_CARNIVORA = create("category/small_carnivora");
        public static final TagKey<EntityType<?>> SMALL_PRIMATE = create("category/small_primate");
        public static final TagKey<EntityType<?>> LAGOMORPH = create("category/lagomorph");
        public static final TagKey<EntityType<?>> SMALL_BIRD = create("category/small_bird");

        /** Every prey category, in this order; an animal's category is the first one it is in. */
        public static final List<TagKey<EntityType<?>>> CATEGORIES =
                List.of(SMALL_RODENT, SMALL_CARNIVORA, SMALL_PRIMATE, LAGOMORPH, SMALL_BIRD);

        /** The prey category of {@code type}, or null if it has none. */
        @Nullable
        public static TagKey<EntityType<?>> categoryOf(EntityType<?> type) {
            for (TagKey<EntityType<?>> category : CATEGORIES) {
                if (type.is(category)) return category;
            }
            return null;
        }

        private static TagKey<EntityType<?>> create(String pName) {
            return TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(DiversityOfCritters.MODID, pName));
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
