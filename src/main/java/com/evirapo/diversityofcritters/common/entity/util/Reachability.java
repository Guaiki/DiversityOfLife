package com.evirapo.diversityofcritters.common.entity.util;

import com.evirapo.diversityofcritters.common.entity.ai.CritterNavigation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
     * Cuántos candidatos sin descartar, del más cercano al más lejano, se prueban por búsqueda. Cada
     * prueba es un pathfinding completo; los que fallan se recuerdan en {@link Unreachable}, así que el
     * siguiente barrido sigue por los de detrás en vez de repetir los mismos.
     */
    public static final int MAX_PATH_CHECKS = 3;

    private Reachability() {
    }

    /** Si ahora se puede trazar una ruta. En pleno salto o caída no, y eso no dice nada del objetivo. */
    public static boolean canPathNow(Mob mob) {
        return !(mob.getNavigation() instanceof CritterNavigation nav) || nav.canPathNow();
    }

    /** Sin tocar la navegación en curso (ver {@link CritterNavigation#findPathWithin}). */
    public static boolean canReach(Mob mob, BlockPos target, int accuracy, float range) {
        Path path = mob.getNavigation() instanceof CritterNavigation nav
                ? nav.findPathWithin(target, accuracy, range)
                : mob.getNavigation().createPath(target, accuracy);
        return path != null && path.canReach();
    }

    public enum Repath {
        /** Ruta completa, en marcha. */
        MOVING,
        /** No se puede trazar ahora (en el aire): seguir con la ruta que hubiera y probar luego. */
        WAIT,
        /** La ruta ya no llega: algo se ha puesto en medio. */
        UNREACHABLE
    }

    /** Echar a andar hacia {@code target}, para arrancar o para recalcular a mitad de camino. */
    public static Repath repath(Mob mob, BlockPos target, int accuracy, float range, double speed) {
        if (!canPathNow(mob)) return Repath.WAIT;
        PathNavigation navigation = mob.getNavigation();
        Path path;
        if (navigation instanceof CritterNavigation nav) {
            path = nav.createPathWithin(target, accuracy, range);
        } else {
            navigation.stop();
            path = navigation.createPath(target, accuracy);
        }
        if (path == null || !path.canReach()) return Repath.UNREACHABLE;
        navigation.moveTo(path, speed);
        return Repath.MOVING;
    }

    /**
     * El candidato más cercano cuyo {@code pathTarget} se alcanza, o null. {@code pathTarget} da la
     * casilla a la que hay que caminar (la propia para un bowl, la de al lado para un tronco); si
     * devuelve null el candidato se salta sin gastar prueba. Los que se saltan por estar en
     * {@code unreachable} tampoco gastan prueba, y los que fallan se añaden ahí.
     */
    public static @Nullable BlockPos nearestReachable(Mob mob, Collection<BlockPos> candidates,
                                                      Function<BlockPos, BlockPos> pathTarget,
                                                      int accuracy, float range, Unreachable unreachable) {
        // En el aire no se puede comprobar nada, y apuntarlo como inalcanzable sería mentira.
        if (!canPathNow(mob)) return null;

        long now = mob.level().getGameTime();
        BlockPos origin = mob.blockPosition();
        List<BlockPos> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingDouble(origin::distSqr));

        int checks = 0;
        for (BlockPos candidate : sorted) {
            if (checks >= MAX_PATH_CHECKS) break;
            if (unreachable.contains(candidate, now)) continue;
            BlockPos target = pathTarget.apply(candidate);
            if (target == null) continue;
            checks++;
            if (canReach(mob, target, accuracy, range)) return candidate;
            unreachable.add(candidate, now);
        }
        return null;
    }

    /**
     * Objetivos que no se alcanzaron hace poco, uno por goal. Se olvidan solos al cabo de
     * {@link #FORGET_AFTER_TICKS}, por si han quitado la pared o el critter se ha movido.
     */
    public static final class Unreachable {

        public static final int FORGET_AFTER_TICKS = 200;

        private final Map<BlockPos, Long> forgetAt = new HashMap<>();

        public boolean contains(BlockPos pos, long now) {
            this.forgetAt.values().removeIf(tick -> tick <= now);
            return this.forgetAt.containsKey(pos);
        }

        public void add(BlockPos pos, long now) {
            this.forgetAt.put(pos.immutable(), now + FORGET_AFTER_TICKS);
        }
    }
}
