package com.evirapo.diversityofcritters.gametest;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.common.block.DOCBlocks;
import com.evirapo.diversityofcritters.common.entity.DOCEntities;
import com.evirapo.diversityofcritters.common.entity.ai.CritterNodeEvaluator;
import com.evirapo.diversityofcritters.common.entity.custom.CivetEntity;
import com.evirapo.diversityofcritters.common.entity.util.Reachability;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

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
