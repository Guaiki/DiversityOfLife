package com.evirapo.diversityofcritters.gametest;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.common.entity.DOCEntities;
import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Climbing ledges, slabs and stairs with real critters in a real world, headless:
 * {@code gradlew runGameTestServer}. Each test builds its scene in an empty arena, takes the
 * critters' goals away (no sleeping, no wandering off) and walks them to a target with their own
 * navigation. Every tick of every critter goes to the log as a {@code [MantleTest]} line.
 *
 * <p>The arena, relative to the test's origin: stone at y 1 and 2, so the ground is at y 3. The pit,
 * where a test has one, is x 4..6, z 3..7 with its floor at y 2: one block deep.
 */
@GameTestHolder(DiversityOfCritters.MODID)
@PrefixGameTestTemplate(false)
public final class MantleGameTests {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ARENA = "mantle_arena";
    private static final int TIMEOUT = 400;
    /** Close enough to the target to count as there: moveTo's path ends a block from its target block. */
    private static final double ARRIVED = 1.5;

    private MantleGameTests() {
    }

    // --- Scenes.

    /** Ground everywhere at y 3; with {@code pit}, the pit dug out of it. */
    private static void arena(GameTestHelper helper, boolean pit) {
        for (int x = 1; x <= 10; x++) {
            for (int z = 1; z <= 10; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
                final boolean inPit = pit && x >= 4 && x <= 6 && z >= 3 && z <= 7;
                helper.setBlock(x, 2, z, inPit ? Blocks.AIR : Blocks.STONE);
            }
        }
    }

    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void civetMantlesOutOfAPit(GameTestHelper helper) {
        arena(helper, true);
        final Run run = new Run(helper, "block");
        run.walk(DOCEntities.CIVET.get(), new Vec3(5.5, 2, 4.5), new Vec3(5.5, 3, 9.5));
        run.start();
    }

    /** Pit floor, a slab, then the ground: two half steps. */
    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void civetClimbsSlabStepsOutOfAPit(GameTestHelper helper) {
        arena(helper, true);
        helper.setBlock(5, 2, 7, Blocks.STONE_SLAB);
        final Run run = new Run(helper, "slab steps");
        run.walk(DOCEntities.CIVET.get(), new Vec3(5.5, 2, 4.5), new Vec3(5.5, 3, 9.5));
        run.start();
    }

