package com.evirapo.diversityofcritters.gametest;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.common.block.DOCBlocks;
import com.evirapo.diversityofcritters.common.block.entity.CorpseBlockEntity;
import com.evirapo.diversityofcritters.common.entity.DOCEntities;
import com.evirapo.diversityofcritters.common.entity.custom.CivetEntity;
import com.evirapo.diversityofcritters.common.event.CorpseEvents;
import com.evirapo.diversityofcritters.misc.tags.DoCTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Corpses: prey killed by a predator leaves a corpse instead of drops; it feeds any meat eater, one
 * portion per meal, and rots away. Hungry civets get batches of their own (they hunt the next arena).
 */
@GameTestHolder(DiversityOfCritters.MODID)
@PrefixGameTestTemplate(false)
public final class CorpseGameTests {

    private static final String ARENA = "mantle_arena";
    private static final int MIDNIGHT = 18000;

    private CorpseGameTests() {
    }

    private static void walledFloor(GameTestHelper helper) {
        helper.setDayTime(MIDNIGHT);
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

    private static CorpseBlockEntity corpse(GameTestHelper helper, BlockPos pos, TagKey<EntityType<?>> category,
                                            int portions, long bornGameTime) {
        helper.setBlock(pos, DOCBlocks.CORPSE.get());
        CorpseBlockEntity be = (CorpseBlockEntity) helper.getBlockEntity(pos);
        be.init(category, portions, bornGameTime);
        return be;
    }

    private static AABB arenaBox(GameTestHelper helper) {
        return new AABB(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(12, 6, 12)));
    }

    private static CivetEntity hungryCivet(GameTestHelper helper, int timeoutTicks) {
        CivetEntity civet = helper.spawn(DOCEntities.CIVET.get(), new Vec3(2.5, 3, 2.5));
        civet.setHunger(civet.maxHunger() / 10);
        helper.runAfterDelay(timeoutTicks - 2, civet::discard);
        return civet;
    }

    @GameTest(template = ARENA, timeoutTicks = 800, batch = "corpse_kill")
    public static void aCivetKillLeavesACorpseInsteadOfDrops(GameTestHelper helper) {
        walledFloor(helper);
        CivetEntity civet = hungryCivet(helper, 800);
        Entity rabbit = helper.spawn(EntityType.RABBIT, new Vec3(8.5, 3, 8.5));
        // If it fails, the rabbit's normal drops would lure hungry civets in later batches.
        helper.runAfterDelay(798, () -> {
            rabbit.discard();
            helper.getLevel().getEntitiesOfClass(ItemEntity.class, arenaBox(helper).inflate(4)).forEach(Entity::discard);
        });

        helper.succeedWhen(() -> {
            BlockPos found = null;
            for (int x = 1; x <= 10 && found == null; x++) {
                for (int z = 1; z <= 10 && found == null; z++) {
                    for (int y = 2; y <= 4 && found == null; y++) {
                        BlockPos p = new BlockPos(x, y, z);
                        if (helper.getBlockState(p).is(DOCBlocks.CORPSE.get())) found = p;
                    }
                }
            }
            helper.assertTrue(found != null, "waiting for the civet to kill the rabbit, rabbit alive: " + rabbit.isAlive());
            CorpseBlockEntity be = (CorpseBlockEntity) helper.getBlockEntity(found);
            helper.assertTrue(DoCTags.EntityTypes.LAGOMORPH.equals(be.category()), "corpse category " + be.category());
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, arenaBox(helper)).isEmpty(),
                    "the rabbit dropped items as well");
            civet.discard();
            helper.setBlock(found, Blocks.AIR);
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 600, batch = "corpse_eat")
    public static void anyMeatEaterFeedsFromACorpse(GameTestHelper helper) {
        walledFloor(helper);
        CorpseBlockEntity be = corpse(helper, new BlockPos(7, 3, 6), DoCTags.EntityTypes.SMALL_RODENT, 3,
                helper.getLevel().getGameTime());
        CivetEntity civet = hungryCivet(helper, 600);

        helper.succeedWhen(() -> {
            helper.assertTrue(be.portions() == 2, "waiting for one meal, portions " + be.portions());
            helper.assertTrue(civet.getHungerPercentage() > 90, "hunger after the meal " + civet.getHungerPercentage());
            civet.discard();
            helper.setBlock(new BlockPos(7, 3, 6), Blocks.AIR);
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 200, batch = "corpse_misc")
    public static void aCorpseRotsAway(GameTestHelper helper) {
        walledFloor(helper);
        long now = helper.getLevel().getGameTime();
        long rotTicks = 20L * com.evirapo.diversityofcritters.common.config.DOCServerConfig.ecosystem().corpseRotSeconds();
        BlockPos pos = new BlockPos(5, 3, 5);
        corpse(helper, pos, DoCTags.EntityTypes.SMALL_BIRD, 3, now - rotTicks + 40);
        helper.succeedWhen(() -> helper.assertBlockNotPresent(DOCBlocks.CORPSE.get(), pos));
    }

    @GameTest(template = ARENA, batch = "corpse_misc")
    public static void eatingTheLastPortionRemovesTheCorpse(GameTestHelper helper) {
        walledFloor(helper);
        BlockPos pos = new BlockPos(5, 3, 5);
        CorpseBlockEntity be = corpse(helper, pos, DoCTags.EntityTypes.SMALL_RODENT, 1, helper.getLevel().getGameTime());
        helper.assertTrue(be.takePortion(), "the last portion could not be eaten");
        helper.assertBlockNotPresent(DOCBlocks.CORPSE.get(), pos);
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "corpse_misc")
    public static void aCorpseNeedsDryGroundNearby(GameTestHelper helper) {
        for (int x = 1; x <= 10; x++) {
            for (int z = 1; z <= 10; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
                for (int y = 2; y <= 5; y++) helper.setBlock(x, y, z, x <= 7 ? Blocks.WATER : Blocks.AIR);
                if (x > 7) helper.setBlock(x, 2, z, Blocks.STONE);
            }
        }
        helper.assertTrue(CorpseEvents.findCorpseSpot(helper.getLevel(), helper.absolutePos(new BlockPos(3, 3, 5))) == null,
                "found a corpse spot in open water");
        BlockPos dry = helper.absolutePos(new BlockPos(9, 3, 5));
        helper.assertTrue(dry.equals(CorpseEvents.findCorpseSpot(helper.getLevel(), dry)),
                "no corpse spot right where the prey died on dry ground");
        BlockPos shore = CorpseEvents.findCorpseSpot(helper.getLevel(), helper.absolutePos(new BlockPos(7, 3, 5)));
        helper.assertTrue(shore != null, "no corpse spot on the shore next to the water");
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "corpse_misc")
    public static void aCorpseKeepsItsDataWhenSaved(GameTestHelper helper) {
        walledFloor(helper);
        BlockPos pos = new BlockPos(5, 3, 5);
        CorpseBlockEntity be = corpse(helper, pos, DoCTags.EntityTypes.LAGOMORPH, 2, 12345L);
        CompoundTag tag = be.saveWithoutMetadata();
        CorpseBlockEntity copy = new CorpseBlockEntity(helper.absolutePos(pos), helper.getBlockState(pos));
        copy.load(tag);
        helper.assertTrue(DoCTags.EntityTypes.LAGOMORPH.equals(copy.category()), "category " + copy.category());
        helper.assertTrue(copy.portions() == 2, "portions " + copy.portions());
        helper.assertTrue(copy.bornGameTime() == 12345L, "born " + copy.bornGameTime());
        helper.setBlock(pos, Blocks.AIR);
        helper.succeed();
    }
}
