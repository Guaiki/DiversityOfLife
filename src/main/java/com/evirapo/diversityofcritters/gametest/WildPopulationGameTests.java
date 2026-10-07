package com.evirapo.diversityofcritters.gametest;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.common.damage.DOCDamageTypes;
import com.evirapo.diversityofcritters.common.entity.DOCEntities;
import com.evirapo.diversityofcritters.common.entity.custom.CivetEntity;
import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.evirapo.diversityofcritters.common.spawn.DOCSpecies;
import com.evirapo.diversityofcritters.common.spawn.WildPopulation;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The wild population hooks with real critters, headless: {@code gradlew runGameTestServer}.
 *
 * <p>The population is global to the world and the game test server runs every test at once, so
 * they must not step on each other's numbers. Tests that finish within their own method run
 * atomically on the server thread and can share the civet. The two birth tests wait for ticks to
 * pass, so each gets a species of its own that nothing else touches: the squirrel and the sifaka.
 */
@GameTestHolder(DiversityOfCritters.MODID)
@PrefixGameTestTemplate(false)
public final class WildPopulationGameTests {

    private static final String ARENA = "mantle_arena";
    private static final Vec3 SPOT = new Vec3(5.5, 3, 5.5);
    private static final BlockPos CENTER = new BlockPos(5, 3, 5);
    private static final int START = 30;
    private static final int BIRTH_TIMEOUT = 100;

    private WildPopulationGameTests() {
    }

    private static void floor(GameTestHelper helper) {
        for (int x = 1; x <= 10; x++) {
            for (int z = 1; z <= 10; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
                helper.setBlock(x, 2, z, Blocks.STONE);
            }
        }
    }

    private static MinecraftServer server(GameTestHelper helper) {
        return helper.getLevel().getServer();
    }

    /** Puts a species at a known value, away from floor, cap and the endangered line. */
    private static WildPopulation start(GameTestHelper helper, DOCSpecies species) {
        floor(helper);
        WildPopulation population = WildPopulation.get(server(helper));
        population.set(species, START, server(helper));
        return population;
    }

    private static CivetEntity wildCivet(GameTestHelper helper) {
        CivetEntity civet = helper.spawn(DOCEntities.CIVET.get(), SPOT);
        civet.setInWild(true);
        return civet;
    }

    private static void killWith(GameTestHelper helper, DiverseCritter critter, ResourceKey<DamageType> type) {
        critter.hurt(DOCDamageTypes.source(helper.getLevel(), type), Float.MAX_VALUE);
    }

    private static void expect(GameTestHelper helper, WildPopulation population, DOCSpecies species,
                               int expected, String what) {
        int actual = population.get(species);
        helper.assertTrue(actual == expected, what + ": expected " + expected + ", got " + actual);
    }

    /**
     * Clears the arena before passing. A critter left alive wanders off its 10x10 floor into the
     * neighbouring tests, and the mantle tests are precise enough to fail on a stranger in the way.
     * Removed by /kill rules, so it does not count against the population.
     */
    private static void done(GameTestHelper helper) {
        helper.killAllEntities();
        helper.succeed();
    }

    // --- Single-tick tests, all on the civet.

    @GameTest(template = ARENA)
    public static void tamingAWildAnimalSubtractsOnce(GameTestHelper helper) {
        WildPopulation population = start(helper, DOCSpecies.CIVET);
        CivetEntity civet = wildCivet(helper);
        Player player = helper.makeMockPlayer();

        civet.tame(player);
        civet.tame(player);

        expect(helper, population, DOCSpecies.CIVET, START - 1, "after taming twice");
        helper.assertFalse(civet.isInWild(), "a tamed animal must leave the wild population");
        done(helper);
    }

    @GameTest(template = ARENA)
    public static void animalsThatWereNeverWildDoNotCount(GameTestHelper helper) {
        WildPopulation population = start(helper, DOCSpecies.CIVET);
        CivetEntity civet = helper.spawn(DOCEntities.CIVET.get(), SPOT);

        civet.tame(helper.makeMockPlayer());
        killWith(helper, civet, DOCDamageTypes.STARVATION);

        expect(helper, population, DOCSpecies.CIVET, START, "spawn-egg civet tamed and starved");
        done(helper);
    }

    @GameTest(template = ARENA)
    public static void habitatDeathsSubtract(GameTestHelper helper) {
        WildPopulation population = start(helper, DOCSpecies.CIVET);

        killWith(helper, wildCivet(helper), DOCDamageTypes.STARVATION);
        killWith(helper, wildCivet(helper), DOCDamageTypes.HYPOTHERMIA);
        killWith(helper, wildCivet(helper), DOCDamageTypes.HEATSTROKE);
        killWith(helper, wildCivet(helper), DOCDamageTypes.HUMIDITY);

        expect(helper, population, DOCSpecies.CIVET, START - 4, "starvation, cold, heat and humidity");
        done(helper);
    }

