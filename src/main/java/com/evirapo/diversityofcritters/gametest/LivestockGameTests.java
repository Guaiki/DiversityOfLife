package com.evirapo.diversityofcritters.gametest;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.common.config.DOCServerConfig;
import com.evirapo.diversityofcritters.common.config.EcosystemSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Vanilla livestock breeds on its own, up to a cap per area (6 by default, radius 32).
 *
 * <p>The breed interval is cut to one second for the test and restored afterwards. Each test gets
 * its own batch: the cap counts every cow within 32 blocks, which would include the other test's.
 */
@GameTestHolder(DiversityOfCritters.MODID)
@PrefixGameTestTemplate(false)
public final class LivestockGameTests {

    private static final String ARENA = "mantle_arena";

    private LivestockGameTests() {
    }

    /** A walled pen, so the cows stay inside the arena. */
    private static void pen(GameTestHelper helper) {
        for (int x = 0; x <= 11; x++) {
            for (int z = 0; z <= 11; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
                helper.setBlock(x, 2, z, Blocks.GRASS_BLOCK);
                boolean edge = x == 0 || z == 0 || x == 11 || z == 11;
                if (edge) {
                    helper.setBlock(x, 3, z, Blocks.STONE);
                    helper.setBlock(x, 4, z, Blocks.STONE);
                }
            }
        }
    }

    private static AABB penBox(GameTestHelper helper) {
        return new AABB(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(12, 8, 12))).inflate(2);
    }

    private static int cowsInPen(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(Cow.class, penBox(helper)).size();
    }

    /**
     * Removes the cows (and any experience orbs from breeding) without killing them. Killed cows drop
     * raw beef, and a later batch reusing this spot would find it: a hungry civet walks off after it.
     */
    private static void clearPen(GameTestHelper helper) {
        helper.getLevel().getEntities((Entity) null, penBox(helper), e -> !(e instanceof Player)).forEach(Entity::discard);
    }

    private static void fastBreeding(boolean on) {
        DOCServerConfig.set(DOCServerConfig.LIVESTOCK_BREED_INTERVAL_SECONDS,
                on ? 1 : EcosystemSettings.DEFAULTS.livestockBreedIntervalSeconds());
    }

    @GameTest(template = ARENA, timeoutTicks = 800, batch = "livestock_breed")
    public static void twoCowsBreedOnTheirOwn(GameTestHelper helper) {
        pen(helper);
        fastBreeding(true);
        helper.spawn(EntityType.COW, new Vec3(4.5, 3, 5.5));
        helper.spawn(EntityType.COW, new Vec3(6.5, 3, 5.5));

        helper.succeedWhen(() -> {
            helper.assertTrue(cowsInPen(helper) >= 3, "waiting for a calf, cows: " + cowsInPen(helper));
            fastBreeding(false);
            clearPen(helper);
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 600, batch = "livestock_cap")
    public static void cowsStopBreedingAtTheCap(GameTestHelper helper) {
        pen(helper);
        fastBreeding(true);
        for (int i = 0; i < 6; i++) {
            helper.spawn(EntityType.COW, new Vec3(2.5 + i * 1.5, 3, 5.5));
        }

        helper.onEachTick(() -> {
            if (cowsInPen(helper) > 6) helper.fail("a cow was born above the cap");
        });
        helper.runAfterDelay(500, () -> {
            fastBreeding(false);
            clearPen(helper);
            helper.succeed();
        });
    }
}
