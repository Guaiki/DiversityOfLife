package com.evirapo.diversityofcritters.mixin.compat;

import com.evirapo.diversityofcritters.common.temperature.AirConditionerManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Serene Seasons derrite nieve y hielo en biomas cálidos (RandomUpdateHandler.meltInChunk).
 * Este mixin protege las posiciones dentro del área de un Air Conditioner en modo
 * FREEZING/COLD para que la nieve/hielo generados por el bloque persistan (ej. en desierto).
 * Solo se aplica cuando Serene Seasons está cargado (ver DiversityMixinPlugin).
 */
@Mixin(targets = "sereneseasons.season.RandomUpdateHandler")
public class SereneSeasonsMeltMixin {

    @Redirect(method = "meltInChunk", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private static boolean doc$protectSnowFromSeasonalMelt(ServerLevel level, BlockPos pos, BlockState newState) {
        if (AirConditionerManager.isProtectedFromMelt(level, pos)) {
            return false;
        }
        return level.setBlockAndUpdate(pos, newState);
    }

    @Redirect(method = "meltInChunk", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/IceBlock;melt(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"))
    private static void doc$protectIceFromSeasonalMelt(IceBlock block, BlockState state, Level level, BlockPos pos) {
        if (AirConditionerManager.isProtectedFromMelt(level, pos)) {
            return;
        }
        // Réplica de IceBlock.melt (es protected, no invocable desde aquí)
        if (level.dimensionType().ultraWarm()) {
            level.removeBlock(pos, false);
        } else {
            level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
            level.neighborChanged(pos, Blocks.WATER, pos);
        }
    }
}
