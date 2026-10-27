package com.evirapo.diversityofcritters.common.entity.util;

import com.evirapo.diversityofcritters.common.entity.ai.CritterNavigation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Elegir objetivos (bowls, digboxes, troncos) a los que el critter puede llegar andando.
 *
 * <p>"Llegar" es {@code path.canReach()}, no {@code path != null}: cuando no hay camino,
 * {@code createPath} devuelve uno parcial hasta el punto más cercano, que con una pared en medio es
 * la propia pared. Los goals que se conformaban con eso dejaban al critter empujándola sin fin.
 *
 * <p>Lo que cuenta como camino lo decide el navegador del mob, escalada incluida (ver
 * {@code DiverseCritter.canClimb()}).
 */
public final class Reachability {

    /**
     * Cuántos candidatos, del más cercano al más lejano, se prueban por búsqueda. Cada prueba es un
     * pathfinding completo; con más de unos pocos objetivos inalcanzables a la vez, el siguiente
     * barrido vuelve a intentarlo.
     */
    public static final int MAX_PATH_CHECKS = 3;

    private Reachability() {
    }

    public static boolean canReach(Mob mob, BlockPos target, int accuracy, float range) {
        Path path = mob.getNavigation() instanceof CritterNavigation nav
                ? nav.createPathWithin(target, accuracy, range)
                : mob.getNavigation().createPath(target, accuracy);
        return path != null && path.canReach();
    }

    /**
     * El candidato más cercano cuyo {@code pathTarget} se alcanza, o null. {@code pathTarget} da la
     * casilla a la que hay que caminar (la propia para un bowl, la de al lado para un tronco); si
     * devuelve null el candidato se salta sin gastar prueba.
     */
    public static @Nullable BlockPos nearestReachable(Mob mob, Collection<BlockPos> candidates,
                                                      Function<BlockPos, BlockPos> pathTarget,
                                                      int accuracy, float range) {
        BlockPos origin = mob.blockPosition();
        List<BlockPos> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingDouble(origin::distSqr));

        int checks = 0;
        for (BlockPos candidate : sorted) {
            if (checks >= MAX_PATH_CHECKS) break;
            BlockPos target = pathTarget.apply(candidate);
            if (target == null) continue;
            checks++;
            if (canReach(mob, target, accuracy, range)) return candidate;
        }
        return null;
    }
}
