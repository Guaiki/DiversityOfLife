package com.evirapo.diversityofcritters.gametest;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.common.entity.DOCEntities;
import com.evirapo.diversityofcritters.common.entity.custom.CivetEntity;
import com.evirapo.diversityofcritters.common.gamerule.DOCGameRules;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The critter gamerules with a real civet, headless: {@code gradlew runGameTestServer}.
 *
 * <p>Gamerules belong to the whole world, and the tests of one batch run at the same time. These
 * tests get a batch of their own, so no other test ever runs while a rule is off (a civet that is
 * meant to be hungry gets refilled by doCritterNeeds = false). Within the batch each test touches
 * only its own rule and turns it back on before passing.
 */
@GameTestHolder(DiversityOfCritters.MODID)
@PrefixGameTestTemplate(false)
public final class CritterGameRulesGameTests {

    private static final String ARENA = "mantle_arena";
    private static final Vec3 SPOT = new Vec3(5.5, 3, 5.5);
    private static final String BATCH = "gamerules";

    private CritterGameRulesGameTests() {
    }

    private static void floor(GameTestHelper helper) {
        for (int x = 1; x <= 10; x++) {
            for (int z = 1; z <= 10; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
                helper.setBlock(x, 2, z, Blocks.STONE);
            }
        }
    }

    private static void setRule(GameTestHelper helper, GameRules.Key<GameRules.BooleanValue> rule, boolean value) {
        helper.getLevel().getGameRules().getRule(rule).set(value, helper.getLevel().getServer());
    }

    /**
     * With needs on, a civet at zero hunger and thirst stays there. Only the gamerule can fill both
     * bars within the test's time.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void needsOffKeepsHungerAndThirstFull(GameTestHelper helper) {
        floor(helper);
        setRule(helper, DOCGameRules.CRITTER_NEEDS, false);
        CivetEntity civet = helper.spawn(DOCEntities.CIVET.get(), SPOT);
        civet.setHunger(0);
        civet.setThirst(0);

        helper.succeedWhen(() -> {
            helper.assertFalse(civet.needsEnabled(), "the civet must see doCritterNeeds as off");
            helper.assertTrue(civet.getHunger() == civet.maxHunger(), "hunger must be full");
            helper.assertTrue(civet.getThirst() == civet.maxThirst(), "thirst must be full");
            helper.assertTrue(civet.isAlive(), "the civet must not starve");
            setRule(helper, DOCGameRules.CRITTER_NEEDS, true);
            helper.killAllEntities();
        });
    }

    /**
     * The comfort bars start empty. Even in its comfort zone a bar refills at 4 points a second, 25
     * seconds from empty, so only the gamerule can fill it within the test's time.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void climateOffMakesEveryCritterComfortable(GameTestHelper helper) {
        floor(helper);
        setRule(helper, DOCGameRules.CRITTER_CLIMATE, false);
        CivetEntity civet = helper.spawn(DOCEntities.CIVET.get(), SPOT);
        civet.setTemperatureComfort(0);
        civet.setHumidityComfort(0);

        helper.succeedWhen(() -> {
            helper.assertFalse(civet.climateEnabled(), "the civet must see doCritterClimate as off");
            helper.assertTrue(civet.getTemperatureZone() == 0, "temperature zone must be comfort");
            helper.assertTrue(civet.getHumidityZone() == 0, "humidity zone must be comfort");
            helper.assertTrue(civet.getTemperatureComfort() == 100, "temperature bar must be full");
            helper.assertTrue(civet.getHumidityComfort() == 100, "humidity bar must be full");
            setRule(helper, DOCGameRules.CRITTER_CLIMATE, true);
            helper.killAllEntities();
        });
    }
}
