"""Banco de pruebas del ciclo de sueno. Replica SleepBehaviorGoal tick a tick y comprueba
invariantes sobre dias completos.

    python tools/sim_sleep.py

Por que existe. El goal solo se puede observar in-game, y sus fallos son de los que no dan
excepcion: un bicho que se queda dormido de noche, o que no reacciona a un boton, se ve raro pero
no rompe nada. Este banco reproduce la maquina de estados —incluida la cadencia real del selector
de goals— y afirma lo que tiene que cumplirse siempre, para poder cambiar constantes sin tener que
entrar al mundo a mirar.

Que replica, y por que importa cada cosa:

  * `Mob.serverAiStep` solo llama a `goalSelector.tick()` cuando `(tickCount + getId()) % 2 == 0`,
    asi que canUse/canContinueToUse/tick corren 1 de cada 2 ticks. Cualquier duracion contada como
    numero de llamadas dura el doble; por eso el goal usa ticks absolutos.
  * Dentro de `GoalSelector.tick()` el orden es: cerrar los que ya no pueden seguir (goalCleanup),
    arrancar los que pueden (goalUpdate), y despues tickear los que corren. Un goal parado en el
    paso 1 puede volver a arrancar en el paso 2 de la MISMA evaluacion.

Si cambias una constante en SleepBehaviorGoal.java, cambiala tambien aqui arriba y vuelve a correr.
"""
import random
import sys

# --- constantes, espejo de SleepBehaviorGoal ---------------------------------------------------
NIGHT_START, NIGHT_END = 13000, 23000
OPEN_SPREAD, CLOSE_SPREAD = 300, 450
ROUSE_SPREAD = 450
SETTLE_SPREAD = 300
MIN_SLEEP_TICKS, MAX_SLEEP_TICKS = 2400, 6000
MIN_AWAKE_TICKS, MAX_AWAKE_TICKS = 600, 1800
FORCED_WAKE_COOLDOWN = 600
SALT_OPEN, SALT_CLOSE = 0x5EED, 0xDA7A
PREPARE_TICKS, AWAKEN_TICKS = 40, 40      # adulto: 2.0 s de clip

AWAKE, PREPARING, SLEEPING, AWAKENING = "AWAKE", "PREPARING", "SLEEPING", "AWAKENING"
ASLEEP_STATES = (PREPARING, SLEEPING)

MASK = (1 << 64) - 1


def _s64(v):
    v &= MASK
    return v - (1 << 64) if v >> 63 else v


class Critter:
    """Lo justo de DiverseCritter que el goal mira."""

    def __init__(self, eid, uuid_lsb, diurnal, rng):
        self.id = eid
        self.uuid_lsb = uuid_lsb
        self.diurnal = diurnal
        self.rng = rng
        self.tickCount = 0
        self.sleepState = AWAKE
        self.scheduleChanged = False
        self.inWater = self.vehicle = self.attacking = self.cleaning = self.sitting = False
        self.hurtTime = 0

    def consume_schedule_changed(self):
        was = self.scheduleChanged
        self.scheduleChanged = False
        return was

    def set_diurnal_by_player(self, value):
        self.diurnal = value
        self.scheduleChanged = True


