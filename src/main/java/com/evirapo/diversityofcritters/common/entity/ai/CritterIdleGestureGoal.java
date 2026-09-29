package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter.IdleVariant;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Reproduce un gesto puramente cosmético —erguirse, olfatear— cada cierto tiempo mientras el critter
 * está parado sin nada mejor que hacer.
 *
 * <pre>{@code
 * this.goalSelector.addGoal(22, new CritterIdleGestureGoal(this, 120, 120)
 *         .add(IdleVariant.SNIFF_LEFT,  "sniff_left",  25)
 *         .add(IdleVariant.SNIFF_RIGHT, "sniff_right", 25)
 *         .add(IdleVariant.STRETCH,     "stretch",     20));
 * }</pre>
 *
 * <p><b>Sin flags de goal.</b> Un gesto cosmético no mueve al animal a ningún sitio, así que
 * reservar MOVE o LOOK bloquearía comportamientos reales para nada: con la versión anterior, un
 * olfateo de dos segundos le quitaba MOVE al goal de paseo y LOOK al de mirar al jugador, y el
 * critter se quedaba congelado sin mirarte. La única restricción que hace falta es la de estar en
 * reposo, y está en {@link #atRest()}. Consecuencia: la prioridad a la que se registre da igual —
 * sin flags, el {@code GoalSelector} nunca tiene motivo para retenerlo. Se registra el último por
 * legibilidad, no por precedencia.
 *
 * <p><b>Las poses de reposo no van aquí.</b> Sentarse y tumbarse son cadenas de tres fases y sí
 * comprometen al animal, así que viven en {@link CritterIdleVariantGoal} sobre {@code
 * PhasedActivityGoal}, con sus flags y con {@code locksMovement()} en la salida.
 *
 * <p>La duración no se cuenta aquí: {@link #canContinueToUse()} pregunta al animator si el clip
 * sigue sonando, igual que hace {@code GuardedMeleeAttackGoal} con la recuperación de sus ataques.
 * Así el gesto no puede desincronizarse de su animación.
 */
public class CritterIdleGestureGoal extends Goal {

    private record Gesture(IdleVariant variant, String clip, int weight) {}

    private final DiverseCritter mob;
    private final int cooldownTicks;
    private final int cooldownSpreadTicks;

    private final List<Gesture> gestures = new ArrayList<>();
    private int totalWeight;

    private Predicate<DiverseCritter> condition = critter -> true;

    /** Primer {@code tickCount} en que se permite el siguiente gesto. No se persiste. */
    private int nextAllowedTick;
    /** El clip que arrancó este goal, mientras siga sonando. */
    private @Nullable Gesture playing;

    /**
     * @param cooldownTicks       suelo entre dos gestos, medido del arranque de uno al del siguiente
     * @param cooldownSpreadTicks extra aleatorio encima, para que no salgan a metrónomo
     */
    public CritterIdleGestureGoal(@NotNull DiverseCritter mob, int cooldownTicks, int cooldownSpreadTicks) {
        this.mob = mob;
        this.cooldownTicks = cooldownTicks;
        // nextInt exige una cota positiva; un spread de 0 es un "siempre exactamente cada tanto".
        this.cooldownSpreadTicks = Math.max(1, cooldownSpreadTicks);
    }

    /**
     * Añade un gesto al sorteo. Se saca uno al azar en proporción a su peso, así que
     * {@code add(a, 3).add(b, 1)} muestra "a" tres veces más a menudo que "b".
     *
     * <p>Un único cooldown compartido gobierna todo el pool. Esa es la diferencia con registrar un
     * goal por gesto, donde cada uno llevaría su propio temporizador y dos gestos distintos podrían
     * salir seguidos.
     *
     * @param name nombre base de la animación registrada; se reproduce {@code name + "_loop"}
     */
    public @NotNull CritterIdleGestureGoal add(@NotNull IdleVariant variant, @NotNull String name, int weight) {
        if (weight <= 0) {
            throw new IllegalArgumentException("el peso debe ser positivo, llegó " + weight + " para " + name);
        }
        this.gestures.add(new Gesture(variant, name + "_loop", weight));
        this.totalWeight += weight;
        return this;
    }

    /** Filtro extra encima del reposo, para lo específico de cada especie. */
    public @NotNull CritterIdleGestureGoal condition(@NotNull Predicate<DiverseCritter> condition) {
        this.condition = condition;
        return this;
    }

    @Override
    public boolean canUse() {
        return !this.gestures.isEmpty()
                && this.mob.tickCount >= this.nextAllowedTick
                // Deliberadamente fuera de atRest(): en cuanto este goal arranca, el critter YA está
                // en una actividad —la suya—, así que continuar no puede comprobarlo o el gesto se
                // cortaría solo en la siguiente evaluación. Aquí significa "no apiles un gesto encima
                // de un trago o una comida ya en marcha".
                && !this.mob.isPerformingActivity()
                && this.atRest();
    }

    @Override
    public boolean canContinueToUse() {
        return this.playing != null
                && this.mob.animator().isPlaying(this.playing.clip())
                && this.atRest();
    }

    /** Parado y sin nada mejor que hacer. */
    private boolean atRest() {
        return !this.mob.isMoving()
                && this.mob.onGround()
                && this.mob.getTarget() == null
                && this.mob.getLastHurtByMob() == null
                && this.mob.getSleepState() == DiverseCritter.SleepState.AWAKE
                && !this.mob.isInWaterOrBubble()
                && !this.mob.animator().isMovementLocked()
                && this.condition.test(this.mob);
    }

    @Override
    public void start() {
        this.playing = this.pick();
        this.nextAllowedTick = this.mob.tickCount + this.cooldownTicks
                + this.mob.getRandom().nextInt(this.cooldownSpreadTicks);
        // La variante sincronizada es lo que enciende la playCondition del clip, y lo que otros
        // goals leen vía isIdleLocked().
        this.mob.setIdleVariant(this.playing.variant());
        this.mob.animator().play(this.mob.animator().getByName(this.playing.clip()));
    }

    @Override
    public void stop() {
        // Cancelar sólo lo nuestro: para cuando esto corre, el clip puede haber terminado por su
        // cuenta, o algo distinto puede haber arrancado su propia acción mientras tanto. Cancelar a
        // ciegas cortaría una actividad que no tiene nada que ver con este goal.
        if (this.playing != null && this.mob.animator().isPlaying(this.playing.clip())) {
            this.mob.animator().stop(this.mob.animator().getByName(this.playing.clip()));
        }
        if (this.playing != null && this.mob.getIdleVariant() == this.playing.variant()) {
            this.mob.setIdleVariant(IdleVariant.NONE);
        }
        this.playing = null;
    }

    private Gesture pick() {
        if (this.gestures.size() == 1) {
            return this.gestures.get(0);
        }
        int roll = this.mob.getRandom().nextInt(this.totalWeight);
        for (Gesture g : this.gestures) {
            roll -= g.weight();
            if (roll < 0) {
                return g;
            }
        }
        // Inalcanzable: los pesos suman totalWeight y roll arranca por debajo.
        return this.gestures.get(this.gestures.size() - 1);
    }
}
