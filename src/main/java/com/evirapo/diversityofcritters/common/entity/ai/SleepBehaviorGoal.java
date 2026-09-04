package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter.SleepState;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Ciclo de sueño: preparar → dormir → despertar.
 *
 * <p><b>Por qué no basta con desfasar el umbral.</b> El primer intento daba a cada animal un desfase
 * sobre las 13000 y ahí se acababa. No funcionó por dos motivos: ±600 ticks son 30 s de un día de 20
 * minutos, así que medio grupo caía a menos de siete segundos del umbral; y sobre todo, <b>los dos
 * umbrales eran tiempo absoluto del mundo</b>, así que daba igual cuándo se hubiera dormido cada uno
 * — todos convergían en el mismo reloj para despertar.
 *
 * <p><b>El modelo actual.</b> El tiempo del mundo sólo define una <i>ventana</i> en la que el animal
 * puede dormir. Dentro de ella:
 *
 * <ul>
 *   <li>Se duerme por <b>probabilidad baja</b>, no al cruzar un umbral: la entrada se reparte sobre
 *       minutos en vez de sobre segundos.</li>
 *   <li>Duerme <b>una tanda de duración propia</b>, sorteada en cada ocasión. Al no depender de un
 *       reloj compartido, un grupo que se durmió junto se despierta separado.</li>
 *   <li>Al despertar espera un rato antes de poder volver a dormirse, así que a lo largo de la
 *       ventana hace varias siestas en lugar de un bloque de doce minutos.</li>
 *   <li>El desfase por UUID se mantiene, pero ya sólo mueve los bordes de la ventana: quién puede
 *       empezar antes y quién se queda dormido más allá del amanecer.</li>
 * </ul>
 */
public class SleepBehaviorGoal extends Goal {

    private static final int NIGHT_START = 13000;
    private static final int NIGHT_END = 23000;

    /**
     * Desfase del borde por el que la ventana <b>abre</b>: a partir de ahí cada uno ya puede
     * dormirse. Es por UUID, así que cada individuo tiene siempre su misma hora de acostarse —una
     * rutina— y además se reparte igual tras recargar. Corto, porque el grueso del reparto lo pone
     * {@link #SETTLE_SPREAD}: sumados dan el escalonado total.
     */
    private static final int OPEN_SPREAD = 300;
    /**
     * Desfase del borde por el que la ventana <b>cierra</b>, por UUID. Es la mitad del reparto del
     * despertar; la otra la pone {@link #ROUSE_SPREAD}.
     *
     * <p>El total sigue siendo 900 ticks (45 s), que es lo que se buscaba al subirlo desde 400 —con
     * 400 el grupo despertaba casi a la vez—. Lo que cambia es cómo se reparten: ver
     * {@code ROUSE_SPREAD}.
     */
    private static final int CLOSE_SPREAD = 450;
    /**
     * Adelanto <b>aleatorio</b> del despertar, sorteado en cada tanda de sueño.
     *
     * <p>Es el gemelo de {@link #SETTLE_SPREAD}, y existe por lo mismo. Con sólo el desfase por
     * UUID el despertar salía <i>en fila</i>: el mismo orden y los mismos huecos todos los días,
     * porque el UUID no cambia. Acostarse no se veía así porque encima del UUID lleva un sorteo por
     * ocasión. Ahora los dos bordes tienen la misma estructura y la misma proporción —mitad rutina
     * del individuo, mitad azar del día—, así que el grupo se levanta repartido pero sin que se le
     * note el orden.
     *
     * <p>Se <b>resta</b> del cierre en vez de sumarse después, para que siga valiendo la garantía de
     * que nadie duerme fuera de su periodo: el desfase sólo puede encoger la ventana.
     */
    private static final int ROUSE_SPREAD = 450;
    private static final long SALT_OPEN = 0x5EEDL;
    private static final long SALT_CLOSE = 0xDA7AL;

