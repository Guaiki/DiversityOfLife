package com.evirapo.diversityofcritters.common.entity.custom.base;

import com.evirapo.diversityofcritters.common.entity.util.CritterClaims;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Critter que se afila las uñas contra un tronco.
 *
 * <p>Misma justificación que {@link IDiggingCritter}: los goals de cadena sólo pueden tiparse
 * {@code PhasedActivityGoal<DiverseCritter>}, así que la capacidad de la especie viaja por interfaz
 * y no por un {@code instanceof} que fallaría en silencio.
 *
 * <p>Va aparte de {@link IDiggingCritter} a propósito. Las dos son actividades de enriquecimiento,
 * pero no van juntas: un bicho puede cavar sin arañar troncos y al revés, y fundirlas en una sola
 * interfaz obligaría a implementar métodos vacíos.
 *
 * <p>El flag debe estar sincronizado: lo lee la {@code playCondition} de {@code scratch_loop}.
 */
public interface IScratchingCritter {

    boolean isScratching();

    void setScratching(boolean scratching);

    /**
     * Tronco que este critter ha apartado para aranarse. {@code null} cuando no va a ninguno.
     *
     * <p>Misma idea que {@link IDiggingCritter#getDigTarget()}: es una reserva que se pone al elegir
     * destino y la lee {@link CritterClaims}, para que dos critters no se suban al mismo tronco.
     */
    @Nullable BlockPos getScratchTarget();

    void setScratchTarget(@Nullable BlockPos pos);
}