    /** Pit floor, a stair up to the ground: walked, never mantled. */
    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void civetWalksUpAStairOutOfAPit(GameTestHelper helper) {
        arena(helper, true);
        helper.setBlock(5, 2, 7, Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
        final Run run = new Run(helper, "stair");
        run.walk(DOCEntities.CIVET.get(), new Vec3(5.5, 2, 4.5), new Vec3(5.5, 3, 9.5));
        run.neverMantles();
        run.start();
    }

    /** A flight of two stairs up onto a platform: walked, never mantled. */
    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void civetWalksUpAFlightOfStairs(GameTestHelper helper) {
        arena(helper, false);
        final var south = Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
        for (int x = 4; x <= 6; x++) {
            helper.setBlock(x, 3, 6, south);
            helper.setBlock(x, 3, 7, Blocks.STONE);
            helper.setBlock(x, 4, 7, south);
            for (int z = 8; z <= 10; z++) {
                helper.setBlock(x, 3, z, Blocks.STONE);
                helper.setBlock(x, 4, z, Blocks.STONE);
            }
        }
        final Run run = new Run(helper, "flight");
        run.walk(DOCEntities.CIVET.get(), new Vec3(5.5, 3, 2.5), new Vec3(5.5, 5, 9.5));
        run.neverMantles();
        run.start();
    }

    /** A civet and a squirrel in the pit, each heading out past the other. */
    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void civetAndSquirrelPassEachOtherInAPit(GameTestHelper helper) {
        arena(helper, true);
        final Run run = new Run(helper, "head-on pit");
        run.walk(DOCEntities.CIVET.get(), new Vec3(5.5, 2, 3.5), new Vec3(5.5, 3, 9.5));
        run.walk(DOCEntities.SQUIRREL.get(), new Vec3(5.5, 2, 7.5), new Vec3(5.5, 3, 1.5));
        run.start();
    }

    /** The same on flat ground. */
    @GameTest(template = ARENA, timeoutTicks = TIMEOUT)
    public static void civetAndSquirrelPassEachOtherOnFlatGround(GameTestHelper helper) {
        arena(helper, false);
        final Run run = new Run(helper, "head-on flat");
        run.walk(DOCEntities.CIVET.get(), new Vec3(5.5, 3, 2.5), new Vec3(5.5, 3, 9.5));
        run.walk(DOCEntities.SQUIRREL.get(), new Vec3(5.5, 3, 9.5), new Vec3(5.5, 3, 2.5));
        run.start();
    }

    // --- Running a scene.

    /** Critters walking to their targets, traced every tick. */
    private static final class Run {
        private final GameTestHelper helper;
        private final String name;
        private final List<Walker> walkers = new ArrayList<>();
        private boolean neverMantles;

        Run(GameTestHelper helper, String name) {
            this.helper = helper;
            this.name = name;
            // Daytime: nothing about the scene should depend on it, but the critters sleep at night.
            helper.getLevel().setDayTime(6000);
        }

        void walk(EntityType<? extends DiverseCritter> type, Vec3 from, Vec3 to) {
            final DiverseCritter critter = this.helper.spawn(type, from);
            critter.setAge(0);
            critter.goalSelector.removeAllGoals(goal -> true);
            critter.targetSelector.removeAllGoals(goal -> true);
            critter.setPersistenceRequired();
            this.walkers.add(new Walker(critter, this.helper.absoluteVec(to), to));
        }

        void neverMantles() {
            this.neverMantles = true;
        }

        void start() {
            this.helper.onEachTick(this::tick);
            this.helper.succeedWhen(() -> {
                for (Walker w : this.walkers) {
                    if (!w.arrived()) {
                        throw new GameTestAssertException(String.format(Locale.ROOT, "%s: %s at %s, not at %s",
                                this.name, w.label(), this.rel(w.critter.position()), w.relTarget));
                    }
                }
                LOGGER.info("[MantleTest] {} passed at tick {}", this.name, this.helper.getTick());
            });
        }

        private void tick() {
            for (Walker w : this.walkers) {
                final DiverseCritter c = w.critter;
                if (!w.arrived() && c.getNavigation().isDone()) {
                    c.getNavigation().moveTo(w.target.x, w.target.y, w.target.z, 1.0D);
                }
                if (this.neverMantles && c.isMantling()) {
                    throw new GameTestAssertException(this.name + ": " + w.label() + " mantled at " + this.rel(c.position()));
                }
                final Path path = c.getNavigation().getPath();
                String next = "-";
                if (path != null && !path.isDone() && path.getNextNodeIndex() < path.getNodeCount()) {
                    final Node n = path.getNode(path.getNextNodeIndex());
                    final BlockPos rel = new BlockPos(n.x, n.y, n.z).subtract(this.helper.absolutePos(BlockPos.ZERO));
                    next = rel.getX() + "," + rel.getY() + "," + rel.getZ() + " " + n.type;
                }
                final Vec3 v = c.getDeltaMovement();
                LOGGER.info(String.format(Locale.ROOT,
                        "[MantleTest] %s t=%d %s pos=%s yaw=%.0f ground=%b hcol=%b mantle=%b step=%.2f next=%s v=(%.3f,%.3f,%.3f)",
                        this.name, this.helper.getTick(), w.label(), this.rel(c.position()), c.getYRot(),
                        c.onGround(), c.horizontalCollision, c.isMantling(), c.maxUpStep(), next, v.x, v.y, v.z));
            }
        }

        private String rel(Vec3 p) {
            final Vec3 o = this.helper.absoluteVec(Vec3.ZERO);
            return String.format(Locale.ROOT, "(%.2f,%.2f,%.2f)", p.x - o.x, p.y - o.y, p.z - o.z);
        }
    }

    private record Walker(DiverseCritter critter, Vec3 target, Vec3 relTarget) {
        boolean arrived() {
            final Vec3 p = this.critter.position();
            final double dx = p.x - this.target.x;
            final double dz = p.z - this.target.z;
            return dx * dx + dz * dz < ARRIVED * ARRIVED && Math.abs(p.y - this.target.y) < 0.6;
        }

        String label() {
            return this.critter.getType().toShortString() + "#" + this.critter.getId();
        }
    }
}