    /**
     * Retardo hasta acostarse desde que se abre la posibilidad, sorteado <b>una sola vez</b> por
     * apertura y uniforme en {@code [0, SETTLE_SPREAD)}.
     *
     * <p>Sustituye a la probabilidad por evaluación que había antes (0.004 por tick par). Aquello
     * era una geométrica: media de ~25 s, pero <b>sin cota</b>. Con 12 animales el último esperado
     * cae sobre los 62 s y con suerte peor, que es exactamente lo que se veía —dos rezagadas cosa
     * de un minuto por detrás del resto—. Una uniforme acotada reparte igual de bien y garantiza
     * que nadie se descuelgue: el más tardón se acuesta {@code OPEN_SPREAD + SETTLE_SPREAD} = 600
     * ticks (30 s) después del más madrugador.
     *
     * <p>Se rearma al salir de la ventana, así que también escalona a un grupo que aparece de golpe
     * con la ventana ya abierta —soltar 12 de una vez— en lugar de dormirlos a todos en el acto.
     */
    private static final int SETTLE_SPREAD = 300;

    /** Duración de una tanda de sueño, sorteada cada vez. 2400-6000 ticks = 2 a 5 minutos. */
    private static final int MIN_SLEEP_TICKS = 2400;
    private static final int MAX_SLEEP_TICKS = 6000;

    /** Rato despierto entre dos tandas. Sin esto se volvería a dormir al instante, porque sigue
     *  dentro de la ventana. */
    private static final int MIN_AWAKE_TICKS = 600;
    private static final int MAX_AWAKE_TICKS = 1800;

    /** Vigilia tras un despertar forzado (daño o agua). Más larga que la normal: al que despiertas a
     *  golpes no se le vuelve a pegar el sueño en cuatro segundos. */
    private static final int FORCED_WAKE_COOLDOWN = 600;

    private final DiverseCritter entity;

    /**
     * Tick absoluto en el que acaba la fase en curso: la entrada si estamos en {@code PREPARING},
     * la salida si estamos en {@code AWAKENING}.
     *
     * <p><b>Absoluto, no un contador.</b> {@link net.minecraft.world.entity.Mob#serverAiStep()}
     * reparte los mobs en dos grupos por paridad de {@code tickCount + getId()} y sólo llama a
     * {@code goalSelector.tick()} —y por tanto a {@code canContinueToUse()} y a {@code tick()}— en
     * el suyo, salvo que el goal pida {@code requiresUpdateEveryTick()}. Un contador incrementado
     * en {@code tick()} avanza así 1 cada 2 ticks de juego, o sea que cualquier duración escrita
     * como número de ticks acaba durando el doble. El sueño ya usaba tiempo absoluto por este
     * motivo; las dos fases de animación se contaban con un contador y duraban el doble que su
     * clip, dejando al animal congelado en el último frame antes de fundir a idle:
     * {@code KeyframeBlender} clampa al último keyframe cuando la definición no es
     * {@code looping()}. Con tick absoluto, {@link DiverseCritter#getPreparingSleepDuration()} y
     * {@link DiverseCritter#getAwakeningDuration()} son ticks de juego de verdad y pueden
     * igualarse a la longitud del clip.
     */
    private int phaseUntilTick;

    /** Tick absoluto en el que termina esta tanda de sueño. */
    private int sleepUntilTick;
    /** Tick absoluto hasta el que no puede volver a dormirse. */
    private int noSleepUntilTick;

    /** Tick absoluto en el que se le pasa el {@link #SETTLE_SPREAD retardo de acostarse}, y si ya
     *  está sorteado para la apertura en curso. Se desarma al salir de la ventana. */
    private int settleUntilTick;
    private boolean settleArmed;

    /** Cuanto se le adelanta el cierre en esta tanda, en ticks del reloj del mundo. Ver
     *  {@link #ROUSE_SPREAD}. */
    private int rouseEarly;

    /** El jugador ha cambiado el cronotipo desde la ficha y todavia no se ha visto el efecto. Se
     *  mantiene armada hasta que le toque dormir de verdad; no caduca sola porque el periodo
     *  natural siempre llega dentro del mismo dia. Ver {@link #canUse()}. */
    private boolean scheduleForced;
    /** Si el despertar en curso fue forzado en vez de por haber dormido lo suyo. */
    private boolean forcedWake;

