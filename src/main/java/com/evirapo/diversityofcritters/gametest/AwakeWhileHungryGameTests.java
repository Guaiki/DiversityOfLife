package com.evirapo.diversityofcritters.gametest;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.common.entity.DOCEntities;
import com.evirapo.diversityofcritters.common.entity.custom.CivetEntity;
import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter.SleepState;
import com.evirapo.diversityofcritters.common.temperature.AirConditionerManager;
import com.evirapo.diversityofcritters.common.temperature.TemperatureTier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Hungry critters stay awake, headless: {@code gradlew runGameTestServer}.
 *
 * <p>The civet is nocturnal and sleeps by day, so the world clock is set to noon, inside its sleep
 * window. Its hunger is set to a quarter: hungry, but far from starving to death during the test.
 */
@GameTestHolder(DiversityOfCritters.MODID)
@PrefixGameTestTemplate(false)
public final class AwakeWhileHungryGameTests {

    private static final String ARENA = "mantle_arena";
    private static final int NOON = 6000;
    private static final int TIMEOUT = 600;

    private AwakeWhileHungryGameTests() {
    }

    private static void floor(GameTestHelper helper) {
        for (int x = 1; x <= 10; x++) {
            for (int z = 1; z <= 10; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
                helper.setBlock(x, 2, z, Blocks.STONE);
            }
        }
    }

    private static void makeHungry(CivetEntity civet) {
        civet.setHunger(civet.maxHunger() / 4);
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void aHungryCivetDoesNotFallAsleep(GameTestHelper helper) {
        floor(helper);
        helper.setDayTime(NOON);
        CivetEntity fed = helper.spawn(DOCEntities.CIVET.get(), new Vec3(3.5, 3, 3.5));
        CivetEntity hungry = helper.spawn(DOCEntities.CIVET.get(), new Vec3(8.5, 3, 8.5));
        makeHungry(hungry);

        helper.onEachTick(() -> {
            if (hungry.getSleepState() != SleepState.AWAKE) {
                helper.fail("the hungry civet went to sleep: " + hungry.getSleepState());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(fed.getSleepState() == SleepState.SLEEPING, "waiting for the fed civet to sleep");
            helper.killAllEntities();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void aSleepingCivetWakesUpWhenItGetsHungry(GameTestHelper helper) {
        floor(helper);
        helper.setDayTime(NOON);
        CivetEntity civet = helper.spawn(DOCEntities.CIVET.get(), new Vec3(5.5, 3, 5.5));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(civet.getSleepState() == SleepState.SLEEPING,
                        "waiting for the civet to sleep"))
                .thenExecute(() -> makeHungry(civet))
                .thenWaitUntil(() -> helper.assertTrue(civet.getSleepState() == SleepState.AWAKE,
                        "waiting for the hungry civet to wake up"))
                .thenExecuteFor(60, () -> helper.assertTrue(civet.getSleepState() == SleepState.AWAKE,
                        "the hungry civet fell asleep again"))
                .thenExecute(helper::killAllEntities)
                .thenSucceed();
    }

    /**
     * Once the rested civet sleeps, the thirsty one is watched for longer than the whole settle
     * spread, so it cannot pass just because its own bedtime had not come yet.
     */
    @GameTest(template = ARENA, timeoutTicks = 1500)
    public static void aThirstyCivetDoesNotFallAsleep(GameTestHelper helper) {
        floor(helper);
        helper.setDayTime(NOON);
        CivetEntity rested = helper.spawn(DOCEntities.CIVET.get(), new Vec3(3.5, 3, 3.5));
        CivetEntity thirsty = helper.spawn(DOCEntities.CIVET.get(), new Vec3(8.5, 3, 8.5));
        thirsty.setThirst(thirsty.maxThirst() / 4);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(rested.getSleepState() == SleepState.SLEEPING,
                        "waiting for the rested civet to sleep"))
                .thenExecuteFor(700, () -> helper.assertTrue(thirsty.getSleepState() == SleepState.AWAKE,
                        "the thirsty civet went to sleep: " + thirsty.getSleepState()))
                .thenExecute(helper::killAllEntities)
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 1500)
    public static void aSleepingCivetWakesUpWhenItGetsThirsty(GameTestHelper helper) {
        floor(helper);
        helper.setDayTime(NOON);
        CivetEntity civet = helper.spawn(DOCEntities.CIVET.get(), new Vec3(5.5, 3, 5.5));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(civet.getSleepState() == SleepState.SLEEPING,
                        "waiting for the civet to sleep"))
                .thenExecute(() -> civet.setThirst(civet.maxThirst() / 4))
                .thenWaitUntil(() -> helper.assertTrue(civet.getSleepState() == SleepState.AWAKE,
                        "waiting for the thirsty civet to wake up"))
                .thenExecuteFor(60, () -> helper.assertTrue(civet.getSleepState() == SleepState.AWAKE,
                        "the thirsty civet fell asleep again"))
                .thenExecute(helper::killAllEntities)
                .thenSucceed();
    }

    /**
     * A freezing air conditioner where the civet lies down (it may wander a few blocks before it
     * settles): two tiers below its COOL minimum, danger.
     */
    @GameTest(template = ARENA, timeoutTicks = 1500)
    public static void aSleepingCivetWakesUpInClimateDanger(GameTestHelper helper) {
        floor(helper);
        helper.setDayTime(NOON);
        CivetEntity civet = helper.spawn(DOCEntities.CIVET.get(), new Vec3(5.5, 3, 5.5));
        BlockPos[] ac = new BlockPos[1];

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(civet.getSleepState() == SleepState.SLEEPING,
                        "waiting for the civet to sleep"))
                .thenExecute(() -> {
                    ac[0] = civet.blockPosition();
                    AirConditionerManager.add(helper.getLevel(), ac[0], TemperatureTier.FREEZING, 2);
                })
                .thenWaitUntil(() -> helper.assertTrue(civet.getSleepState() == SleepState.AWAKE,
                        "waiting for the freezing civet to wake up: " + civet.getSleepState()
                                + " tier " + civet.getTemperatureTier()))
                .thenExecute(() -> {
                    AirConditionerManager.remove(helper.getLevel(), ac[0]);
                    helper.killAllEntities();
                })
                .thenSucceed();
    }
}
