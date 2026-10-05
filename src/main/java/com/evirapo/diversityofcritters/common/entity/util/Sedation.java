package com.evirapo.diversityofcritters.common.entity.util;

/**
 * Tiempos de un tranquilizante: espera hasta tumbarse, sueño forzado y cooldown hasta la siguiente
 * dosis. Una por {@code DiverseCritter}, que la tickea en servidor y la guarda en NBT.
 *
 * <p><b>Sólo cuenta; no duerme a nadie.</b> El dueño de {@code SleepState} sigue siendo
 * {@code SleepBehaviorGoal}, que pregunta {@link #isSedated()} y hace el resto. Así la sedación no
 * tiene que saber nada de animaciones ni de goals, y se puede probar sin Minecraft.
 *
 * <p><b>Ticks restantes, no tiempo absoluto del mundo.</b> Se guardan tal cual en NBT, así que
 * sobreviven a una recarga o a un cambio de dimensión sin aritmética de relojes. Con el chunk
 * descargado no se tickea y la cuenta se pausa, igual que el resto del animal.
 *
 * <p><b>El cooldown arranca con la dosis</b>, no al despertar, y dura más que todo el efecto. Por eso
 * una segunda dosis mientras hace efecto cae en {@link DoseResult#ON_COOLDOWN} y no hace falta un
 * tercer resultado para "ya está sedado".
 */
public final class Sedation {

    /** 30 s desde la dosis hasta tumbarse. */
    public static final int DROWSY_TICKS = 600;
    /** 1 min de sueño forzado, contado desde que empieza a tumbarse. */
    public static final int SEDATED_TICKS = 1200;
    /** 5 min desde la dosis hasta poder recibir otra. */
    public static final int COOLDOWN_TICKS = 6000;

    public enum DoseResult { ACCEPTED, ON_COOLDOWN }

    private int drowsyTicks;
    private int sedatedTicks;
    private int cooldownTicks;

    public DoseResult dose() {
        if (this.cooldownTicks > 0) return DoseResult.ON_COOLDOWN;
        this.drowsyTicks = DROWSY_TICKS;
        this.sedatedTicks = 0;
        this.cooldownTicks = COOLDOWN_TICKS;
        return DoseResult.ACCEPTED;
    }

    /**
     * Un tick de juego. El sueño forzado cuenta aunque algo le impida tumbarse (agua, ir en brazos):
     * si sólo contara dormido, un animal que no puede dormirse se quedaría sedado para siempre.
     */
    public void tick() {
        if (this.cooldownTicks > 0) this.cooldownTicks--;
        if (this.drowsyTicks > 0) {
            if (--this.drowsyTicks == 0) this.sedatedTicks = SEDATED_TICKS;
        } else if (this.sedatedTicks > 0) {
            this.sedatedTicks--;
        }
    }

    public boolean isDrowsy() { return this.drowsyTicks > 0; }
    public boolean isSedated() { return this.sedatedTicks > 0; }
    public int drowsyTicks() { return this.drowsyTicks; }
    public int sedatedTicks() { return this.sedatedTicks; }
    public int cooldownTicks() { return this.cooldownTicks; }

    /** Desde NBT. Un mundo anterior a este sistema no trae las claves y lee 0: nada pendiente. */
    public void restore(int drowsy, int sedated, int cooldown) {
        this.drowsyTicks = Math.max(0, drowsy);
        this.sedatedTicks = Math.max(0, sedated);
        this.cooldownTicks = Math.max(0, cooldown);
    }
}
