package com.evirapo.diversityofcritters.common.entity.custom.base;

import com.evirapo.diversityofcritters.common.entity.util.CritterClaims;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Critter capaz de cavar: la civeta en tierra o en una caja de arena, y quien venga detrás.
 *
 * <p><b>Por qué una interfaz y no un flag en {@link DiverseCritter}.</b> Los goals de cadena están
 * obligados a ser {@code PhasedActivityGoal<DiverseCritter>} — la cota de la librería es
 * {@code E extends Mob & Animatable<E>} y es {@code DiverseCritter} quien declara
 * {@code Animatable<DiverseCritter>}, así que ninguna subclase satisface la cota con su propio tipo.
 * Eso deja dos salidas para llegar al flag de la especie: un {@code instanceof} dentro del goal, o
 * esto.
 *
 * <p>El {@code instanceof} ya se probó y falla en silencio: {@code FindDigBoxGoal} estaba tipado
 * {@code DiverseCritter} y hacía {@code if (critter instanceof CivetEntity civet)}, de modo que
 * cualquier otra especie que registrara el goal cavaba <b>sin animación y sin aviso</b>. Con la
 * interfaz, el constructor del goal pide {@code <E extends DiverseCritter & IDiggingCritter>} y
 * registrarlo donde no toca es un error de compilación.
 *
 * <p>El flag debe estar <b>sincronizado</b> ({@code EntityDataAccessor}): lo lee la
 * {@code playCondition} del bucle {@code dig_loop}, que se evalúa en servidor, y lo necesita el
 * cliente para dibujar. Un booleano normal daría la animación sólo en un lado.
 */
public interface IDiggingCritter {

    boolean isDigging();

    void setDigging(boolean digging);

    /**
     * Bloque que este critter ha apartado para cavar -- tierra o caja de arena, da igual: no puede
     * hacer las dos a la vez, asi que una sola ranura basta. {@code null} cuando no esta cavando ni
     * yendo a cavar.
     *
     * <p>Es una <b>reserva</b>, no un estado de animacion: se pone en cuanto el goal elige destino
     * (antes de caminar, no al llegar) y se quita al terminar. {@link CritterClaims} la lee para que
     * dos critters no se metan en el mismo hoyo.
     *
     * <p>No hace falta sincronizarla ni persistirla: solo la consultan busquedas de servidor, y si
     * el critter se descarga la reserva desaparece con el, que es justo lo que se quiere.
     */
    @Nullable BlockPos getDigTarget();

    void setDigTarget(@Nullable BlockPos pos);
}