    public SleepBehaviorGoal(DiverseCritter entity) {
        this.entity = entity;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // Adopcion de un estado huerfano. `SleepState` se guarda en NBT
        // ({@code DiverseCritter#addAdditionalSaveData}) pero el goal no guarda nada: un bicho que
        // se serializa dormido —al descargarse su chunk, o al salir del mundo— vuelve del disco en
        // SLEEPING sin que ningun goal sea su dueno. Y el unico sitio que devuelve el estado a
        // AWAKE es el stop() de aqui, asi que se quedaba congelado indefinidamente: canMove() y
        // travel() lo clavan al suelo y MobAnimator le deja el bucle de dormir puesto, porque
        // arranca solo cualquier REPEATING cuya condicion se cumpla. Para un nocturno recargado de
        // noche era peor todavia, porque para salir de ahi hacia falta que canUse() acertara, y
        // canUse() exige estar dentro de la ventana: no se despertaba hasta el amanecer siguiente.
        //
        // Va lo primero, por delante incluso de los guardas de estado, porque reclamar la
        // propiedad es justo lo que hace falta cuando el bicho ya esta en una fase de sueno. A
        // partir de ahi canContinueToUse() lo saca por el camino normal —ventana cerrada =>
        // AWAKENING => stop()— y ademas con su animacion de despertar.
        if (this.entity.getSleepState() != SleepState.AWAKE) {
            return true;
        }

        // Cambio de horario desde la ficha (botones sol/luna). La intencion del jugador se GUARDA en
        // scheduleForced, no se gasta aqui: si en el instante de pulsar todavia no le toca dormir
        // —pulsas el sol siendo de dia, con lo que pasa a diurna y se despierta, que es lo
        // correcto— hay que recordarla para cuando llegue la noche. Antes se consumia en el acto y
        // se tiraba, y por eso poner la noche despues no la acostaba.
        //
        // Ademas se tiran los plazos heredados del horario anterior. El importante es
        // noSleepUntilTick: lo acaba de armar el propio despertar que provoco este cambio, y son
        // 30-90 s de espera entre siestas que bloquearian justo la siesta que se esta pidiendo.
        if (this.entity.consumeScheduleChanged()) {
            this.scheduleForced = true;
            this.noSleepUntilTick = 0;
            this.settleArmed = false;
        }

        // Una orden directa mira el periodo natural en crudo, sin el desfase por UUID: ese desfase
        // existe para escalonar a un grupo, y a una mascota que responde a un boton solo le mete
        // espera. Con OPEN_SPREAD la ventana de esta ardilla abria en 13195, asi que un
        // `/time set 13000` la dejaba 10 s parada antes siquiera de empezar a contar.
        if (this.scheduleForced && this.inNaturalSleepPeriod()) {
            this.scheduleForced = false;
            this.settleArmed = false;
            return true;
        }

        if (!this.inSleepWindow()) {
            this.settleArmed = false;   // se rearma en la proxima apertura
            return false;
        }
        if (!this.settleArmed) {
            this.settleUntilTick = this.entity.tickCount + this.entity.getRandom().nextInt(SETTLE_SPREAD);
            this.settleArmed = true;
        }
        if (this.entity.tickCount < this.noSleepUntilTick) {
            return false;
        }
        // Cooldown de re-dormirse tras una amenaza, sin re-armar nada: mientras haya alguien cerca el
        // sensor devuelve 0 y esta puerta no se abre; cuando el ultimo se va, la cuenta arranca sola.
        //
        // Va separado del noSleepUntilTick de arriba a proposito. Aquel cubre despertar por dano o
        // por agua con 600 ticks; subirlo a cinco minutos haria que salpicar con agua bloqueara el
        // sueno cinco minutos. Dos causas distintas, dos plazos distintos.
        //
        // Gateado por isEnabled() para que a un critter recien domesticado —donde el sistema se apaga
        // entero— no le quede una espera heredada de cuando era salvaje.
        if (this.entity.sensor().isEnabled()
                && this.entity.sensor().ticksSinceThreat() < this.entity.reSleepCooldownTicks()) {
            return false;
        }
        if (entity.isInWater() || entity.isVehicle() || entity.isAttacking()
                || entity.isCleaning() || entity.isOrderedToSit()) {
            return false;
        }
        if (this.entity.tickCount < this.settleUntilTick) {
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (entity.getSleepState() == SleepState.AWAKENING) {
            return this.entity.tickCount < this.phaseUntilTick;
        }

        // La amenaza despierta igual que el dano o el agua, y por el mismo camino: AWAKENING retiene
        // al bicho hasta que el clip termina, y solo entonces stop() suelta MOVE y CritterFleeGoal
        // (prioridad 2) se lo lleva. La huida "encadenada despues del despertar" sale del reparto de
        // prioridades; no hay que construirla.
        if (entity.hurtTime > 0 || entity.isInWater() || entity.sensor().hasThreat()) {
            this.startWaking(true);
            return true;
        }

        // Se despierta por lo que llegue antes: haber dormido su tanda, o que se acabe la ventana.
        // Ojo con canKeepSleeping() en vez de inSleepWindow(): ver el javadoc de aquel.
        if (this.entity.tickCount >= this.sleepUntilTick || !this.canKeepSleeping()) {
            this.startWaking(false);
            return true;
        }

        return true;
    }

    @Override
    public void start() {
        entity.getNavigation().stop();
        this.forcedWake = false;
        this.sleepUntilTick = this.entity.tickCount + this.randomBetween(MIN_SLEEP_TICKS, MAX_SLEEP_TICKS);
        this.rouseEarly = this.entity.getRandom().nextInt(ROUSE_SPREAD);

        // Si venimos de adoptar (ver canUse) se respeta la fase con la que se guardo: repetir la
        // entrada desde una pose ya tumbada se veria como un tiron.
        SleepState restored = entity.getSleepState();
        if (restored == SleepState.AWAKE) {
            entity.setSleepState(SleepState.PREPARING);
            this.phaseUntilTick = this.entity.tickCount + entity.getPreparingSleepDuration();
        } else {
            this.phaseUntilTick = this.entity.tickCount + (restored == SleepState.AWAKENING
                    ? entity.getAwakeningDuration()
                    : entity.getPreparingSleepDuration());
        }
    }

    @Override
    public void stop() {
        entity.setSleepState(SleepState.AWAKE);
        this.noSleepUntilTick = this.entity.tickCount + (this.forcedWake
                ? FORCED_WAKE_COOLDOWN
                : this.randomBetween(MIN_AWAKE_TICKS, MAX_AWAKE_TICKS));
        this.forcedWake = false;
    }

    @Override
    public void tick() {
        switch (entity.getSleepState()) {
            case PREPARING -> {
                if (this.entity.tickCount >= this.phaseUntilTick) {
                    entity.setSleepState(SleepState.SLEEPING);
                }
            }
            case SLEEPING -> entity.getNavigation().stop();
            default -> { }   // AWAKENING lo cierra canContinueToUse() al agotar su fase
        }
    }

    private void startWaking(boolean forced) {
        if (entity.getSleepState() != SleepState.AWAKENING) {
            entity.setSleepState(SleepState.AWAKENING);
            this.phaseUntilTick = this.entity.tickCount + entity.getAwakeningDuration();
            this.forcedWake = forced;
        }
    }

    /**
     * ¿Estamos dentro de su franja de sueño? Sólo define cuándo <i>puede</i> dormir, no cuándo lo
     * hace ni cuánto.
     *
     * <p>Escrito por separado para diurnos y nocturnos, en vez de calcular "es de noche" y negarlo,
     * para que los desfases caigan en el borde que les toca: {@code OPEN} donde la ventana empieza y
     * {@code CLOSE} donde acaba. Con la versión negada, para un nocturno los dos salts quedaban
     * intercambiados y el desfase grande caía justo en el despertar.
     *
     * <p><b>Los desfases sólo encogen la ventana, nunca la estiran.</b> Eran simétricos (±spread) y
     * eso metía a medio grupo a dormir <i>fuera</i> de su periodo: para la ardilla, que es nocturna,
     * la ventana abre al amanecer, así que un desfase de −900 la dejaba dormirse 900 ticks antes de
     * que amaneciera — de noche, justo cuando debería estar en plena actividad. Simétrico al otro
     * lado: +400 en el cierre la mantenía dormida 400 ticks después del anochecer. Repartir la hora
     * de acostarse es lo que se buscaba, pero hacia dentro: el que se desfasa se acuesta más tarde y
     * se levanta más pronto, nunca al revés. Así ninguno duerme fuera de su periodo.
     */
    private boolean inSleepWindow() {
        return this.betweenOpenAndClose(this.windowOpen(), this.windowClose());
    }

    /**
     * ¿Puede <b>seguir</b> dormido? Igual que {@link #inSleepWindow()} pero abriendo en el borde
     * natural, sin el desfase por UUID.
     *
     * <p>Son dos preguntas distintas y hay que hacerlas con reglas distintas. El desfase de apertura
     * reparte la <i>hora de acostarse</i>; no puede ser motivo para <i>despertar</i> a quien ya está
     * durmiendo. Cuando compartían función pasaba esto: con la ardilla en diurna y {@code /time set
     * 13000}, la orden del botón la acostaba mirando el periodo natural, y dos ticks después
     * {@code canContinueToUse} la levantaba porque su ventana escalonada no abría hasta 13195. La
     * siesta nacía muerta.
     */
    private boolean canKeepSleeping() {
        long rawOpen = entity.isDiurnal() ? NIGHT_START : NIGHT_END;
        return this.betweenOpenAndClose(rawOpen, this.windowClose() - this.rouseEarly);
    }

    /** El diurno tiene su tramo seguido en el reloj; el del nocturno cruza la medianoche, así que la
     *  comprobación va al revés. */
    private boolean betweenOpenAndClose(long open, long close) {
        long time = entity.level().getDayTime() % 24000L;
        return entity.isDiurnal() ? (time >= open && time < close) : (time >= open || time < close);
    }

    /**
     * El periodo de sueño de su especie, en crudo: de noche para un diurno, de día para un
     * nocturno. Sin el desfase por UUID, que es reparto de grupo y no biología. Lo usa la orden
     * directa del jugador, para la que escalonar no tiene sentido.
     */
    private boolean inNaturalSleepPeriod() {
        long time = entity.level().getDayTime() % 24000L;
        boolean night = time >= NIGHT_START && time < NIGHT_END;
        return entity.isDiurnal() == night;
    }

    /** Hora del mundo a la que se le abre la ventana. Separado de {@link #inSleepWindow()} para
     *  poder imprimirlo en la traza sin recalcularlo a mano. */
    private long windowOpen() {
        return (entity.isDiurnal() ? NIGHT_START : NIGHT_END) + this.inwardOffset(SALT_OPEN, OPEN_SPREAD);
    }

    /** Hora del mundo a la que se le cierra. */
    private long windowClose() {
        return (entity.isDiurnal() ? NIGHT_END : NIGHT_START) - this.inwardOffset(SALT_CLOSE, CLOSE_SPREAD);
    }

    private int randomBetween(int min, int max) {
        return min + this.entity.getRandom().nextInt(max - min + 1);
    }

    /**
     * Desfase estable de este individuo, en {@code [0, spread)}. Sale del UUID mezclado con la sal,
     * así que es determinista, no ocupa NBT y no hace falta sincronizarlo: el goal sólo corre en
     * servidor.
     *
     * <p>Siempre positivo a propósito: lo suma quien abre la ventana y lo resta quien la cierra, de
     * modo que el desfase sólo puede recortarla. Ver {@link #inSleepWindow()}.
     */
    private int inwardOffset(long salt, int spread) {
        long bits = entity.getUUID().getLeastSignificantBits() ^ salt;
        return (int) Math.floorMod(bits, (long) spread);
    }
}
