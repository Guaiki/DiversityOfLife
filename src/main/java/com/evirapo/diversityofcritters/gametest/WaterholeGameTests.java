package com.evirapo.diversityofcritters.gametest;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.common.block.DOCBlocks;
import com.evirapo.diversityofcritters.common.entity.DOCEntities;
import com.evirapo.diversityofcritters.common.entity.custom.CivetEntity;
import com.evirapo.diversityofcritters.common.temperature.AirConditionerManager;
import com.evirapo.diversityofcritters.common.temperature.TemperatureTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The waterhole: a puddle block critters drink from, which never dries up.
 */
@GameTestHolder(DiversityOfCritters.MODID)
@PrefixGameTestTemplate(false)
public final class WaterholeGameTests {

    private static final String ARENA = "mantle_arena";
    private static final BlockPos HOLE = new BlockPos(8, 3, 5);

    private WaterholeGameTests() {
    }

    private static void dryFloorWithAWaterhole(GameTestHelper helper) {
        for (int x = 1; x <= 10; x++) {
            for (int z = 1; z <= 10; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
                helper.setBlock(x, 2, z, Blocks.STONE);
            }
        }
        helper.setBlock(HOLE, DOCBlocks.WATERHOLE.get());
    }

    @GameTest(template = ARENA, timeoutTicks = 800)
    public static void aThirstyCivetDrinksFromAWaterhole(GameTestHelper helper) {
        dryFloorWithAWaterhole(helper);
        CivetEntity civet = helper.spawn(DOCEntities.CIVET.get(), new Vec3(3.5, 3, 5.5));
        civet.setThirst(civet.maxThirst() / 10);

        helper.succeedWhen(() -> {
            helper.assertTrue(civet.getThirstPercentage() > 50,
                    "waiting for the civet to drink, thirst " + civet.getThirstPercentage() + " %");
            helper.killAllEntities();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 700)
    public static void aWaterholeNeverDriesUp(GameTestHelper helper) {
        dryFloorWithAWaterhole(helper);
        BlockPos abs = helper.absolutePos(HOLE);
        AirConditionerManager.add(helper.getLevel(), abs, TemperatureTier.SCORCHING, 2);

        helper.runAfterDelay(600, () -> {
            AirConditionerManager.remove(helper.getLevel(), abs);
            helper.assertBlockPresent(DOCBlocks.WATERHOLE.get(), HOLE);
            helper.succeed();
        });
    }

    /** The world-generation feature, as {@code /place feature diversityofcritters:waterhole} runs it. */
    @GameTest(template = ARENA)
    public static void theWaterholeFeaturePlacesPuddlesOnGrass(GameTestHelper helper) {
        for (int x = 1; x <= 10; x++) {
            for (int z = 1; z <= 10; z++) {
                helper.setBlock(x, 1, z, Blocks.DIRT);
                helper.setBlock(x, 2, z, Blocks.GRASS_BLOCK);
            }
        }
        ConfiguredFeature<?, ?> feature = helper.getLevel().registryAccess()
                .registryOrThrow(Registries.CONFIGURED_FEATURE)
                .get(new ResourceLocation(DiversityOfCritters.MODID, "waterhole"));
        helper.assertTrue(feature != null, "configured feature diversityofcritters:waterhole is missing");

        boolean placed = false;
        for (int attempt = 0; attempt < 10 && !placed; attempt++) {
            placed = feature.place(helper.getLevel(), helper.getLevel().getChunkSource().getGenerator(),
                    helper.getLevel().getRandom(), helper.absolutePos(new BlockPos(5, 3, 5)));
        }
        helper.assertTrue(placed, "the feature placed nothing in 10 attempts");
        int count = 0;
        for (int x = 1; x <= 10; x++) {
            for (int z = 1; z <= 10; z++) {
                if (helper.getBlockState(new BlockPos(x, 3, z)).is(DOCBlocks.WATERHOLE.get())) count++;
            }
        }
        helper.assertTrue(count >= 1, "no waterhole on the grass floor");
        helper.succeed();
    }
}
