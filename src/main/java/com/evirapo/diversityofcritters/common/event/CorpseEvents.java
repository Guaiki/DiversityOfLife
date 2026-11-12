package com.evirapo.diversityofcritters.common.event;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import com.evirapo.diversityofcritters.common.block.DOCBlocks;
import com.evirapo.diversityofcritters.common.block.entity.CorpseBlockEntity;
import com.evirapo.diversityofcritters.common.config.DOCServerConfig;
import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.evirapo.diversityofcritters.misc.tags.DoCTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Prey killed by a predator leaves a corpse instead of its drops. Kills by players, or of animals
 * the predator does not hunt, drop as usual. With no dry spot within two blocks (prey killed in open
 * water) the normal drops fall too.
 */
@Mod.EventBusSubscriber(modid = DiversityOfCritters.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CorpseEvents {

    private static final int SEARCH_RADIUS = 2;

    private CorpseEvents() {
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        Level level = victim.level();
        if (level.isClientSide()) return;
        if (!(event.getSource().getEntity() instanceof DiverseCritter hunter)) return;
        if (victim.getType() == hunter.getType() || !hunter.huntsCategoryOf(victim.getType())) return;
        if (victim instanceof TamableAnimal tamable && tamable.isTame()) return;

        TagKey<EntityType<?>> category = DoCTags.EntityTypes.categoryOf(victim.getType());
        if (category == null) return;
        BlockPos spot = findCorpseSpot(level, victim.blockPosition());
        if (spot == null) return;

        level.setBlock(spot, DOCBlocks.CORPSE.get().defaultBlockState(), 3);
        if (level.getBlockEntity(spot) instanceof CorpseBlockEntity corpse) {
            corpse.init(category, DOCServerConfig.ecosystem().corpsePortions(), level.getGameTime());
            event.setCanceled(true);
        }
    }

    /**
     * Where to put the corpse: where the prey died, or the nearest spot within two blocks that is
     * free (air or something replaceable like grass), dry, and on firm ground. Null if there is none.
     */
    @Nullable
    public static BlockPos findCorpseSpot(Level level, BlockPos origin) {
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-SEARCH_RADIUS, -1, -SEARCH_RADIUS),
                origin.offset(SEARCH_RADIUS, 1, SEARCH_RADIUS))) {
            candidates.add(p.immutable());
        }
        candidates.sort(Comparator.comparingDouble(origin::distSqr));
        for (BlockPos p : candidates) {
            BlockState state = level.getBlockState(p);
            BlockPos below = p.below();
            if (state.canBeReplaced() && level.getFluidState(p).isEmpty()
                    && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                return p;
            }
        }
        return null;
    }
}
