package com.evirapo.diversityofcritters.gametest;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.common.block.DOCBlocks;
import com.evirapo.diversityofcritters.common.block.entity.BowlBlockEntity;
import com.evirapo.diversityofcritters.common.entity.DOCEntities;
import com.evirapo.diversityofcritters.common.entity.custom.CivetEntity;
import com.evirapo.diversityofcritters.common.temperature.AirConditionerManager;
import com.evirapo.diversityofcritters.common.temperature.TemperatureTier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Seeking comfort: an animal outside its temperature/humidity range walks to a better spot.
 *
 * <p>The climate is faked with air-conditioner areas. A freezing area covers the whole arena and a
 * MODERATE one covers a corner (the nearest area wins). Areas reach past the arena, so these tests
 * get a batch of their own: no other test's animal ever stands in this cold.
 */
@GameTestHolder(DiversityOfCritters.MODID)
@PrefixGameTestTemplate(false)
public final class SeekComfortGameTests {

    private static final String ARENA = "mantle_arena";
    private static final String BATCH = "seek_comfort";
    private static final BlockPos COLD_CENTER = new BlockPos(5, 3, 5);
    private static final BlockPos WARM_CORNER = new BlockPos(9, 3, 9);

    private SeekComfortGameTests() {
    }

    /** Midnight: the civet is nocturnal, so it stays awake and sleep never hides the walk. */
    private static void floor(GameTestHelper helper) {
        helper.setDayTime(18000);
        for (int x = 1; x <= 10; x++) {
            for (int z = 1; z <= 10; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
                helper.setBlock(x, 2, z, Blocks.STONE);
            }
        }
    }

    /**
     * The fake climate, removed again shortly before the timeout too: a test that fails never runs
     * its own clean-up, and a leftover freezing area would chill whatever later batch reuses this
     * spot in the world.
     */
    private static void climate(GameTestHelper helper, boolean warmCorner, int timeoutTicks) {
        helper.runAfterDelay(timeoutTicks - 2, () -> {
            AirConditionerManager.remove(helper.getLevel(), helper.absolutePos(COLD_CENTER));
            AirConditionerManager.remove(helper.getLevel(), helper.absolutePos(WARM_CORNER));
        });
        AirConditionerManager.add(helper.getLevel(), helper.absolutePos(COLD_CENTER), TemperatureTier.FREEZING, 12);
        if (warmCorner) {
            AirConditionerManager.add(helper.getLevel(), helper.absolutePos(WARM_CORNER), TemperatureTier.MODERATE, 4);
        }
    }

    private static void cleanUp(GameTestHelper helper) {
        AirConditionerManager.remove(helper.getLevel(), helper.absolutePos(COLD_CENTER));
        AirConditionerManager.remove(helper.getLevel(), helper.absolutePos(WARM_CORNER));
        helper.killAllEntities();
    }

    @GameTest(template = ARENA, timeoutTicks = 800, batch = BATCH)
    public static void aFreezingCivetWalksToTheWarmCorner(GameTestHelper helper) {
        floor(helper);
        climate(helper, true, 800);
        CivetEntity civet = helper.spawn(DOCEntities.CIVET.get(), new Vec3(2.5, 3, 2.5));

        helper.succeedWhen(() -> {
            helper.assertTrue(civet.climateScoreAt(civet.blockPosition()) == 0,
                    "waiting for the civet to reach comfort, score " + civet.climateScoreAt(civet.blockPosition()));
            cleanUp(helper);
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 800, batch = BATCH)
    public static void aWanderingPetSeeksComfortToo(GameTestHelper helper) {
        floor(helper);
        climate(helper, true, 800);
        CivetEntity civet = helper.spawn(DOCEntities.CIVET.get(), new Vec3(2.5, 3, 2.5));
        civet.setTame(true);
        civet.setWandering(true);

        helper.succeedWhen(() -> {
            helper.assertTrue(civet.climateScoreAt(civet.blockPosition()) == 0,
                    "waiting for the pet to reach comfort, score " + civet.climateScoreAt(civet.blockPosition())
                            + " tame " + civet.isTame() + " wandering " + civet.isWandering()
                            + " sit " + civet.isOrderedToSit() + " sleep " + civet.getSleepState()
                            + " at " + helper.relativePos(civet.blockPosition())
                            + " navDone " + civet.getNavigation().isDone());
            cleanUp(helper);
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 400, batch = BATCH)
    public static void aSittingPetStaysPutInTheCold(GameTestHelper helper) {
        floor(helper);
        climate(helper, true, 400);
        CivetEntity civet = helper.spawn(DOCEntities.CIVET.get(), new Vec3(2.5, 3, 2.5));
        civet.setTame(true);
        civet.setWandering(false);
        civet.setOrderedToSit(true);
        Vec3 start = civet.position();

        helper.onEachTick(() -> {
            if (civet.position().distanceTo(start) > 1.0D) helper.fail("the sitting pet moved away");
        });
        helper.runAfterDelay(300, () -> {
            cleanUp(helper);
            helper.succeed();
        });
    }

    /** Freezing everywhere: no better spot, so the search backs off and the civet still eats. */
    @GameTest(template = ARENA, timeoutTicks = 600, batch = BATCH)
    public static void withNoBetterSpotTheCivetStillEats(GameTestHelper helper) {
        floor(helper);
        climate(helper, false, 600);
        BlockPos bowl = new BlockPos(7, 3, 5);
        helper.setBlock(bowl, DOCBlocks.BOWL.get());
        BowlBlockEntity bowlBe = (BowlBlockEntity) helper.getBlockEntity(bowl);
        bowlBe.getInventory().setItem(0, new ItemStack(Items.BEEF, 1));
        CivetEntity civet = helper.spawn(DOCEntities.CIVET.get(), new Vec3(3.5, 3, 5.5));
        civet.setHunger(civet.maxHunger() / 10);

        helper.succeedWhen(() -> {
            helper.assertTrue(bowlBe.getInventory().isEmpty(), "waiting for the cold civet to eat");
            cleanUp(helper);
        });
    }
}