class SleepGoal:
    """Traduccion literal de SleepBehaviorGoal."""

    def __init__(self, entity):
        self.e = entity
        self.running = False
        self.sleepUntilTick = 0
        self.noSleepUntilTick = 0
        self.phaseUntilTick = 0
        self.settleUntilTick = 0
        self.settleArmed = False
        self.rouseEarly = 0
        self.scheduleForced = False
        self.forcedWake = False

    # --- ventana ---
    def _inward(self, salt, spread):
        return _s64(self.e.uuid_lsb ^ salt) % spread

    def window_open(self):
        base = NIGHT_START if self.e.diurnal else NIGHT_END
        return base + self._inward(SALT_OPEN, OPEN_SPREAD)

    def window_close(self):
        base = NIGHT_END if self.e.diurnal else NIGHT_START
        return base - self._inward(SALT_CLOSE, CLOSE_SPREAD)

    def in_window(self, world_time):
        t = world_time % 24000
        o, c = self.window_open(), self.window_close()
        return (o <= t < c) if self.e.diurnal else (t >= o or t < c)

    def in_natural_period(self, world_time):
        """Periodo de la especie en crudo, sin el desfase por UUID."""
        night = NIGHT_START <= world_time % 24000 < NIGHT_END
        return self.e.diurnal == night

    def can_keep_sleeping(self, world_time):
        """Para SEGUIR dormido se abre en el borde natural. El desfase de apertura reparte la hora
        de acostarse; no puede despertar a quien ya duerme."""
        t = world_time % 24000
        o = NIGHT_START if self.e.diurnal else NIGHT_END
        c = self.window_close() - self.rouseEarly
        return (o <= t < c) if self.e.diurnal else (t >= o or t < c)

    # --- lifecycle ---
    def can_use(self, world_time):
        if self.e.sleepState != AWAKE:
            return True                                    # adopcion de estado huerfano
        schedule_changed = self.e.consume_schedule_changed()
        if schedule_changed:
            self.scheduleForced = True                     # se guarda, no se gasta
            self.noSleepUntilTick = 0                      # plazos del horario anterior, fuera
            self.settleArmed = False
        if self.scheduleForced and self.in_natural_period(world_time):
            self.scheduleForced = False                    # orden directa: sin escalonado
            self.settleArmed = False
            return True
        if not self.in_window(world_time):
            self.settleArmed = False
            return False
        if not self.settleArmed:
            self.settleUntilTick = self.e.tickCount + self.e.rng.randrange(SETTLE_SPREAD)
            self.settleArmed = True
        if schedule_changed:
            return True
        if self.e.tickCount < self.noSleepUntilTick:
            return False
        if self.e.inWater or self.e.vehicle or self.e.attacking or self.e.cleaning or self.e.sitting:
            return False
        return self.e.tickCount >= self.settleUntilTick

    def can_continue(self, world_time):
        if self.e.sleepState == AWAKENING:
            return self.e.tickCount < self.phaseUntilTick
        if self.e.hurtTime > 0 or self.e.inWater:
            self._start_waking(True)
            return True
        if self.e.tickCount >= self.sleepUntilTick or not self.can_keep_sleeping(world_time):
            self._start_waking(False)
            return True
        return True

    def start(self):
        self.forcedWake = False
        self.sleepUntilTick = self.e.tickCount + self.e.rng.randint(MIN_SLEEP_TICKS, MAX_SLEEP_TICKS)
        self.rouseEarly = self.e.rng.randrange(ROUSE_SPREAD)
        restored = self.e.sleepState
        if restored == AWAKE:
            self.e.sleepState = PREPARING
            self.phaseUntilTick = self.e.tickCount + PREPARE_TICKS
        else:
            self.phaseUntilTick = self.e.tickCount + (
                AWAKEN_TICKS if restored == AWAKENING else PREPARE_TICKS)

    def stop(self):
        self.e.sleepState = AWAKE
        self.noSleepUntilTick = self.e.tickCount + (
            FORCED_WAKE_COOLDOWN if self.forcedWake
            else self.e.rng.randint(MIN_AWAKE_TICKS, MAX_AWAKE_TICKS))
        self.forcedWake = False

    def tick(self):
        if self.e.sleepState == PREPARING and self.e.tickCount >= self.phaseUntilTick:
            self.e.sleepState = SLEEPING

    def _start_waking(self, forced):
        if self.e.sleepState != AWAKENING:
            self.e.sleepState = AWAKENING
            self.phaseUntilTick = self.e.tickCount + AWAKEN_TICKS
            self.forcedWake = forced


def step(goal, world_time):
    """Una llamada a GoalSelector, con su orden real."""
    e = goal.e
    if (e.tickCount + e.id) % 2 != 0 and e.tickCount > 1:
        return                                    # este tick el selector no evalua este mob
    if goal.running and not goal.can_continue(world_time):
        goal.stop()
        goal.running = False
    if not goal.running and goal.can_use(world_time):
        goal.start()
        goal.running = True
    if goal.running:
        goal.tick()


