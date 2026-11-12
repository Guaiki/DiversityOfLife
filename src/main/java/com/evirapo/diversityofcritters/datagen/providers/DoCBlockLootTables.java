package com.evirapo.diversityofcritters.datagen.providers;

import com.evirapo.diversityofcritters.common.block.DOCBlocks;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.CopyNbtFunction;
import net.minecraft.world.level.storage.loot.providers.nbt.ContextNbtProvider;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.registries.RegistryObject;

import java.util.Set;

public class DoCBlockLootTables extends BlockLootSubProvider {

    public DoCBlockLootTables() {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags());
    }

    @Override
    protected void generate() {
        // The dig box spills its contents from onRemove, so the block itself is a plain drop.
        this.dropSelf(DOCBlocks.DIG_BOX.get());
        this.dropSelf(DOCBlocks.AIR_CONDITIONER.get());
        this.dropSelf(DOCBlocks.HUMIDIFIER.get());
        this.dropSelf(DOCBlocks.WATERHOLE.get());

        // The bowl keeps what it holds, like a shulker box: the contents ride along in the
        // item's BlockEntityTag and are loaded back into the block entity when it is placed.
        this.add(DOCBlocks.BOWL.get(), this::createBowlDrop);
    }

    private LootTable.Builder createBowlDrop(Block block) {
        return LootTable.lootTable().withPool(this.applyExplosionCondition(block,
                LootPool.lootPool()
                        .setRolls(ConstantValue.exactly(1.0F))
                        .add(LootItem.lootTableItem(block)
                                .apply(CopyNbtFunction.copyData(ContextNbtProvider.BLOCK_ENTITY)
                                        .copy("Items", "BlockEntityTag.Items")
                                        .copy("WaterCharges", "BlockEntityTag.WaterCharges")))));
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return DOCBlocks.BLOCKS.getEntries().stream().map(RegistryObject::get)::iterator;
    }
}
