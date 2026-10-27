package com.evirapo.diversityofcritters.gametest;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.common.block.DOCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

/** What the dig box holds, headless: {@code gradlew runGameTestServer}. */
@GameTestHolder(DiversityOfCritters.MODID)
@PrefixGameTestTemplate(false)
public final class DigBoxGameTests {

    private DigBoxGameTests() {
    }

    @GameTest(template = "mantle_arena")
    public static void aDigBoxAcceptsAnyItem(GameTestHelper helper) {
        BlockPos pos = new BlockPos(5, 3, 5);
        helper.setBlock(pos, DOCBlocks.DIG_BOX.get());
        IItemHandler box = helper.getBlockEntity(pos).getCapability(ForgeCapabilities.ITEM_HANDLER)
                .orElseThrow(IllegalStateException::new);

        helper.assertTrue(box.insertItem(0, new ItemStack(Items.DIRT), false).isEmpty(), "the dig box refused dirt");
        helper.assertTrue(box.insertItem(1, new ItemStack(Items.DIAMOND_SWORD), false).isEmpty(),
                "the dig box refused a sword");
        helper.succeed();
    }
}
