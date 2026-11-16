package com.evirapo.diversityofcritters.gametest;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.common.entity.DOCEntities;
import com.evirapo.diversityofcritters.common.entity.custom.CivetEntity;
import com.evirapo.diversityofcritters.common.entity.custom.SquirrelEntity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Hunting by prey category: the civet hunts small rodents, lagomorphs and small birds, from up to
 * 32 blocks away, only while hungry, and never a pet.
 *
 * <p>A hungry civet hunts anything in range, the next arena included, so every test with one gets
 * a batch where nothing else is prey. Each test discards its own animals (not killed: no drops, and
 * a chase may have left the arena), also shortly before its timeout in case it fails.
 */
@GameTestHolder(DiversityOfCritters.MODID)
@PrefixGameTestTemplate(false)
public final class HuntingGameTests {

    private static final String ARENA = "mantle_arena";
    private static final int MIDNIGHT = 18000;

    private HuntingGameTests() {
    }

    /** Floor, midnight (the civet is nocturnal) and a civet in one corner. */
    private static CivetEntity setUp(GameTestHelper helper, boolean hungry) {
        helper.setDayTime(MIDNIGHT);
        for (int x = 1; x <= 10; x++) {
            for (int z = 1; z <= 10; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
                helper.setBlock(x, 2, z, Blocks.STONE);
            }
        }
        CivetEntity civet = helper.spawn(DOCEntities.CIVET.get(), new Vec3(1.5, 3, 1.5));
        civet.setHunger(hungry ? civet.maxHunger() / 10 : civet.maxHunger());
        return civet;
    }

    private static void discardLater(GameTestHelper helper, int timeoutTicks, Entity... entities) {
        helper.runAfterDelay(timeoutTicks - 2, () -> discard(entities));
    }

    private static void discard(Entity... entities) {
        for (Entity e : entities) e.discard();
    }

    private static void targets(GameTestHelper helper, CivetEntity civet, LivingEntity prey, String what) {
        discardLater(helper, 400, civet, prey);
        helper.succeedWhen(() -> {
            helper.assertTrue(civet.getTarget() == prey, "waiting for the civet to target the " + what
                    + ", target: " + civet.getTarget());
            discard(civet, prey);
        });
    }

