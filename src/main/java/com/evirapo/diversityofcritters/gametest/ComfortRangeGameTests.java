package com.evirapo.diversityofcritters.gametest;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.common.entity.DOCEntities;
import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.evirapo.diversityofcritters.common.humidity.HumidityTier;
import com.evirapo.diversityofcritters.common.temperature.TemperatureTier;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Each species' comfort ranges, as the client asked for them (module 5 of the quote).
 */
@GameTestHolder(DiversityOfCritters.MODID)
@PrefixGameTestTemplate(false)
public final class ComfortRangeGameTests {

    private static final String ARENA = "mantle_arena";

    private ComfortRangeGameTests() {
    }

    private static void check(GameTestHelper helper, EntityType<? extends DiverseCritter> type, String name,
                              TemperatureTier tempMin, TemperatureTier tempMax,
                              HumidityTier humidityMin, HumidityTier humidityMax) {
        DiverseCritter critter = helper.spawn(type, new Vec3(5.5, 3, 5.5));
        helper.assertTrue(critter.comfortTierMin() == tempMin, name + " temperature min: " + critter.comfortTierMin());
        helper.assertTrue(critter.comfortTierMax() == tempMax, name + " temperature max: " + critter.comfortTierMax());
        helper.assertTrue(critter.comfortHumidityMin() == humidityMin, name + " humidity min: " + critter.comfortHumidityMin());
        helper.assertTrue(critter.comfortHumidityMax() == humidityMax, name + " humidity max: " + critter.comfortHumidityMax());
    }

    @GameTest(template = ARENA)
    public static void comfortRangesMatchTheQuote(GameTestHelper helper) {
        for (int x = 1; x <= 10; x++) {
            for (int z = 1; z <= 10; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
                helper.setBlock(x, 2, z, Blocks.STONE);
            }
        }

        check(helper, DOCEntities.SQUIRREL.get(), "squirrel",
                TemperatureTier.FREEZING, TemperatureTier.WARM, HumidityTier.ARID, HumidityTier.SATURATED);
        check(helper, DOCEntities.CIVET.get(), "civet",
                TemperatureTier.COOL, TemperatureTier.HOT, HumidityTier.MODERATE, HumidityTier.WET);
        check(helper, DOCEntities.SIFAKA.get(), "sifaka",
                TemperatureTier.MODERATE, TemperatureTier.HOT, HumidityTier.ARID, HumidityTier.MODERATE);

        helper.killAllEntities();
        helper.succeed();
    }
}
