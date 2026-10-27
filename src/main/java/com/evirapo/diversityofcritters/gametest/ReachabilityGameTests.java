package com.evirapo.diversityofcritters.gametest;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.common.block.DOCBlocks;
import com.evirapo.diversityofcritters.common.block.entity.BowlBlockEntity;
import com.evirapo.diversityofcritters.common.entity.DOCEntities;
import com.evirapo.diversityofcritters.common.entity.ai.CritterNodeEvaluator;
import com.evirapo.diversityofcritters.common.entity.custom.CivetEntity;
import com.evirapo.diversityofcritters.common.entity.util.Reachability;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

import java.util.Set;

/**
 * Critters only head for blocks they can walk to, headless: {@code gradlew runGameTestServer}.
 *
 * <p>The arena is stone with the ground at y 3, walled in two blocks high. "Out of reach" is a glass
 * cage with a roof, so not even a climber gets in. All civet tests share a batch: the clock is global
 * and the civet is nocturnal.
 */
@GameTestHolder(DiversityOfCritters.MODID)
@PrefixGameTestTemplate(false)
public final class ReachabilityGameTests {

    private static final String ARENA = "mantle_arena";
    private static final String BATCH = "reachability_civet";
    private static final int TIMEOUT = 600;
    private static final int MIDNIGHT = 18000;
    private static final float RANGE = 32.0F;