def is_night(t):
    return NIGHT_START <= t % 24000 < NIGHT_END


# --- escenarios --------------------------------------------------------------------------------
FAILURES = []


def check(name, ok, detail=""):
    print("  %-58s %s%s" % (name, "OK" if ok else "FALLA", ("  " + detail) if detail else ""))
    if not ok:
        FAILURES.append(name)


def scenario_full_days(diurnal, label, days=6, n=12):
    """N bichos, varios dias seguidos. Comprueba que nunca duermen fuera de su periodo y que
    ninguna siesta se desmadra, y mide el reparto del grupo al acostarse y al levantarse.

    El reparto se mide en ticks ABSOLUTOS y anclado a la apertura/cierre de cada ventana, no sobre
    la hora del reloj: la ventana del nocturno cruza la medianoche (23000 -> 0), asi que restar
    horas del reloj daria la vuelta al dia entera."""
    rng = random.Random(1234)
    pack = []
    for i in range(n):
        e = Critter(i, rng.getrandbits(64) - (1 << 63), diurnal, random.Random(500 + i))
        pack.append((e, SleepGoal(e)))

    bad_period = 0
    longest_asleep = 0
    asleep_since = {}
    prev = {e.id: AWAKE for e, _ in pack}
    prev_in_win = {e.id: None for e, _ in pack}
    # periodo -> {id: tick}. El primer acostarse tras abrirse la ventana, y el ultimo despertar
    # tras cerrarse; son los dos momentos en los que el jugador ve al grupo hacer lo mismo a la vez.
    first_bed, last_wake = {}, {}
    period = {e.id: 0 for e, _ in pack}

    for t in range(days * 24000):
        for e, g in pack:
            e.tickCount = t
            step(g, t)
            st = e.sleepState

            in_win = g.in_window(t)
            if prev_in_win[e.id] is False and in_win:
                period[e.id] += 1                       # se le abrio la ventana: periodo nuevo
            prev_in_win[e.id] = in_win

            # invariante 1: dormir solo dentro de su periodo natural
            wrong = is_night(t) if not diurnal else not is_night(t)
            if st in ASLEEP_STATES and wrong:
                bad_period += 1

            # invariante 2: ninguna siesta se desmadra
            if st in ASLEEP_STATES and prev[e.id] == AWAKE:
                asleep_since[e.id] = t
                first_bed.setdefault(period[e.id], {}).setdefault(e.id, t)
            if st == AWAKE and prev[e.id] != AWAKE and e.id in asleep_since:
                longest_asleep = max(longest_asleep, t - asleep_since.pop(e.id))
                # El "despertar por cierre" hay que detectarlo con la MISMA regla que lo provoca,
                # can_keep_sleeping, no con in_window: el adelanto aleatorio los levanta hasta 450
                # ticks antes, y en ese instante in_window todavia dice que si.
                if not g.can_keep_sleeping(t):
                    last_wake.setdefault(period[e.id], {})[e.id] = t
            prev[e.id] = st

    cap = MAX_SLEEP_TICKS + PREPARE_TICKS + AWAKEN_TICKS + 4
    # Los que siguen dormidos al cortar la simulacion no estan "enganchados": simplemente les pillo
    # a mitad de siesta. Lo que hay que exigirles es lo mismo que a las cerradas: que no se pasen.
    open_naps = [days * 24000 - since for since in asleep_since.values()]
    longest_asleep = max([longest_asleep] + open_naps)

    print("\n%s (%d bichos, %d dias)" % (label, n, days))
    check("nunca duerme fuera de su periodo natural", bad_period == 0,
          "" if bad_period == 0 else "%d ticks en falso" % bad_period)
    check("ninguna siesta se pasa del maximo (%d ticks)" % cap, longest_asleep <= cap,
          "mas larga: %d" % longest_asleep)

    def spread(per_period, min_members):
        vals = [max(d.values()) - min(d.values())
                for d in per_period.values() if len(d) >= min_members]
        return (sum(vals) / len(vals) / 20) if vals else float("nan")

    # Acostarse: entran todos, asi que se exigen los n.
    bed_s = spread(first_bed, n)
    check("el grupo se acuesta repartido pero sin rezagados (<=45s)", bed_s <= 45,
          "reparto ~%.0fs" % bed_s)

    # Levantarse por cierre de ventana: solo despiertan asi los que siguen dormidos en ese momento
    # —los que ya acabaron su ultima siesta, no—, o sea que nunca estan los n. Se mide sobre los
    # que si, que son los que el jugador ve levantarse a la vez.
    wake_s = spread(last_wake, max(3, n // 2))
    check("el grupo se levanta repartido, no a la vez (>=15s)", wake_s >= 15,
          "reparto ~%.0fs entre los que despierta el cierre" % wake_s)

    # El despertar debe estar construido igual que el acostarse: una parte fija del individuo (su
    # rutina) y otra sorteada en cada ocasion (el azar del dia). Con solo la fija salia en fila.
    check("el despertar lleva sus dos mitades, como el acostarse",
          ROUSE_SPREAD > 0 and CLOSE_SPREAD == ROUSE_SPREAD,
          "UUID %d + azar %d = %d ticks (%.0fs)"
          % (CLOSE_SPREAD, ROUSE_SPREAD, CLOSE_SPREAD + ROUSE_SPREAD,
             (CLOSE_SPREAD + ROUSE_SPREAD) / 20))

    # Lo que se veia "en fila": con solo el desfase por UUID el orden de despertar es el mismo
    # todos los dias, porque el UUID no cambia. El sorteo por tanda (ROUSE_SPREAD) tiene que
    # romperlo.
    orders = {tuple(sorted(d, key=d.get)) for d in last_wake.values() if len(d) >= 4}
    check("el orden en que despiertan cambia de un dia a otro", len(orders) > 1,
          "%d ordenes distintos en %d periodos" % (len(orders), len(last_wake)))


def scenario_button():
    """Los cuatro cruces de boton x hora, que es lo que se reporto como roto."""
    print("\nBoton sol/luna (ardilla domesticada)")
    for hour_label, world_time in (("DIA  t=6000", 6000), ("NOCHE t=18000", 18000)):
        for press_diurnal in (True, False):
            e = Critter(0, 0x1234_5678_9ABC_DEF0, not press_diurnal, random.Random(9))
            g = SleepGoal(e)
            # deja que se estabilice con su cronotipo original
            for t in range(2000):
                e.tickCount = t
                step(g, world_time + t)
            before = e.sleepState
            e.set_diurnal_by_player(press_diurnal)
            reacted = None
            for t in range(2000, 2000 + 400):
                e.tickCount = t
                step(g, world_time + t)
                settled = (e.sleepState == SLEEPING) or (e.sleepState == AWAKE and before != AWAKE)
                if settled and reacted is None:
                    reacted = t - 2000
            want_sleep = g.in_window(world_time)
            got_sleep = e.sleepState in (SLEEPING, PREPARING)
            check("%s + %-4s -> %s en %s ticks"
                  % (hour_label, "SOL" if press_diurnal else "LUNA",
                     "DUERME" if got_sleep else "DESPIERTA",
                     reacted if reacted is not None else ">400"),
                  got_sleep == want_sleep and reacted is not None and reacted <= 100,
                  "" if got_sleep == want_sleep else "esperaba %s" % ("DUERME" if want_sleep else "DESPIERTA"))


def scenario_orphan():
    """Estado de sueno restaurado de NBT sin goal dueno: tiene que recuperarse, y de noche
    (nocturno) debe despertarse en vez de quedarse tieso hasta el amanecer."""
    print("\nEstado huerfano tras recargar (nocturno)")
    for label, world_time in (("de dia  (le toca dormir)", 6000), ("de noche (no le toca)", 18000)):
        e = Critter(0, 0xFEED_FACE_CAFE_0001, False, random.Random(4))
        g = SleepGoal(e)
        e.sleepState = SLEEPING          # como sale de readAdditionalSaveData
        e.tickCount = 0
        recovered = None
        for t in range(600):
            e.tickCount = t
            step(g, world_time + t)
            if g.running and recovered is None:
                recovered = t
        # Lo que hay que exigir es que el goal lo ADOPTE (si no, se queda congelado para siempre) y
        # que acabe en el estado que toca. Que el goal siga corriendo al final no: de noche lo
        # correcto es justo que lo despierte y se suelte.
        expect_awake = not g.in_window(world_time)
        ok = recovered is not None and (e.sleepState == AWAKE) == expect_awake
        check("%s -> adoptado en %s ticks, acaba %s" % (label, recovered, e.sleepState), ok,
              "" if ok else "esperaba %s" % (AWAKE if expect_awake else "dormido"))


def scenario_button_then_time():
    """La secuencia que se reporto rota: de dia, ardilla nocturna dormida -> pulsar SOL (pasa a
    diurna) la despierta -> poco despues se pone la noche con /time -> deberia acostarse ya.

    Fallaba porque el despertar que provoca el propio boton arma noSleepUntilTick (30-90 s, la
    espera normal ENTRE siestas). Ese plazo es del horario viejo y sobrevivia al cambio, asi que
    bloqueaba la siesta que el jugador acababa de pedir: hasta 66 s parada sin motivo visible."""
    print("\nSecuencia boton-y-luego-hora (dia -> SOL -> /time noche)")
    delays, stuck = [], []
    for seed in range(400):
        e = Critter(0, random.Random(seed).getrandbits(64) - (1 << 63), False, random.Random(seed))
        g = SleepGoal(e)
        t, world = 0, 1000
        while t < 3000 and e.sleepState != SLEEPING:
            e.tickCount = t; step(g, world + t); t += 1
        e.set_diurnal_by_player(True)
        while t < 3200 and e.sleepState != AWAKE:
            e.tickCount = t; step(g, world + t); t += 1
        for _ in range(480):                       # los ~24 s que pasaron en el log
            e.tickCount = t; step(g, world + t); t += 1
        slept = None
        for k in range(6000):                      # /time set 13000
            e.tickCount = t; step(g, NIGHT_START + k)
            if e.sleepState in ASLEEP_STATES and slept is None:
                slept = k
            # No basta con que EMPIECE: la siesta tiene que aguantar. El fallo real era que
            # arrancaba y `canContinueToUse` la mataba dos ticks despues, por mirar la ventana
            # escalonada en vez del periodo natural. Medido solo el arranque, esto pasaba igual.
            if slept is not None and k > slept + 200:
                break
            t += 1
        stuck.append(slept is not None and e.sleepState in ASLEEP_STATES)
        delays.append(slept if slept is not None else 10 ** 6)
    # Es una orden directa del jugador, no un grupo al que escalonar: en cuanto el reloj entra en
    # el periodo natural de la especie tiene que acostarse, sin esperar a que abra "su" ventana ni
    # al retardo de reparto. La cota es la del propio ciclo del selector (evalua 1 de cada 2 ticks).
    worst = max(delays) / 20
    check("tras poner la noche se acuesta en el acto (<=1s)", worst <= 1.0,
          "peor %.1fs | media %.1fs" % (worst, sum(delays) / len(delays) / 20))
    check("y la siesta aguanta, no la mata canContinueToUse", all(stuck),
          "%d de %d se despertaron solas" % (stuck.count(False), len(stuck)))


def main():
    scenario_full_days(False, "NOCTURNO (ardilla, civet) - duerme de dia")
    scenario_full_days(True, "DIURNO (sifaka) - duerme de noche")
    scenario_button()
    scenario_button_then_time()
    scenario_orphan()
    print("\n%s" % ("TODO OK" if not FAILURES else "FALLAN %d: %s" % (len(FAILURES), FAILURES)))
    return 1 if FAILURES else 0


if __name__ == "__main__":
    sys.exit(main())
