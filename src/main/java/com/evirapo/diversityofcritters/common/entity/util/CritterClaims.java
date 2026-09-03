package com.evirapo.diversityofcritters.common.entity.util;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.evirapo.diversityofcritters.common.entity.custom.base.IDiggingCritter;
import com.evirapo.diversityofcritters.common.entity.custom.base.IScratchingCritter;
import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reservas de bloque entre critters: qué hoyo, qué caja y qué tronco tiene ya dueño.
 *
 * <p>Sin esto, dos critters con la misma necesidad hacen la misma búsqueda desde sitios parecidos y
 * eligen el mismo bloque, porque ninguna de las dos mira lo que hace la otra. El resultado es que se
 * meten en el mismo hoyo o se suben al mismo tronco y se solapan.
 *
 * <p><b>Se recogen de una vez, no bloque a bloque.</b> Es lo que hace esto viable: la búsqueda de
 * tronco barre 33×3×33 ≈ 3.200 posiciones, y preguntar por las entidades cercanas en cada una sería
 * ruinoso. Se pide un {@link Set} una sola vez antes del barrido y luego cada candidato es un
 * {@code contains}.
 *
 * <p>El caso normal —ningún otro critter cerca— no reserva memoria: se devuelve {@link Set#of()}.
 *
 * <p>La reserva vive en un campo del propio critter ({@code getDigTarget} / {@code getScratchTarget}),
 * no en un registro global. Así no hay nada que limpiar cuando un critter muere o se descarga: deja
 * de estar entre los vecinos y su reserva se va con él.
 */
public final class CritterClaims {

    private CritterClaims() {
    }

    /** Bloques que otros critters cercanos ya han apartado para cavar (tierra o caja). */
    public static Set<BlockPos> digClaims(DiverseCritter seeker, double radius) {
        Set<BlockPos> claimed = null;
        for (DiverseCritter other : nearby(seeker, radius)) {
            if (!(other instanceof IDiggingCritter digger)) continue;
            BlockPos pos = digger.getDigTarget();
            if (pos == null) continue;
            if (claimed == null) claimed = new HashSet<>();
            claimed.add(pos);
        }
        return claimed == null ? Set.of() : claimed;
    }

    /** Troncos que otros critters cercanos ya han apartado para arañarse. */
    public static Set<BlockPos> scratchClaims(DiverseCritter seeker, double radius) {
        Set<BlockPos> claimed = null;
        for (DiverseCritter other : nearby(seeker, radius)) {
            if (!(other instanceof IScratchingCritter scratcher)) continue;
            BlockPos pos = scratcher.getScratchTarget();
            if (pos == null) continue;
            if (claimed == null) claimed = new HashSet<>();
            claimed.add(pos);
        }
        return claimed == null ? Set.of() : claimed;
    }

    /**
     * El radio se pide del doble del de búsqueda: dos critters pueden pelearse por un bloque estando
     * cada uno a un radio de él, o sea a dos radios el uno del otro.
     *
     * <p>Los muertos no cuentan. Un critter que muere a medio camino no llega a soltar su reserva
     * —{@code stop()} puede no correr— y sin este filtro dejaría el bloque bloqueado hasta que su
     * cadáver desapareciera.
     */
    private static List<DiverseCritter> nearby(DiverseCritter seeker, double radius) {
        return seeker.level().getEntitiesOfClass(
                DiverseCritter.class,
                seeker.getBoundingBox().inflate(radius),
                other -> other != seeker && other.isAlive());
    }
}
