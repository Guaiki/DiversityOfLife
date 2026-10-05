package com.evirapo.diversityofcritters.common.item;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.evirapo.diversityofcritters.common.entity.util.Sedation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/** Lo que comparten el dardo y la píldora: dosificar y contárselo al jugador. */
public final class TranquilizerHelper {

    private TranquilizerHelper() {}

    /**
     * Servidor. Devuelve si la dosis entró. {@code feedbackTo} es quien disparó o dio la píldora; null
     * si el dardo no tiene dueño (lo disparó un dispensador, o el tirador ya no existe).
     */
    public static boolean dose(DiverseCritter critter, @Nullable Player feedbackTo) {
        Sedation.DoseResult result = critter.tryTranquilize();
        if (feedbackTo != null) {
            if (result == Sedation.DoseResult.ACCEPTED) {
                feedbackTo.displayClientMessage(Component.translatable(
                        "message.diversityofcritters.tranquilizer.drowsy", critter.getName()), true);
            } else {
                int seconds = (critter.tranquilizerCooldownTicks() + 19) / 20;
                feedbackTo.displayClientMessage(Component.translatable(
                        "message.diversityofcritters.tranquilizer.cooldown", critter.getName(),
                        String.format("%d:%02d", seconds / 60, seconds % 60)), true);
            }
        }
        return result == Sedation.DoseResult.ACCEPTED;
    }
}
