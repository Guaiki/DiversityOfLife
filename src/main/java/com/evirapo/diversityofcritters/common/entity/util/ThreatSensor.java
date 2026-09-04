package com.evirapo.diversityofcritters.common.entity.util;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.mojang.logging.LogUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Detecta amenazas alrededor de un {@link DiverseCritter} y decide si está tranquilo, alerta o
 * huyendo. Es el cerebro del sistema; los goals son las manos.
 *
 * <p><b>Por qué vive en la entidad y no en un goal.</b> El sueño necesita detectar amenazas
 * <i>mientras es él quien retiene al bicho</i>: {@code SleepBehaviorGoal} está en prioridad 1 y un
 * goal de alerta nunca llegaría a ejecutarse con el critter dormido. Si la detección viviera dentro
 * de un goal habría que duplicarla ahí, con sus propios radios y su propio LoS, y las dos copias se
 * desincronizarían a la primera.
 *
 * <p>Cuatro consumidores leen este objeto: los dos goals, el goal de sueño y {@code /doc_wariness}.
 * Una sola fuente de verdad.
 *
 * <p>Sólo servidor. Se tickea desde {@code DiverseCritter.aiStep()}, que corre <b>todos</b> los
 * ticks — a diferencia de la pasada del {@code GoalSelector}, que va en ticks alternos. Por eso el
 * temporizador de alerta puede contar en ticks de juego de verdad.
 */
public final class ThreatSensor {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Cada cuántos ticks se rehace el escaneo. El temporizador sigue contando todos los ticks, así
     * que la granularidad sólo afecta a cuándo se <i>entera</i> de un cambio: sobre 80 ticks de
     * dwell son un 6 % de error, imperceptible. Y quien entre lo bastante rápido como para colarse
     * entre dos escaneos ya está en la zona roja, que dispara igual.
     */
    private static final int SCAN_INTERVAL = 5;

    /** Número de partículas por circunferencia de debug. */
    private static final int RING_POINTS = 48;

    public enum State { CALM, ALERT, FLEE }

    private final DiverseCritter mob;

    private State state = State.CALM;
    private int dwellTicksLeft;
    private int fleeTicks;
    /**
     * Tick absoluto del último escaneo con amenaza. Arranca muy negativo para que la resta del
     * cooldown de re-dormirse no bloquee el primer sueño de un critter recién aparecido.
     */
    private int lastThreatTick = -1_000_000;
    private boolean loggedRadiusWarning;

    // --- resultado del ultimo escaneo ---
    private @Nullable LivingEntity nearest;
    private double nearestDistSq = Double.MAX_VALUE;
    private boolean anyInner;
    private boolean anyOuter;
    private boolean anySprinting;
    private boolean allCrouching;

