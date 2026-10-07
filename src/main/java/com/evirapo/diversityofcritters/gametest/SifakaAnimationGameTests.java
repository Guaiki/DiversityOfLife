package com.evirapo.diversityofcritters.gametest;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.common.entity.DOCEntities;
import com.evirapo.diversityofcritters.common.entity.custom.SifakaEntity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Every animation the sifaka's goals and play conditions ask for is registered. A name that differs by
 * one letter between the registration and a goal fails silently in game (the pose just never plays),
 * so this pins the full list.
 */
@GameTestHolder(DiversityOfCritters.MODID)
@PrefixGameTestTemplate(false)
public final class SifakaAnimationGameTests {

    private static final String ARENA = "mantle_arena";

    private static final List<String> NAMES = List.of(
            "idle", "walk", "run",
            "ordered_sit_enter", "ordered_sit_loop", "ordered_sit_exit",
            "drink_enter", "drink_loop", "drink_exit",
            "clean_enter", "clean_loop", "clean_exit",
            "eat_enter", "eat_loop", "eat_exit",
            "stretch_loop", "sniff_left_loop", "sniff_right_loop",
            "idle_sit_enter", "idle_sit_loop", "idle_sit_exit",
            "idle_stand_up_enter", "idle_stand_up_loop", "idle_stand_up_exit",
            "climb_up", "climb_down", "climb_hang", "swim",
            "sleep_enter", "sleep_loop", "sleep_exit", "attack",
            "baby_idle_loop", "baby_crawl_loop",
            "baby_sleep_enter_loop", "baby_sleep_loop_loop", "baby_sleep_exit_loop",
            "cry_enter", "cry_loop", "cry_exit");

    private SifakaAnimationGameTests() {
    }

    @GameTest(template = ARENA)
    public static void everySifakaAnimationIsRegistered(GameTestHelper helper) {
        for (int x = 1; x <= 10; x++) {
            for (int z = 1; z <= 10; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
                helper.setBlock(x, 2, z, Blocks.STONE);
            }
        }
        SifakaEntity sifaka = helper.spawn(DOCEntities.SIFAKA.get(), new Vec3(5.5, 3, 5.5));

        for (String name : NAMES) {
            try {
                sifaka.animator().getByName(name);
            } catch (RuntimeException e) {
                helper.killAllEntities();
                helper.fail("the sifaka has no animation '" + name + "'");
            }
        }

        helper.killAllEntities();
        helper.succeed();
    }
}
