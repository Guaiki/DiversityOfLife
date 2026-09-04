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

    /**
     * Cuántos ticks sigue contando una amenaza tras dejar de verla.
     *
     * <p>Sin esto, la opinión del sensor parpadea: un jugador parado justo en el borde del radio
     * entra y sale de la zona por centímetros, y cada parpadeo devolvía el estado a CALM y
     * reiniciaba el temporizador de alerta a su valor completo. Se veía en el debug como
     * {@code dwell left} subiendo de 1 a 37. En juego significaba que quedándote en el borde el
     * critter <b>no huía nunca</b>, porque la cuenta no llegaba a cero.
     *
     * <p>Cubre además el parpadeo de línea de visión —una brizna de hierba entre medias— y el
     * aliasing del intervalo de escaneo.
     */
    private static final int THREAT_MEMORY = 20;

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

    // --- borrador del escaneo en curso; solo se publica si encuentra algo ---
    private @Nullable LivingEntity scanNearest;
    private double scanNearestDistSq = Double.MAX_VALUE;
    private boolean scanInner;
    private boolean scanSprinting;

    // --- ultimo escaneo con amenaza, vigente mientras dure THREAT_MEMORY ---
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
        // Fuera de la ventana de memoria se olvida el ultimo escaneo; dentro, se conserva tal cual.
        // Es lo que evita que la opinion del sensor parpadee. Ver THREAT_MEMORY.
        if (this.mob.tickCount - this.lastThreatTick > THREAT_MEMORY) {
            this.forgetThreats();
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
        this.forgetThreats();
        this.dwellTicksLeft = 0;
        this.fleeTicks = 0;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ESCANEO
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Escanea y <b>sólo publica el resultado si encontró algo</b>. Si no, deja intacto el último
     * escaneo con amenaza: {@link #tick()} decide si sigue dentro de la ventana de memoria o si toca
     * olvidarlo. Así una pérdida momentánea —un paso atrás en el borde del radio, una brizna de
     * hierba tapando la línea de visión— no vuelca el estado a CALM.
     */
    private void scan() {
        this.scanInner = false;
        this.scanSprinting = false;
        this.scanNearest = null;
        this.scanNearestDistSq = Double.MAX_VALUE;

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

            // consider() devuelve false si esta dentro de la CAJA del escaneo pero fuera del radio.
            if (!this.consider(player, false)) continue;
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

        if (!sawAny) {
            return;   // se conserva el escaneo anterior mientras dure la memoria
        }

        this.nearest = this.scanNearest;
        this.nearestDistSq = this.scanNearestDistSq;
        this.anyInner = this.scanInner;
        this.anySprinting = this.scanSprinting;
        this.anyOuter = true;
        // Solo se congela el temporizador si TODAS estan agachadas. Si bastara con una, dos
        // jugadores romperian el sistema: uno se agacha y el otro entra corriendo con impunidad.
        this.allCrouching = everyoneCrouching;
        this.lastThreatTick = this.mob.tickCount;
    }

    /** Se acabó la memoria: el sensor deja de creer que hay alguien. */
    private void forgetThreats() {
        this.nearest = null;
        this.nearestDistSq = Double.MAX_VALUE;
        this.anyInner = false;
        this.anyOuter = false;
        this.anySprinting = false;
        this.allCrouching = false;
    }

    /**
     * @param ignoreRadius para quien nos ha pegado, que cuenta este donde este
     * @return false si esta fuera del radio exterior y por tanto no es amenaza
     *
     * <p><b>El filtro por distancia es obligatorio.</b> {@code getBoundingBox().inflate(r)} da una
     * CAJA, no una esfera, asi que el escaneo devuelve entidades hasta a {@code r*sqrt(3)} en
     * diagonal. Sin esta comprobacion la zona amarilla era un cubo de lado 2r: con r=10 detectaba a
     * 14.1 en horizontal y 17.3 contando altura. Se veia en el debug como
     * {@code threat: Dev at 10.6 [OUTER]} con el radio puesto a 10.
     */
    private boolean consider(LivingEntity threat, boolean ignoreRadius) {
        double distSq = this.mob.distanceToSqr(threat);
        double outer = this.mob.outerThreatRadius();
        if (!ignoreRadius && distSq > outer * outer) {
            return false;
        }

        if (distSq < this.scanNearestDistSq) {
            this.scanNearestDistSq = distSq;
            this.scanNearest = threat;
        }
        double inner = this.mob.innerThreatRadius();
        if (ignoreRadius || distSq <= inner * inner) {
            this.scanInner = true;
        }
        if (threat.isSprinting()) {
            this.scanSprinting = true;
        }
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  MAQUINA DE ESTADOS
    // ─────────────────────────────────────────────────────────────────────────

    private void advance() {
        switch (this.state) {
            case CALM -> {
                // El sprint dispara la huida tambien desde CALM, no solo desde ALERT. Estando solo
                // en ALERT, entrar corriendo daba CALM -> ALERT (un tick) -> FLEE, y ese unico tick
                // bastaba para que CritterAlertGoal arrancara y lanzara alert_enter: se veia como un
                // parpadeo de la pose de alerta justo antes de salir corriendo.
                if (this.anyInner || this.anySprinting) this.enterFlee();
                else if (this.anyOuter) this.enterAlert();
            }
            case ALERT -> {
                if (!this.anyOuter) {
                    this.state = State.CALM;
                    // Si no, el debug muestra "CALM  dwell left: 21t" con el resto de la alerta.
                    this.dwellTicksLeft = 0;
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
        // Si no, el debug muestra "state: FLEE  dwell left: 6t" con el resto de la alerta que se
        // corto al entrar en la zona roja, y parece que el temporizador sigue vivo.
        this.dwellTicksLeft = 0;
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

    /** Tick absoluto del último escaneo con amenaza; sólo para introspección. */
    public int lastThreatTick() { return this.lastThreatTick; }

    /**
     * Ticks desde la última amenaza <i>creída</i>, que es contra lo que resta el cooldown de
     * re-dormirse.
     *
     * <p>No es {@code tickCount - lastThreatTick}: ese sello sólo se pone en los ticks de escaneo, 1
     * de cada {@link #SCAN_INTERVAL}, así que la resta cruda oscila entre 0 y 4 y en el debug se veía
     * el contador temblando entre 5996 y 6000 con un jugador parado delante. Y no se puede sellar
     * cada tick, porque la ventana de {@link #THREAT_MEMORY} se mide contra ese mismo campo y nunca
     * expiraría.
     *
     * <p>Mientras el sensor cree que hay alguien la respuesta es 0 por definición.
     */
    public int ticksSinceThreat() {
        return this.anyOuter ? 0 : this.mob.tickCount - this.lastThreatTick;
    }

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
