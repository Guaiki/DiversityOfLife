package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Set;

public class CritterNavigation extends WallClimberNavigation {

    protected final DiverseCritter critter;

    /**
     * El mismo PathFinder que usa la navegación, para las comprobaciones de {@link #findPathWithin}.
     * Sin inicializador a propósito: se asigna en {@link #createPathFinder}, que corre dentro del
     * constructor de PathNavigation, y un inicializador lo pisaría después con null.
     */
    private PathFinder checkPathFinder;

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
        this.checkPathFinder = new PathFinder(this.nodeEvaluator, pMaxVisitedNodes);
        return this.checkPathFinder;
    }

    /** Si ahora mismo se puede trazar una ruta: no en pleno salto o caída (ver {@link #canUpdatePath}). */
    public boolean canPathNow() {
        return this.canUpdatePath();
    }

    /**
     * Una ruta de prueba, sin tocar la navegación. {@code createPath} no vale para comprobar: si el
     * objetivo coincide con el de la ruta en curso devuelve esa ruta (la de seguir a la madre, por
     * ejemplo), y si traza una nueva apunta {@code targetPos} a ella, con lo que el siguiente recálculo
     * llevaba al critter hacia el bowl tapiado que acababa de descartar.
     *
     * <p>Radio propio en vez del FOLLOW_RANGE del mob (16), que se queda corto para un objetivo en la
     * esquina del radio de búsqueda o tras un rodeo.
     */
    public @Nullable Path findPathWithin(BlockPos target, int accuracy, float range) {
        if (!this.canUpdatePath() || this.mob.getY() < this.level.getMinBuildHeight()) return null;
        BlockPos self = this.mob.blockPosition();
        if (!this.level.hasChunk(target.getX() >> 4, target.getZ() >> 4)) return null;
        if (!this.level.hasChunk(self.getX() >> 4, self.getZ() >> 4)) return null;

        int r = (int) (range + 8);
        PathNavigationRegion region = new PathNavigationRegion(this.level, self.offset(-r, -r, -r), self.offset(r, r, r));
        return this.checkPathFinder.findPath(region, this.mob, ImmutableSet.of(target), range, accuracy, 1.0F);
    }

    /**
     * La ruta para echar a andar de verdad. Suelta antes la ruta en curso para que {@code createPath}
     * no la devuelva tal cual, y deja {@code targetPos} apuntando a este objetivo, que es lo que usan
     * los recálculos de vanilla.
     */
    public @Nullable Path createPathWithin(BlockPos target, int accuracy, float range) {
        this.stop();
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