    @GameTest(template = ARENA)
    public static void thirstKillAndTameDeathsDoNotCount(GameTestHelper helper) {
        WildPopulation population = start(helper, DOCSpecies.CIVET);

        killWith(helper, wildCivet(helper), DOCDamageTypes.DEHYDRATION);
        wildCivet(helper).kill();

        CivetEntity pet = helper.spawn(DOCEntities.CIVET.get(), SPOT);
        pet.tame(helper.makeMockPlayer());
        killWith(helper, pet, DOCDamageTypes.STARVATION);

        expect(helper, population, DOCSpecies.CIVET, START, "thirst, /kill and a pet starving");
        done(helper);
    }

    /** Builds a civet the way /summon and other mods do: by loading it from NBT. Not added to the world. */
    private static DiverseCritter loadCivet(GameTestHelper helper, CompoundTag tag) {
        tag.putString("id", "diversityofcritters:civet");
        Entity entity = EntityType.loadEntityRecursive(tag, helper.getLevel(), e -> e);
        helper.assertTrue(entity instanceof DiverseCritter, "the tag must load a civet");
        return (DiverseCritter) entity;
    }

    @GameTest(template = ARENA)
    public static void summonedAnimalsAreNotWild(GameTestHelper helper) {
        floor(helper);
        helper.assertFalse(loadCivet(helper, new CompoundTag()).isInWild(),
                "/summon with no NBT must not count the animal as wild");
        done(helper);
    }

    @GameTest(template = ARENA)
    public static void untameAnimalsSavedBeforeTheFlagAreWild(GameTestHelper helper) {
        floor(helper);
        CompoundTag saved = new CompoundTag();
        // A critter saved by an older version: it has the mod's state but not DOCInWild.
        saved.putInt("DiverseAge", 0);
        saved.putInt("Hunger", 100);
        helper.assertTrue(loadCivet(helper, saved).isInWild(),
                "an untamed animal saved before DOCInWild existed must stay wild");
        done(helper);
    }

    @GameTest(template = ARENA)
    public static void thePopulationNeverFallsBelowTheFloor(GameTestHelper helper) {
        floor(helper);
        WildPopulation population = WildPopulation.get(server(helper));
        population.set(DOCSpecies.CIVET, 0, server(helper));

        expect(helper, population, DOCSpecies.CIVET, 5, "set to 0 with the default floor");
        population.set(DOCSpecies.CIVET, START, server(helper));
        done(helper);
    }

    // --- Births: they span ticks, so each owns a species.

    /**
     * A pregnant female about to give birth, wild or owned by {@code owner}. Waits for the baby and
     * checks how it was born and what it did to the population.
     */
    private static <T extends DiverseCritter> void birth(GameTestHelper helper, EntityType<T> type,
                                                         DOCSpecies species, @Nullable Player owner,
                                                         int expectedPopulation) {
        WildPopulation population = start(helper, species);
        T mother = helper.spawn(type, SPOT);
        if (owner == null) {
            mother.setInWild(true);
        } else {
            mother.tame(owner);
        }
        mother.setIsMale(false);
        mother.setPregnant(true);
        mother.pregnancyTimer = 1;

        helper.succeedWhen(() -> {
            List<T> babies = helper.getEntities(type, CENTER, 10.0D).stream()
                    .filter(DiverseCritter::isBaby).toList();
            helper.assertTrue(babies.size() == 1, "waiting for the birth");
            T baby = babies.get(0);

            if (owner == null) {
                helper.assertTrue(baby.isInWild(), "a wild-born baby must be wild");
                helper.assertFalse(baby.isTame(), "a wild-born baby must not be tame");
            } else {
                helper.assertTrue(baby.isTame(), "a captive-born baby must be tame");
                helper.assertTrue(owner.getUUID().equals(baby.getOwnerUUID()),
                        "the baby must be the mother's owner's");
                helper.assertFalse(baby.isInWild(), "a captive-born baby must not be wild");
            }
            expect(helper, population, species, expectedPopulation, "after the birth");
            helper.killAllEntities();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = BIRTH_TIMEOUT)
    public static void aWildBirthAddsOne(GameTestHelper helper) {
        birth(helper, DOCEntities.SQUIRREL.get(), DOCSpecies.SQUIRREL, null, START + 1);
    }

    @GameTest(template = ARENA, timeoutTicks = BIRTH_TIMEOUT)
    public static void aCaptiveBirthIsBornTameAndDoesNotCount(GameTestHelper helper) {
        birth(helper, DOCEntities.SIFAKA.get(), DOCSpecies.SIFAKA, helper.makeMockPlayer(), START);
    }
}
