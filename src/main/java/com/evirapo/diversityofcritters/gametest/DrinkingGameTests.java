package com.evirapo.diversityofcritters.gametest;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.common.entity.DOCEntities;
import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * A thirsty critter drinks from water blocks, not only from bowls, headless:
 * {@code gradlew runGameTestServer}.
 *
 * <p>The arena is stone with the ground at y 3, walled in two blocks high so nobody wanders off.
 * Each species runs in a batch of its own, because the clock is global and each needs its waking
 * hours: the sifaka is diurnal, the civet nocturnal.
 */
@GameTestHolder(DiversityOfCritters.MODID)
@PrefixGameTestTemplate(false)
public final class DrinkingGameTests {

    private static final String ARENA = "mantle_arena";
    private static final int TIMEOUT = 600;
    private static final int NOON = 6000;
    private static final int MIDNIGHT = 18000;

    private DrinkingGameTests() {
    }

    private static void walledFloor(GameTestHelper helper) {
        for (int x = 1; x <= 10; x++) {
            for (int z = 1; z <= 10; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
                helper.setBlock(x, 2, z, Blocks.STONE);
                boolean edge = x == 1 || x == 10 || z == 1 || z == 10;
                helper.setBlock(x, 3, z, edge ? Blocks.STONE : Blocks.AIR);
                helper.setBlock(x, 4, z, edge ? Blocks.STONE : Blocks.AIR);
            }
        }
    }

    /** A natural bank: the water surface is a block below the ground, so the bank beside it is solid. */
    private static void pond(GameTestHelper helper) {
        walledFloor(helper);
        for (int x = 6; x <= 8; x++) {
            for (int z = 6; z <= 8; z++) {
                helper.setBlock(x, 2, z, Blocks.WATER);
            }
        }
    }

    private static <T extends DiverseCritter> void drinks(GameTestHelper helper, EntityType<T> type, int dayTime) {
        helper.setDayTime(dayTime);
        T critter = helper.spawn(type, new Vec3(3.5, 3, 3.5));
        critter.setThirst(critter.maxThirst() / 4);

        helper.succeedWhen(() -> {
            helper.assertTrue(critter.getThirst() == critter.maxThirst(), "waiting for it to drink");
            helper.killAllEntities();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT, batch = "drinking_sifaka")
    public static void aThirstySifakaDrinksFromAPond(GameTestHelper helper) {
        pond(helper);
        drinks(helper, DOCEntities.SIFAKA.get(), NOON);
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT, batch = "drinking_civet")
    public static void aThirstyCivetDrinksFromAPond(GameTestHelper helper) {
        pond(helper);
        drinks(helper, DOCEntities.CIVET.get(), MIDNIGHT);
    }
}