    public ThreatSensor(DiverseCritter mob) {
        this.mob = mob;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CICLO
    // ─────────────────────────────────────────────────────────────────────────

    public void tick() {
        if (!this.isEnabled()) {
            this.reset();
            return;
        }
        if (this.mob.tickCount % SCAN_INTERVAL == 0) {
            this.scan();
        }
        this.advance();
        this.mob.setAlert(this.state == State.ALERT);
        this.mob.setFleeing(this.state == State.FLEE);
    }

    /**
     * El sistema entero se apaga si el critter está domesticado, si su especie no ha hecho opt-in, o
     * si es recién nacido — un recién nacido no supera a nada corriendo, y su cadena de llanto
     * (prioridad 2) importa más.
     */
    public boolean isEnabled() {
        return !this.mob.level().isClientSide()
                && this.mob.isAlive()
                && this.mob.isWary()
                && !this.mob.isTame()
                && !this.mob.isNewborn();
    }

    private void reset() {
        if (this.state != State.CALM) {
            this.state = State.CALM;
            this.mob.setAlert(false);
            this.mob.setFleeing(false);
        }
        this.nearest = null;
        this.nearestDistSq = Double.MAX_VALUE;
        this.anyInner = false;
        this.anyOuter = false;
        this.anySprinting = false;
        this.allCrouching = false;
        this.fleeTicks = 0;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ESCANEO
    // ─────────────────────────────────────────────────────────────────────────

    private void scan() {
        this.nearest = null;
        this.nearestDistSq = Double.MAX_VALUE;
        this.anyInner = false;
        this.anyOuter = false;
        this.anySprinting = false;

        boolean sawAny = false;
        boolean everyoneCrouching = true;

        // Solo Player.class porque es lo barato y va indexado por chunk. Ampliar a depredadores
        // exige tocar aqui ademas de sobrescribir isThreat(); ver el javadoc de aquel.
        for (Player player : this.mob.level().getEntitiesOfClass(
                Player.class, this.mob.getBoundingBox().inflate(this.mob.outerThreatRadius()))) {
            if (!this.mob.isThreat(player)) continue;
            // El LoS aplica tambien a la zona roja a proposito: alguien pegado al otro lado de una
            // pared no es una amenaza, y es lo que hace que acercarse por detras de cobertura
            // funcione. getSensing() cachea por tick, asi que esto es barato.
            if (!this.mob.getSensing().hasLineOfSight(player)) continue;

            this.consider(player, false);
            sawAny = true;
            if (!player.isCrouching()) everyoneCrouching = false;
        }

        // Quien te ha pegado cuenta ignorando radios y LoS: sin esto, una flecha desde 30 bloques
        // dejaria al critter quieto y el sistema pareceria roto en el caso mas obvio. Entra en la
        // lista normal en vez de ser una transicion aparte, asi la huida termina por la regla de
        // safeDistance y no hay un segundo camino de salida que mantener.
        LivingEntity attacker = this.mob.getLastHurtByMob();
        if (attacker != null && attacker.isAlive()) {
            this.consider(attacker, true);
            sawAny = true;
            everyoneCrouching = false;
        }

        this.anyOuter = sawAny;
        // Solo se congela el temporizador si TODAS estan agachadas. Si bastara con una, dos
        // jugadores romperian el sistema: uno se agacha y el otro entra corriendo con impunidad.
        this.allCrouching = sawAny && everyoneCrouching;
        if (sawAny) {
            this.lastThreatTick = this.mob.tickCount;
        }
    }

    private void consider(LivingEntity threat, boolean forceInner) {
        double distSq = this.mob.distanceToSqr(threat);
        if (distSq < this.nearestDistSq) {
            this.nearestDistSq = distSq;
            this.nearest = threat;
        }
        double inner = this.mob.innerThreatRadius();
        if (forceInner || distSq <= inner * inner) {
            this.anyInner = true;
        }
        if (threat.isSprinting()) {
            this.anySprinting = true;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  MAQUINA DE ESTADOS
    // ─────────────────────────────────────────────────────────────────────────

    private void advance() {
        switch (this.state) {
            case CALM -> {
                if (this.anyInner) this.enterFlee();
                else if (this.anyOuter) this.enterAlert();
            }
            case ALERT -> {
                if (!this.anyOuter) {
                    this.state = State.CALM;
                } else if (this.anyInner || this.anySprinting) {
                    this.enterFlee();
                } else if (!this.allCrouching && --this.dwellTicksLeft <= 0) {
                    this.enterFlee();
                }
            }
            case FLEE -> {
                double safe = this.effectiveSafeDistance();
                boolean clear = this.nearest == null || this.nearestDistSq > safe * safe;
                // maxFleeTicks cubre al acorralado, que nunca alcanzaria safeDistance y huiria para
                // siempre. Al agotarse se rinde.
                if (clear || ++this.fleeTicks >= this.mob.maxFleeTicks()) {
                    this.state = State.CALM;
                    this.fleeTicks = 0;
                }
            }
        }
    }

    private void enterAlert() {
        this.state = State.ALERT;
        this.dwellTicksLeft = this.mob.alertDwellTicks();
    }

    private void enterFlee() {
        this.state = State.FLEE;
        this.fleeTicks = 0;
    }

    /**
     * El anti-loop es histéresis, no un cooldown: al terminar la huida la amenaza ya está fuera de
     * la zona amarilla, así que no puede re-alertarse en el acto. Un cooldown temporal haría lo
     * mismo pero dejando una ventana en la que el bicho te ignora.
     *
     * <p>La invariante <b>no</b> se comprueba con {@code assert}: Java las desactiva en runtime
     * salvo que se arranque con {@code -ea}, así que una configuración mal puesta pasaría
     * desapercibida justo en producción. Se loguea una vez y se clampa.
     */
    private double effectiveSafeDistance() {
        double safe = this.mob.safeDistance();
        double outer = this.mob.outerThreatRadius();
        if (safe > outer) return safe;

        if (!this.loggedRadiusWarning) {
            this.loggedRadiusWarning = true;
            LOGGER.error("{}: safeDistance ({}) debe ser mayor que outerThreatRadius ({}). "
                            + "Se clampa a {} para evitar el bucle huida-alerta-huida.",
                    this.mob.getType().getDescriptionId(), safe, outer, outer * 1.5D);
        }
        return outer * 1.5D;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  LECTURA
    // ─────────────────────────────────────────────────────────────────────────

    public State state() { return this.state; }

    public boolean isAlerted() { return this.state == State.ALERT; }

    public boolean isFleeing() { return this.state == State.FLEE; }

    /** Hay alguna amenaza detectada ahora mismo. Lo usa el despertar forzado del sueño. */
    public boolean hasThreat() { return this.isEnabled() && this.anyOuter; }

    /** Tick absoluto del último escaneo con amenaza; el sueño resta contra este valor. */
    public int lastThreatTick() { return this.lastThreatTick; }

    public @Nullable LivingEntity nearestThreat() { return this.nearest; }

    public double nearestDistance() {
        return this.nearest == null ? -1.0D : Math.sqrt(this.nearestDistSq);
    }

    public int dwellTicksLeft() { return this.dwellTicksLeft; }

    public boolean isTimerFrozen() { return this.allCrouching; }

    /** Dibuja las dos circunferencias con partículas. Sólo lo llama el comando de debug. */
    public void spawnDebugRings(ServerLevel level) {
        this.ring(level, this.mob.innerThreatRadius(), true);
        this.ring(level, this.mob.outerThreatRadius(), false);
    }

    private void ring(ServerLevel level, double radius, boolean inner) {
        for (int i = 0; i < RING_POINTS; i++) {
            double angle = (Math.PI * 2.0D / RING_POINTS) * i;
            level.sendParticles(
                    inner ? ParticleTypes.FLAME : ParticleTypes.HAPPY_VILLAGER,
                    this.mob.getX() + Math.cos(angle) * radius,
                    this.mob.getY() + 0.2D,
                    this.mob.getZ() + Math.sin(angle) * radius,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }
}
