package com.evirapo.diversityofcritters.common.block;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class DOCBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, DiversityOfCritters.MODID);

    public static final RegistryObject<Block> BOWL = BLOCKS.register("bowl",
            () -> new BowlBlock(
                    BlockBehaviour.Properties
                            .copy(Blocks.OAK_PLANKS)
                            .noOcclusion()
            )
    );

    public static final RegistryObject<Block> DIG_BOX = BLOCKS.register("dig_box",
            () -> new DigBoxBlock(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS).noOcclusion()));

    public static final RegistryObject<Block> AIR_CONDITIONER = BLOCKS.register("air_conditioner",
            () -> new AirConditionerBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).strength(3.5F)));

    // Same hardness as the furnace (3.5); both are tagged mineable/pickaxe in the block tag datagen.
    public static final RegistryObject<Block> HUMIDIFIER = BLOCKS.register("humidifier",
            () -> new HumidifierBlock(BlockBehaviour.Properties.copy(Blocks.PRISMARINE).strength(3.5F)));

    // A puddle animals drink from; generated in the world like a plant (see worldgen/placed_feature).
    public static final RegistryObject<Block> WATERHOLE = BLOCKS.register("waterhole",
            () -> new WaterholeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WATER)
                    .noCollission()
                    .noOcclusion()
                    .strength(0.3F)
                    .sound(SoundType.MUD)
                    .pushReaction(PushReaction.DESTROY)));

    // What a predator's kill leaves: a food source for meat eaters that rots away. No item.
    public static final RegistryObject<Block> CORPSE = BLOCKS.register("corpse",
            () -> new CorpseBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN)
                    .noCollission()
                    .noOcclusion()
                    .strength(0.5F)
                    .sound(SoundType.MUD)
                    .pushReaction(PushReaction.DESTROY)));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}