    private static void ignores(GameTestHelper helper, CivetEntity civet, LivingEntity other, String what) {
        helper.onEachTick(() -> {
            if (civet.getTarget() != null) helper.fail("the civet targeted " + what + ": " + civet.getTarget());
        });
        helper.runAfterDelay(200, () -> {
            discard(civet, other);
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 400, batch = "hunt_squirrel")
    public static void aHungryCivetHuntsASquirrel(GameTestHelper helper) {
        CivetEntity civet = setUp(helper, true);
        SquirrelEntity squirrel = helper.spawn(DOCEntities.SQUIRREL.get(), new Vec3(9.5, 3, 9.5));
        targets(helper, civet, squirrel, "squirrel");
    }

    @GameTest(template = ARENA, timeoutTicks = 400, batch = "hunt_rabbit")
    public static void aHungryCivetStillHuntsRabbits(GameTestHelper helper) {
        CivetEntity civet = setUp(helper, true);
        LivingEntity rabbit = helper.spawn(EntityType.RABBIT, new Vec3(9.5, 3, 9.5));
        targets(helper, civet, rabbit, "rabbit");
    }

    @GameTest(template = ARENA, timeoutTicks = 300, batch = "hunt_fed")
    public static void aFedCivetIgnoresPrey(GameTestHelper helper) {
        CivetEntity civet = setUp(helper, false);
        SquirrelEntity squirrel = helper.spawn(DOCEntities.SQUIRREL.get(), new Vec3(9.5, 3, 9.5));
        ignores(helper, civet, squirrel, "a squirrel while fed");
    }

    @GameTest(template = ARENA, timeoutTicks = 300, batch = "hunt_protected")
    public static void aHungryCivetLeavesATamedSquirrelAlone(GameTestHelper helper) {
        CivetEntity civet = setUp(helper, true);
        SquirrelEntity squirrel = helper.spawn(DOCEntities.SQUIRREL.get(), new Vec3(9.5, 3, 9.5));
        squirrel.setTame(true);
        ignores(helper, civet, squirrel, "a tamed squirrel");
    }

    @GameTest(template = ARENA, timeoutTicks = 300, batch = "hunt_protected")
    public static void aHungryCivetDoesNotHuntASifaka(GameTestHelper helper) {
        CivetEntity civet = setUp(helper, true);
        LivingEntity sifaka = helper.spawn(DOCEntities.SIFAKA.get(), new Vec3(9.5, 3, 9.5));
        ignores(helper, civet, sifaka, "a sifaka");
    }

    /** The arena is too small to show it, so the range itself: 32 blocks, twice the old 16. */
    @GameTest(template = ARENA, batch = "hunt_fed")
    public static void theCivetSpotsPreyFrom32Blocks(GameTestHelper helper) {
        CivetEntity civet = setUp(helper, false);
        helper.assertTrue(civet.huntRange() >= 32.0D, "hunt range " + civet.huntRange());
        helper.assertTrue(civet.getAttributeValue(Attributes.FOLLOW_RANGE) >= 32.0D,
                "follow range " + civet.getAttributeValue(Attributes.FOLLOW_RANGE) + " would drop the chase early");
        civet.discard();
        helper.succeed();
    }

    /** Walls two blocks high round the floor, so a rabbit cannot run off. */
    private static void walls(GameTestHelper helper) {
        for (int x = 1; x <= 10; x++) {
            for (int z = 1; z <= 10; z++) {
                boolean edge = x == 1 || x == 10 || z == 1 || z == 10;
                helper.setBlock(x, 3, z, edge ? Blocks.STONE : Blocks.AIR);
                helper.setBlock(x, 4, z, edge ? Blocks.STONE : Blocks.AIR);
            }
        }
    }

    /** Killing prey is enrichment, as it was before hunting went by category. */
    @GameTest(template = ARENA, timeoutTicks = 800, batch = "hunt_enrichment")
    public static void aKillFillsTheCivetsEnrichment(GameTestHelper helper) {
        CivetEntity civet = setUp(helper, true);
        walls(helper);
        civet.setPos(helper.absoluteVec(new Vec3(2.5, 3, 2.5)));
        civet.setEnrichment(0);
        LivingEntity rabbit = helper.spawn(EntityType.RABBIT, new Vec3(8.5, 3, 8.5));
        discardLater(helper, 800, civet, rabbit);

        helper.succeedWhen(() -> {
            helper.assertTrue(!rabbit.isAlive(), "waiting for the kill");
            helper.assertTrue(civet.getEnrichment() == civet.maxEnrichment(),
                    "enrichment after the kill: " + civet.getEnrichment() + "/" + civet.maxEnrichment());
            discard(civet);
            clearCorpses(helper);
        });
    }

    /**
     * After a kill the civet goes to eat it, instead of hunting the next prey in sight first: with
     * two rabbits, it eats the first one's corpse while the second is still alive.
     */
    @GameTest(template = ARENA, timeoutTicks = 1000, batch = "hunt_one_at_a_time")
    public static void aCivetEatsItsKillBeforeHuntingAgain(GameTestHelper helper) {
        CivetEntity civet = setUp(helper, true);
        walls(helper);
        civet.setPos(helper.absoluteVec(new Vec3(2.5, 3, 2.5)));
        LivingEntity first = helper.spawn(EntityType.RABBIT, new Vec3(8.5, 3, 8.5));
        LivingEntity second = helper.spawn(EntityType.RABBIT, new Vec3(8.5, 3, 2.5));
        discardLater(helper, 1000, civet, first, second);

        helper.onEachTick(() -> {
            if (!first.isAlive() && !second.isAlive() && civet.isHungry()) {
                helper.fail("the civet killed both rabbits before eating");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(!first.isAlive() || !second.isAlive(), "waiting for the first kill");
            helper.assertTrue(!civet.isHungry(), "waiting for the civet to eat its kill, hunger "
                    + civet.getHungerPercentage() + "%");
            discard(civet, first, second);
            clearCorpses(helper);
        });
    }

    private static void clearCorpses(GameTestHelper helper) {
        for (int x = 1; x <= 10; x++) {
            for (int z = 1; z <= 10; z++) {
                for (int y = 2; y <= 4; y++) {
                    net.minecraft.core.BlockPos p = new net.minecraft.core.BlockPos(x, y, z);
                    if (helper.getBlockState(p).is(com.evirapo.diversityofcritters.common.block.DOCBlocks.CORPSE.get())) {
                        helper.setBlock(p, Blocks.AIR);
                    }
                }
            }
        }
    }
}
