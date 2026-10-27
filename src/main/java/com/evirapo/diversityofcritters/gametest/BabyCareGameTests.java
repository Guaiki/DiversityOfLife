package com.evirapo.diversityofcritters.gametest;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.common.block.DOCBlocks;
import com.evirapo.diversityofcritters.common.block.entity.BowlBlockEntity;
import com.evirapo.diversityofcritters.common.entity.DOCEntities;
import com.evirapo.diversityofcritters.common.entity.custom.CivetEntity;
import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
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
 * Who a baby follows and whether a juvenile still gets to eat, headless:
 * {@code gradlew runGameTestServer}.
 *
 * <p>Same walled stone arena as {@link ReachabilityGameTests}. The adult sits in a glass cage so it
 * stays put, and a baby following it walks up to the glass.
 */
@GameTestHolder(DiversityOfCritters.MODID)
@PrefixGameTestTemplate(false)
public final class BabyCareGameTests {

    private static final String ARENA = "mantle_arena";
    private static final String BATCH = "baby_care_civet";
    private static final int TIMEOUT = 600;
    private static final int MIDNIGHT = 18000;
    private static final int JUVENILE_AGE = DiverseCritter.JUVENILE_AGE_THRESHOLD / 2;

    private BabyCareGameTests() {
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

    private static CivetEntity civetAt(GameTestHelper helper, double x, double z) {
        helper.setDayTime(MIDNIGHT);
        return helper.spawn(DOCEntities.CIVET.get(), new Vec3(x, 3, z));
    }

    /** An adult civet in a glass cage at (4, 4), so the baby always has the same parent to walk to. */
    private static CivetEntity cagedAdult(GameTestHelper helper) {
        ReachabilityGameTests.sealIn(helper, new BlockPos(4, 3, 4), 1, 2);
        return civetAt(helper, 4.5, 4.5);
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT, batch = BATCH)
    public static void aTameNewbornIsNotFollowingButATameJuvenileIs(GameTestHelper helper) {
        walledFloor(helper);
        CivetEntity newborn = civetAt(helper, 3.5, 3.5);
        newborn.setBaby(true);
        newborn.setTame(true);
        CivetEntity juvenile = civetAt(helper, 7.5, 7.5);
        juvenile.setAge(JUVENILE_AGE);
        juvenile.setTame(true);

        helper.assertTrue(newborn.isNewborn(), "setup: the first civet is not a newborn");
        helper.assertTrue(juvenile.isJuvenile(), "setup: the second civet is not a juvenile");
        helper.assertFalse(newborn.isFollowing(), "a tame newborn is in follow-the-owner mode");
        helper.assertTrue(juvenile.isFollowing(), "a tame juvenile is not in follow-the-owner mode");
        helper.killAllEntities();
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT, batch = BATCH)
    public static void aHungryJuvenileWalksAwayFromItsMotherToEat(GameTestHelper helper) {
        walledFloor(helper);
        cagedAdult(helper);
        BlockPos bowlPos = new BlockPos(8, 3, 9);
        helper.setBlock(bowlPos, DOCBlocks.BOWL.get());
        ((BowlBlockEntity) helper.getBlockEntity(bowlPos)).getInventory().setItem(0, new ItemStack(Items.BEEF, 8));
        CivetEntity juvenile = civetAt(helper, 7.5, 4.5);
        juvenile.setAge(JUVENILE_AGE);
        juvenile.setHunger(juvenile.maxHunger() / 4);

        helper.succeedWhen(() -> {
            helper.assertTrue(juvenile.getHunger() > juvenile.maxHunger() * 9 / 10, "waiting for the juvenile to eat");
            helper.killAllEntities();
        });
    }

    /** Regression: following the parent now claims MOVE, and a newborn must still do it. */
    @GameTest(template = ARENA, timeoutTicks = TIMEOUT, batch = BATCH)
    public static void aNewbornStillFollowsItsMother(GameTestHelper helper) {
        walledFloor(helper);
        CivetEntity adult = cagedAdult(helper);
        CivetEntity newborn = civetAt(helper, 9.5, 9.5);
        newborn.setBaby(true);

        helper.succeedWhen(() -> {
            helper.assertTrue(newborn.distanceTo(adult) <= 4.0F, "waiting for the newborn to reach its mother");
            helper.killAllEntities();
        });
    }
}