    private ReachabilityGameTests() {
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

    /**
     * Glass shell around an air box of {@code radius} around {@code center} and {@code height} tall
     * from the ground, roof included. The inside is left untouched, so place the target after.
     */
    static void sealIn(GameTestHelper helper, BlockPos center, int radius, int height) {
        int outer = radius + 1;
        for (int x = center.getX() - outer; x <= center.getX() + outer; x++) {
            for (int z = center.getZ() - outer; z <= center.getZ() + outer; z++) {
                for (int y = 3; y <= 3 + height; y++) {
                    boolean inside = Math.abs(x - center.getX()) <= radius
                            && Math.abs(z - center.getZ()) <= radius
                            && y <= 3 + height - 1;
                    if (!inside) helper.setBlock(x, y, z, Blocks.GLASS);
                }
            }
        }
    }

    /**
     * Topped up, from the quarter the tests start at. Not {@code == max}: needs keep draining a point
     * every few ticks, so the exact maximum lasts only until the next drain.
     */
    private static boolean refilled(int value, int max) {
        return value > max * 9 / 10;
    }

    private static CivetEntity civetAt(GameTestHelper helper, double x, double z) {
        helper.setDayTime(MIDNIGHT);
        return helper.spawn(DOCEntities.CIVET.get(), new Vec3(x, 3, z));
    }

    /** A path the way {@code PathNavigation} builds one, but over the evaluator we hand it. */
    private static boolean pathReaches(CivetEntity mob, CritterNodeEvaluator evaluator, BlockPos target) {
        evaluator.setCanPassDoors(true);
        BlockPos from = mob.blockPosition();
        int r = (int) RANGE;
        PathNavigationRegion region = new PathNavigationRegion(mob.level(), from.offset(-r, -r, -r), from.offset(r, r, r));
        Path path = new PathFinder(evaluator, 512).findPath(region, mob, Set.of(target), RANGE, 1, 1.0F);
        return path != null && path.canReach();
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT, batch = BATCH)
    public static void onlyAClimberPathsOverALogWall(GameTestHelper helper) {
        walledFloor(helper);
        for (int x = 2; x <= 9; x++) {
            helper.setBlock(x, 3, 6, Blocks.OAK_LOG);
            helper.setBlock(x, 4, 6, Blocks.OAK_LOG);
        }
        BlockPos bowl = new BlockPos(5, 3, 8);
        helper.setBlock(bowl, DOCBlocks.BOWL.get());
        CivetEntity civet = civetAt(helper, 5.5, 3.5);

        helper.succeedWhen(() -> {
            helper.assertTrue(civet.onGround(), "waiting for the civet to land");
            BlockPos target = helper.absolutePos(bowl);
            helper.assertTrue(pathReaches(civet, new CritterNodeEvaluator(true), target),
                    "a climber found no way over the log wall");
            helper.assertFalse(pathReaches(civet, new CritterNodeEvaluator(false), target),
                    "a non-climber pathed over the log wall");
            // And the civet's own navigation was built as a climber's (canClimb() wiring).
            helper.assertTrue(Reachability.canReach(civet, target, 1, RANGE),
                    "the civet's navigation does not climb");
            helper.killAllEntities();
        });
    }

    /** A bowl holding {@code stack}; filling the slot flips the bowl's CONTENT on its own. */
    private static void bowlWith(GameTestHelper helper, BlockPos pos, ItemStack stack) {
        helper.setBlock(pos, DOCBlocks.BOWL.get());
        BowlBlockEntity bowl = (BowlBlockEntity) helper.getBlockEntity(pos);
        bowl.getInventory().setItem(0, stack);
    }

    private static ItemStack meat() {
        return new ItemStack(Items.BEEF, 8);
    }

    private static ItemStack water() {
        return PotionUtils.setPotion(new ItemStack(Items.POTION, 4), Potions.WATER);
    }

    private static IItemHandler digBoxWith(GameTestHelper helper, BlockPos pos, int sticks) {
        helper.setBlock(pos, DOCBlocks.DIG_BOX.get());
        IItemHandler handler = helper.getBlockEntity(pos).getCapability(ForgeCapabilities.ITEM_HANDLER)
                .orElseThrow(IllegalStateException::new);
        handler.insertItem(0, new ItemStack(Items.STICK, sticks), false);
        return handler;
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT, batch = BATCH)
    public static void aCivetEatsFromTheReachableBowlNotTheSealedOne(GameTestHelper helper) {
        walledFloor(helper);
        BlockPos sealed = new BlockPos(4, 3, 4);
        sealIn(helper, sealed, 0, 1);
        bowlWith(helper, sealed, meat());
        bowlWith(helper, new BlockPos(8, 3, 8), meat());
        CivetEntity civet = civetAt(helper, 2.5, 2.5);
        civet.setHunger(civet.maxHunger() / 4);

        helper.succeedWhen(() -> {
            helper.assertTrue(refilled(civet.getHunger(), civet.maxHunger()), "waiting for it to eat");
            helper.killAllEntities();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT, batch = BATCH)
    public static void aCivetSwitchesBowlsWhenItsBowlGetsWalledOff(GameTestHelper helper) {
        walledFloor(helper);
        BlockPos first = new BlockPos(8, 3, 8);
        bowlWith(helper, first, meat());
        CivetEntity civet = civetAt(helper, 2.5, 2.5);
        civet.setHunger(civet.maxHunger() / 4);

        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    sealIn(helper, first, 0, 1);
                    bowlWith(helper, new BlockPos(2, 3, 9), meat());
                })
                .thenWaitUntil(() -> helper.assertTrue(refilled(civet.getHunger(), civet.maxHunger()),
                        "waiting for it to eat from the second bowl"))
                .thenExecute(helper::killAllEntities)
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT, batch = BATCH)
    public static void aCivetDrinksFromTheReachableBowlNotTheSealedOne(GameTestHelper helper) {
        walledFloor(helper);
        BlockPos sealed = new BlockPos(4, 3, 4);
        sealIn(helper, sealed, 0, 1);
        bowlWith(helper, sealed, water());
        bowlWith(helper, new BlockPos(8, 3, 8), water());
        CivetEntity civet = civetAt(helper, 2.5, 2.5);
        civet.setThirst(civet.maxThirst() / 4);

        helper.succeedWhen(() -> {
            helper.assertTrue(refilled(civet.getThirst(), civet.maxThirst()), "waiting for it to drink");
            helper.killAllEntities();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT, batch = BATCH)
    public static void aCivetDigsInTheReachableBoxNotTheSealedOne(GameTestHelper helper) {
        walledFloor(helper);
        BlockPos sealed = new BlockPos(4, 3, 4);
        sealIn(helper, sealed, 0, 1);
        digBoxWith(helper, sealed, 1);
        IItemHandler open = digBoxWith(helper, new BlockPos(8, 3, 8), 2);
        CivetEntity civet = civetAt(helper, 2.5, 2.5);
        civet.setEnrichment(0);

        helper.succeedWhen(() -> {
            helper.assertTrue(open.getStackInSlot(0).getCount() == 1, "waiting for it to dig in the open box");
            helper.killAllEntities();
        });
    }

    /**
     * Checks which log the civet picks, not that it finishes scratching: the scratch approach can stop
     * a block short of its stand cell (DeluxeLib walks there with vanilla's accuracy-1 moveTo), which
     * has nothing to do with walls and would make this test flaky.
     */
    @GameTest(template = ARENA, timeoutTicks = TIMEOUT, batch = BATCH)
    public static void aCivetPicksTheReachableLogNotTheSealedOne(GameTestHelper helper) {
        walledFloor(helper);
        BlockPos sealed = new BlockPos(5, 3, 5);
        sealIn(helper, sealed, 1, 2);
        helper.setBlock(sealed, Blocks.OAK_LOG);
        BlockPos open = new BlockPos(9, 3, 2);
        helper.setBlock(open, Blocks.OAK_LOG);
        CivetEntity civet = civetAt(helper, 8.5, 8.5);
        civet.setEnrichment(0);
        // Tame and not told to wander: it does not stroll. A wild one strolls between tries until the
        // open log happens to be the nearer one, and then even the old code picked it.
        civet.setTame(true);

        helper.onEachTick(() -> {
            if (helper.absolutePos(sealed).equals(civet.getScratchTarget())) {
                helper.fail("the civet picked the sealed log");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.absolutePos(open).equals(civet.getScratchTarget()),
                    "waiting for it to pick the open log");
            helper.killAllEntities();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT, batch = BATCH)
    public static void reachabilityRejectsASealedBlock(GameTestHelper helper) {
        walledFloor(helper);
        BlockPos bowl = new BlockPos(4, 3, 4);
        sealIn(helper, bowl, 0, 1);
        helper.setBlock(bowl, DOCBlocks.BOWL.get());
        CivetEntity civet = civetAt(helper, 2.5, 2.5);

        helper.succeedWhen(() -> {
            helper.assertTrue(civet.onGround(), "waiting for the civet to land");
            helper.assertFalse(Reachability.canReach(civet, helper.absolutePos(bowl), 1, RANGE),
                    "the sealed bowl counted as reachable");
            helper.killAllEntities();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT, batch = BATCH)
    public static void reachabilityFollowsADetour(GameTestHelper helper) {
        walledFloor(helper);
        for (int x = 2; x <= 8; x++) {
            helper.setBlock(x, 3, 6, Blocks.STONE);
            helper.setBlock(x, 4, 6, Blocks.STONE);
        }
        BlockPos bowl = new BlockPos(5, 3, 8);
        helper.setBlock(bowl, DOCBlocks.BOWL.get());
        CivetEntity civet = civetAt(helper, 2.5, 2.5);

        helper.succeedWhen(() -> {
            helper.assertTrue(civet.onGround(), "waiting for the civet to land");
            helper.assertTrue(Reachability.canReach(civet, helper.absolutePos(bowl), 1, RANGE),
                    "the bowl around the wall counted as unreachable");
            helper.killAllEntities();
        });
    }
}
