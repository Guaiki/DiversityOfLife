package com.evirapo.diversityofcritters.gametest;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.common.block.DOCBlocks;
import com.evirapo.diversityofcritters.common.block.entity.BowlBlockEntity;
import com.evirapo.diversityofcritters.common.entity.DOCEntities;
import com.evirapo.diversityofcritters.common.entity.custom.CivetEntity;
import com.evirapo.diversityofcritters.common.entity.util.BowlFeedingHelper;
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
 * Eating quenches thirst: a small animal gets {@code smallThirstFromFood} (50 %) of its max thirst
 * back per meal. The meal is taken in the same tick it is checked, because thirst drains fast enough
 * on the test server to blur a walk to the bowl.
 */
@GameTestHolder(DiversityOfCritters.MODID)
@PrefixGameTestTemplate(false)
public final class ThirstFromFoodGameTests {

    private static final String ARENA = "mantle_arena";
    private static final BlockPos BOWL = new BlockPos(5, 3, 6);

    private ThirstFromFoodGameTests() {
    }

    /** Thirst, in % of max, right after the civet eats the single steak in a bowl next to it. */
    private static int thirstPercentAfterOneMeal(GameTestHelper helper, int thirstPercentBefore) {
        for (int x = 1; x <= 10; x++) {
            for (int z = 1; z <= 10; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
                helper.setBlock(x, 2, z, Blocks.STONE);
            }
        }
        helper.setBlock(BOWL, DOCBlocks.BOWL.get());
        BowlBlockEntity bowl = (BowlBlockEntity) helper.getBlockEntity(BOWL);
        bowl.getInventory().setItem(0, new ItemStack(Items.BEEF, 1));

        CivetEntity civet = helper.spawn(DOCEntities.CIVET.get(), new Vec3(5.5, 3, 5.5));
        civet.setHunger(civet.maxHunger() / 10);
        civet.setThirst(civet.maxThirst() * thirstPercentBefore / 100);

        helper.assertTrue(BowlFeedingHelper.consumeFoodFor(civet, helper.getLevel(), helper.absolutePos(BOWL)),
                "the civet could not eat from the meat bowl");
        helper.assertTrue(bowl.getInventory().isEmpty(), "the steak was not eaten");
        helper.assertTrue(civet.getHunger() == civet.maxHunger(), "hunger not refilled: " + civet.getHunger());
        return civet.getThirstPercentage();
    }

    @GameTest(template = ARENA)
    public static void eatingQuenchesHalfOfASmallAnimalsThirst(GameTestHelper helper) {
        int thirst = thirstPercentAfterOneMeal(helper, 10);
        helper.assertTrue(thirst == 60, "thirst after one meal should be 60 %, was " + thirst);
        helper.killAllEntities();
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void thirstFromFoodIsCappedAtMax(GameTestHelper helper) {
        int thirst = thirstPercentAfterOneMeal(helper, 90);
        helper.assertTrue(thirst == 100, "thirst should be capped at 100 %, was " + thirst);
        helper.killAllEntities();
        helper.succeed();
    }
}
