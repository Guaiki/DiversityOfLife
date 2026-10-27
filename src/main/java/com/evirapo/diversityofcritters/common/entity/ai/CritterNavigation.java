package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Set;

public class CritterNavigation extends WallClimberNavigation {

    protected final DiverseCritter critter;

    public CritterNavigation(Mob mob, Level level) {
        super(mob, level);
        this.critter = (DiverseCritter) mob;
    }

    /**
     * Anula el "empujon fantasma" de {@link WallClimberNavigation}.
     *
     * <p>Sin ruta activa, su {@code tick()} sigue llamando cada tick a
     * {@code moveControl.setWantedPosition(pathToPosition, speedModifier)}. En la arana eso se
     * resuelve solo: su MoveControl la lleva hasta ese punto y el propio {@code tick()} pone el campo
     * a null al llegar. En los critters no, porque su MoveControl ignora la posicion deseada mientras
     * la navegacion este terminada — asi que no llegan nunca y el empujon no caduca. Y
     * {@code pathToPosition} es privado y <b>no lo limpia {@code stop()}</b>, asi que tampoco se
     * puede cortar desde fuera.
     *
     * <p>Lo unico que conseguia era dejar la operacion del MoveControl en {@code MOVE_TO} con el
     * ultimo {@code speedModifier}. Y {@code customServerAiStep()} de las tres especies deriva el
     * sprint de {@code hasWanted()} y de esa velocidad, leyendolos <i>antes</i> de que
     * {@code moveControl.tick()} los limpie: el critter se quedaba parado con particulas de correr
     * hasta que otro goal conseguia una ruta de verdad y pisaba el modificador. Se destapo al
     * terminar una huida, que es lo primero que lo mueve a 1.6 y luego lo para en seco.
     *
     * <p>Se replica lo unico que {@code PathNavigation.tick()} hace sin ruta —el contador y la
     * recomputacion diferida— para no perderlo por el camino.
     */
    @Override
    public void tick() {
        if (this.isDone()) {
            ++this.tick;
            if (this.hasDelayedRecomputation) {
                this.recomputePath();
            }
            return;
        }
        super.tick();
    }

    @Override
    protected PathFinder createPathFinder(int pMaxVisitedNodes) {
        // this.mob y no this.critter: esto corre en el constructor de PathNavigation, antes de que
        // se asigne this.critter.
        this.nodeEvaluator = new CritterNodeEvaluator(this.mob instanceof DiverseCritter c && c.canClimb());
        this.nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(this.nodeEvaluator, pMaxVisitedNodes);
    }

    /**
     * Como {@code createPath(BlockPos, int)} pero con un radio propio en vez del FOLLOW_RANGE del mob
     * (16), que se queda corto para un objetivo en la esquina del radio de búsqueda o tras un rodeo.
     */
    public @Nullable Path createPathWithin(BlockPos target, int accuracy, float range) {
        return this.createPath(ImmutableSet.of(target), 8, false, accuracy, range);
    }

    @Override
    protected Path createPath(Set<BlockPos> pTargets, int pRegionOffset,
                              boolean pOffsetUpward, int pAccuracy, float pFollowRange) {
        for (BlockPos target : pTargets) {
            if (!this.level.hasChunk(target.getX() >> 4, target.getZ() >> 4)) return null;
        }
        BlockPos self = this.mob.blockPosition();
        if (!this.level.hasChunk(self.getX() >> 4, self.getZ() >> 4)) return null;
        return super.createPath(pTargets, pRegionOffset, pOffsetUpward, pAccuracy, pFollowRange);
    }

    @Override
    protected boolean canUpdatePath() {
        return this.critter.isClimbing()
                || this.mob.onGround()
                || this.isInLiquid()
                || this.mob.isPassenger();
    }

    @Override
    protected double getGroundY(Vec3 pVec) {
        if (this.critter.isClimbing()) return pVec.y;
        return super.getGroundY(pVec);
    }

    @Override
    protected Vec3 getTempMobPos() {
        if (this.critter.isClimbing()) return this.mob.position();
        return super.getTempMobPos();
    }
}
