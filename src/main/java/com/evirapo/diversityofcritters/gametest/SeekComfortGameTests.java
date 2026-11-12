package com.evirapo.diversityofcritters.gametest;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.common.block.DOCBlocks;
import com.evirapo.diversityofcritters.common.block.entity.BowlBlockEntity;
import com.evirapo.diversityofcritters.common.entity.DOCEntities;
import com.evirapo.diversityofcritters.common.entity.custom.CivetEntity;
import com.evirapo.diversityofcritters.common.humidity.HumidifierManager;
import com.evirapo.diversityofcritters.common.humidity.HumidityTier;
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
     * Floor, fake climate and the civet. Everything is undone again shortly before the timeout too:
     * a failing test never runs its own clean-up, a leftover freezing area would chill whatever later
     * batch reuses this spot, and a civet that walked out of the arena is out of reach of
     * {@code killAllEntities()} and would wander into other tests.
     */
    private static CivetEntity setUp(GameTestHelper helper, boolean warmCorner, int timeoutTicks, Vec3 civetAt) {
        floor(helper);
        AirConditionerManager.add(helper.getLevel(), helper.absolutePos(COLD_CENTER), TemperatureTier.FREEZING, 12);
        // Humidity pinned to the civet's range, so only temperature varies (the biome under this
        // batch's spot changes from run to run, and a dry one left the civet in stress everywhere).
        HumidifierManager.add(helper.getLevel(), helper.absolutePos(COLD_CENTER), HumidityTier.HUMID, 16);
        if (warmCorner) {
            AirConditionerManager.add(helper.getLevel(), helper.absolutePos(WARM_CORNER), TemperatureTier.MODERATE, 4);
        }
        CivetEntity civet = helper.spawn(DOCEntities.CIVET.get(), civetAt);
        helper.runAfterDelay(timeoutTicks - 2, () -> cleanUp(helper, civet));
        return civet;
    }

    private static void cleanUp(GameTestHelper helper, CivetEntity civet) {
        AirConditionerManager.remove(helper.getLevel(), helper.absolutePos(COLD_CENTER));
        AirConditionerManager.remove(helper.getLevel(), helper.absolutePos(WARM_CORNER));
        HumidifierManager.remove(helper.getLevel(), helper.absolutePos(COLD_CENTER));
        civet.discard();
    }

    @GameTest(template = ARENA, timeoutTicks = 800, batch = BATCH)
    public static void aFreezingCivetWalksToTheWarmCorner(GameTestHelper helper) {
        CivetEntity civet = setUp(helper, true, 800, new Vec3(2.5, 3, 2.5));

        helper.succeedWhen(() -> {
            helper.assertTrue(civet.climateScoreAt(civet.blockPosition()) == 0,
                    "waiting for the civet to reach comfort, score " + civet.climateScoreAt(civet.blockPosition()));
            cleanUp(helper, civet);
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 800, batch = BATCH)
    public static void aWanderingPetSeeksComfortToo(GameTestHelper helper) {
        CivetEntity civet = setUp(helper, true, 800, new Vec3(2.5, 3, 2.5));
        civet.setTame(true);
        civet.setWandering(true);

        helper.succeedWhen(() -> {
            helper.assertTrue(civet.climateScoreAt(civet.blockPosition()) == 0,
                    "waiting for the pet to reach comfort, score " + civet.climateScoreAt(civet.blockPosition())
                            + " tame " + civet.isTame() + " wandering " + civet.isWandering()
                            + " sit " + civet.isOrderedToSit() + " sleep " + civet.getSleepState()
                            + " at " + helper.relativePos(civet.blockPosition())
                            + " navDone " + civet.getNavigation().isDone());
            cleanUp(helper, civet);
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 400, batch = BATCH)
    public static void aSittingPetStaysPutInTheCold(GameTestHelper helper) {
        CivetEntity civet = setUp(helper, true, 400, new Vec3(2.5, 3, 2.5));
        civet.setTame(true);
        civet.setWandering(false);
        civet.setOrderedToSit(true);
        Vec3 start = civet.position();

        helper.onEachTick(() -> {
            if (civet.position().distanceTo(start) > 1.0D) helper.fail("the sitting pet moved away");
        });
        helper.runAfterDelay(300, () -> {
            cleanUp(helper, civet);
            helper.succeed();
        });
    }

    /** Hungry in freezing danger with food at hand: it gets its grace to eat before it leaves. */
    @GameTest(template = ARENA, timeoutTicks = 600, batch = BATCH)
    public static void aHungryCivetInDangerEatsBeforeLeaving(GameTestHelper helper) {
        CivetEntity civet = setUp(helper, false, 600, new Vec3(3.5, 3, 5.5));
        BlockPos bowl = new BlockPos(7, 3, 5);
        helper.setBlock(bowl, DOCBlocks.BOWL.get());
        BowlBlockEntity bowlBe = (BowlBlockEntity) helper.getBlockEntity(bowl);
        bowlBe.getInventory().setItem(0, new ItemStack(Items.BEEF, 1));
        civet.setHunger(civet.maxHunger() / 10);

        helper.succeedWhen(() -> {
            helper.assertTrue(bowlBe.getInventory().isEmpty(), "waiting for the cold civet to eat");
            cleanUp(helper, civet);
        });
    }
}
