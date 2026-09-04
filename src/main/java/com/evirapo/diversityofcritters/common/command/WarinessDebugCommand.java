package com.evirapo.diversityofcritters.common.command;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * {@code /doc_wariness}: estado del sensor de amenazas de los critters cercanos.
 *
 * <p>Lo que mas tiempo ahorra no es ver el estado cuando el sistema funciona, sino saber <b>que
 * puerta esta cerrada</b> cuando no pasa nada: domesticado, {@code !isWary()} o recien nacido apagan
 * el sistema entero, y desde fuera eso se ve exactamente igual que un bug.
 */
public class WarinessDebugCommand {

    private static final double RADIUS = 16.0D;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("doc_wariness")
                .requires(source -> source.hasPermission(2))
                .executes(context -> run(context.getSource())));
    }

    private static int run(CommandSourceStack source) {
        Vec3 pos = source.getPosition();
        List<DiverseCritter> critters = source.getLevel().getEntitiesOfClass(
                DiverseCritter.class,
                new AABB(pos.x - RADIUS, pos.y - RADIUS, pos.z - RADIUS,
                         pos.x + RADIUS, pos.y + RADIUS, pos.z + RADIUS));

        if (critters.isEmpty()) {
            source.sendFailure(Component.literal(
                    "[Wariness] No hay critters a " + (int) RADIUS + " bloques."));
            return 0;
        }

        send(source, "§e=== WARINESS (" + critters.size() + " critters) ===");
        for (DiverseCritter critter : critters) {
            send(source, "§f[" + critter.getId() + "] " + critter.getType().getDescriptionId());
            send(source, "  §7zonas: roja=" + fmt(critter.innerThreatRadius())
                    + "  amarilla=" + fmt(critter.outerThreatRadius())
                    + "  segura=" + fmt(critter.safeDistance())
                    + "  dwell=" + critter.alertDwellTicks() + "t");
            send(source, "  §7flags: alert=" + critter.isAlert() + " fleeing=" + critter.isFleeing());
            send(source, "  " + reason(critter));
        }
        send(source, "§e=== FIN ===");
        return 1;
    }

    /** Por que esta apagado, o que esta haciendo si no lo esta. */
    private static String reason(DiverseCritter critter) {
        if (!critter.isWary())   return "§cAPAGADO §7- la especie no ha hecho opt-in (isWary() = false)";
        if (critter.isTame())    return "§cAPAGADO §7- domesticado; el sistema es solo para salvajes";
        if (critter.isNewborn()) return "§cAPAGADO §7- recien nacido";
        if (!critter.isAlive())  return "§cAPAGADO §7- muerto";
        return "§aACTIVO";
    }

    private static String fmt(double v) {
        return String.format("%.1f", v);
    }

    private static void send(CommandSourceStack source, String msg) {
        source.sendSuccess(() -> Component.literal(msg), false);
    }
}
