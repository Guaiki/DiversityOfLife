package com.evirapo.diversityofcritters.common.block.entity;

import com.evirapo.diversityofcritters.common.block.DOCBlockEntities;
import com.evirapo.diversityofcritters.common.config.DOCServerConfig;
import com.evirapo.diversityofcritters.common.item.DOCItems;
import com.evirapo.diversityofcritters.misc.tags.DoCTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * A corpse's state: the prey category it came from, the portions of meat left and when it appeared.
 * The last portion eaten removes the block; so does age ({@code corpseRotSeconds}), eaten or not.
 */
public class CorpseBlockEntity extends BlockEntity {

    private static final int ROT_CHECK_INTERVAL = 20;

    private @Nullable TagKey<EntityType<?>> category;
    private int portions = -1;
    private long bornGameTime;

    public CorpseBlockEntity(BlockPos pos, BlockState state) {
        super(DOCBlockEntities.CORPSE_BE.get(), pos, state);
    }

    /** Sets it up when it is placed (a kill, a command or a test). */
    public void init(TagKey<EntityType<?>> category, int portions, long bornGameTime) {
        this.category = category;
        this.portions = portions;
        this.bornGameTime = bornGameTime;
        this.setChanged();
    }

    @Nullable
    public TagKey<EntityType<?>> category() { return category; }

    public int portions() { return Math.max(0, portions); }

    public long bornGameTime() { return bornGameTime; }

    /** One portion of meat, removing the corpse when it was the last; empty if nothing is left. */
    public ItemStack takePortion() {
        if (level == null || portions <= 0) return ItemStack.EMPTY;
        portions--;
        setChanged();
        ItemStack meat = new ItemStack(DOCItems.meatFor(category));
        if (portions <= 0) level.removeBlock(worldPosition, false);
        return meat;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CorpseBlockEntity be) {
        if (level.getGameTime() % ROT_CHECK_INTERVAL != 0) return;
        if (be.portions < 0) {
            // Placed without init (e.g. /setblock): a fresh small rodent.
            be.init(DoCTags.EntityTypes.SMALL_RODENT, DOCServerConfig.ecosystem().corpsePortions(), level.getGameTime());
            return;
        }
        long rotTicks = 20L * DOCServerConfig.ecosystem().corpseRotSeconds();
        if (level.getGameTime() - be.bornGameTime >= rotTicks) {
            level.destroyBlock(pos, false);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (category != null) tag.putString("Category", category.location().toString());
        tag.putInt("Portions", portions);
        tag.putLong("Born", bornGameTime);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        category = tag.contains("Category")
                ? TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(tag.getString("Category")))
                : null;
        portions = tag.contains("Portions") ? tag.getInt("Portions") : -1;
        bornGameTime = tag.getLong("Born");
    }
}
