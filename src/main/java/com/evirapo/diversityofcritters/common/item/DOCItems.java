package com.evirapo.diversityofcritters.common.item;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.common.block.DOCBlocks;
import com.evirapo.diversityofcritters.common.entity.DOCEntities;
import com.evirapo.diversityofcritters.common.item.custom.BrushItem;
import com.evirapo.diversityofcritters.common.item.custom.GauzeBandageItem;
import com.evirapo.diversityofcritters.common.item.custom.MedicinePillItem;
import com.evirapo.diversityofcritters.common.item.custom.TranquilizerRifleItem;
import com.evirapo.diversityofcritters.misc.tags.DoCTags;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class DOCItems {
    public static final DeferredRegister<Item> ITEM_TYPES =
        DeferredRegister.create(ForgeRegistries.ITEMS, DiversityOfCritters.MODID);

    public static final RegistryObject<Item> LION_SPAWN_EGG = ITEM_TYPES.register("lion_spawn_egg",
            ()-> new ForgeSpawnEggItem(DOCEntities.LION, 0xffffff, 0xffffff, new Item.Properties()));

    public static final RegistryObject<Item> CIVET_SPAWN_EGG = ITEM_TYPES.register("civet_spawn_egg",
            ()-> new ForgeSpawnEggItem(DOCEntities.CIVET, 0xffffff, 0xffffff, new Item.Properties()));

    public static final RegistryObject<Item> SQUIRREL_SPAWN_EGG = ITEM_TYPES.register("squirrel_spawn_egg",
            ()-> new ForgeSpawnEggItem(DOCEntities.SQUIRREL, 0xffffff, 0xffffff, new Item.Properties()));

    public static final RegistryObject<Item> SIFAKA_SPAWN_EGG = ITEM_TYPES.register("sifaka_spawn_egg",
            ()-> new ForgeSpawnEggItem(DOCEntities.SIFAKA, 0xffffff, 0xffffff, new Item.Properties()));

    public static final RegistryObject<Item> ZOO_BOOK = ITEM_TYPES.register("zoo_book",
            ()-> new Item(new Item.Properties()));

    public static final RegistryObject<Item> ENTITY_TAB_ICON = ITEM_TYPES.register("entity_tab_icon",
            ()-> new Item(new Item.Properties()));

    public static final RegistryObject<Item> TRAINING_STICK = ITEM_TYPES.register("training_stick",
            ()-> new Item(new Item.Properties()));

    // Meat of each prey category, what a corpse gives. Placeholder textures until the client's arrive.
    private static final FoodProperties CATEGORY_MEAT = new FoodProperties.Builder()
            .nutrition(2).saturationMod(0.3F).meat().build();

    public static final RegistryObject<Item> SMALL_RODENT_MEAT = ITEM_TYPES.register("small_rodent_meat",
            () -> new Item(new Item.Properties().food(CATEGORY_MEAT)));
    public static final RegistryObject<Item> SMALL_CARNIVORA_MEAT = ITEM_TYPES.register("small_carnivora_meat",
            () -> new Item(new Item.Properties().food(CATEGORY_MEAT)));
    public static final RegistryObject<Item> SMALL_PRIMATE_MEAT = ITEM_TYPES.register("small_primate_meat",
            () -> new Item(new Item.Properties().food(CATEGORY_MEAT)));
    public static final RegistryObject<Item> LAGOMORPH_MEAT = ITEM_TYPES.register("lagomorph_meat",
            () -> new Item(new Item.Properties().food(CATEGORY_MEAT)));
    public static final RegistryObject<Item> SMALL_BIRD_MEAT = ITEM_TYPES.register("small_bird_meat",
            () -> new Item(new Item.Properties().food(CATEGORY_MEAT)));

    /** The meat of a prey category; small rodent meat for an unknown or missing one. */
    public static Item meatFor(@javax.annotation.Nullable net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> category) {
        if (category == null) return SMALL_RODENT_MEAT.get();
        if (category.equals(DoCTags.EntityTypes.SMALL_CARNIVORA)) return SMALL_CARNIVORA_MEAT.get();
        if (category.equals(DoCTags.EntityTypes.SMALL_PRIMATE)) return SMALL_PRIMATE_MEAT.get();
        if (category.equals(DoCTags.EntityTypes.LAGOMORPH)) return LAGOMORPH_MEAT.get();
        if (category.equals(DoCTags.EntityTypes.SMALL_BIRD)) return SMALL_BIRD_MEAT.get();
        return SMALL_RODENT_MEAT.get();
    }

    public static final RegistryObject<Item> WATERHOLE = ITEM_TYPES.register("waterhole",
            () -> new BlockItem(DOCBlocks.WATERHOLE.get(), new Item.Properties()));

    public static final RegistryObject<Item> BOWL = ITEM_TYPES.register("bowl",
            () -> new BlockItem(DOCBlocks.BOWL.get(), new Item.Properties()));

    public static final RegistryObject<Item> DIG_BOX = ITEM_TYPES.register("dig_box",
            () -> new BlockItem(DOCBlocks.DIG_BOX.get(), new Item.Properties()));

    public static final RegistryObject<Item> AIR_CONDITIONER = ITEM_TYPES.register("air_conditioner",
            () -> new BlockItem(DOCBlocks.AIR_CONDITIONER.get(), new Item.Properties()));

    public static final RegistryObject<Item> HUMIDIFIER = ITEM_TYPES.register("humidifier",
            () -> new BlockItem(DOCBlocks.HUMIDIFIER.get(), new Item.Properties()));

    public static final RegistryObject<Item> BRUSH = ITEM_TYPES.register("brush",
            () -> new BrushItem(new Item.Properties().stacksTo(1).durability(64)));

    public static final RegistryObject<Item> GAUZE_BANDAGE = ITEM_TYPES.register("gauze_bandage",
            () -> new GauzeBandageItem(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Item> EMPTY_NURSER_BOTTLE = ITEM_TYPES.register("empty_nurser_bottle",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> FILLED_NURSER_BOTTLE = ITEM_TYPES.register("filled_nurser_bottle",
            () -> new Item(new Item.Properties().durability(5)));

    public static final RegistryObject<Item> WORM = ITEM_TYPES.register("worm",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(3)
                    .saturationMod(0.1f).build())));

    public static final RegistryObject<Item> TRANQUILIZER_RIFLE = ITEM_TYPES.register("tranquilizer_rifle",
            () -> new TranquilizerRifleItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> TRANQUILIZER_DART = ITEM_TYPES.register("tranquilizer_dart",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> MEDICINE_PILL = ITEM_TYPES.register("medicine_pill",
            () -> new MedicinePillItem(new Item.Properties().stacksTo(16)));

    public static void register(IEventBus eventBus) {
        ITEM_TYPES.register(eventBus);
    }

}
