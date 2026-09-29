package com.evirapo.diversityofcritters.common.command;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.evirapo.diversityofcritters.common.entity.util.ThreatSensor;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
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
                    "[Wariness] No critters within " + (int) RADIUS + " blocks."));
            return 0;
        }

        send(source, "§e=== WARINESS (" + critters.size() + " critters) ===");
        for (DiverseCritter critter : critters) {
            send(source, "§f[" + critter.getId() + "] " + critter.getType().getDescriptionId());
            send(source, "  §7zones: inner=" + fmt(critter.innerThreatRadius())
                    + "  outer=" + fmt(critter.outerThreatRadius())
                    + "  safe=" + fmt(critter.safeDistance())
                    + "  dwell=" + critter.alertDwellTicks() + "t");
            send(source, "  §7flags: alert=" + critter.isAlert() + " fleeing=" + critter.isFleeing());
            send(source, "  " + reason(critter));
            if (critter.sensor().isEnabled()) {
                detail(source, critter);
            }
        }
        send(source, "§e=== END ===");
        return 1;
    }

    /** Lo que esta viendo y decidiendo el sensor ahora mismo. */
    private static void detail(CommandSourceStack source, DiverseCritter critter) {
        ThreatSensor sensor = critter.sensor();

        send(source, "  §bstate: " + sensor.state()
                + "   dwell left: " + sensor.dwellTicksLeft() + "t"
                + (sensor.isTimerFrozen() ? " §e(FROZEN: everyone crouching)" : ""));

        LivingEntity threat = sensor.nearestThreat();
        if (threat == null) {
            send(source, "  §bthreat: none");
        } else {
            boolean innerZone = sensor.nearestDistance() <= critter.innerThreatRadius();
            send(source, "  §bthreat: " + threat.getName().getString()
                    + " at " + fmt(sensor.nearestDistance())
                    + (innerZone ? " §c[INNER]" : " §e[OUTER]"));
        }

        int since = sensor.ticksSinceThreat();
        send(source, "  §bre-sleep in: " + Math.max(0, critter.reSleepCooldownTicks() - since)
                + "t  (needs " + critter.reSleepCooldownTicks() + "t threat-free)");

        movement(source, critter);
        sensor.spawnDebugRings(source.getLevel());
    }

    /**
     * Quien retiene el flag MOVE y que clip esta sonando.
     *
     * <p>Desde fuera del juego, "el goal de huida corre pero no encuentra ruta" y "un goal de idle le
     * ha robado el flag" se ven <b>exactamente igual</b>: el critter parado con una pose de idle.
     * Esta seccion es lo unico que los distingue.
     */
    private static void movement(CommandSourceStack source, DiverseCritter critter) {
        send(source, "  §6sprinting=" + critter.isSprinting()
                + " moving=" + critter.isMoving()
                + " navDone=" + critter.getNavigation().isDone()
                + " animLocked=" + critter.animator().isMovementLocked());

        String anim = critter.animator().getCurrent(0).getName();
        send(source, "  §6anim: " + (anim.isEmpty() ? "(none)" : anim));

        StringBuilder holders = new StringBuilder();
        critter.goalSelector.getRunningGoals().forEach(wrapped -> {
            if (!wrapped.getFlags().contains(Goal.Flag.MOVE)) return;
            if (holders.length() > 0) holders.append(", ");
            holders.append(wrapped.getGoal().getClass().getSimpleName());
        });
        send(source, "  §6MOVE held by: " + (holders.length() == 0 ? "nobody" : holders));
    }

    /** Por que esta apagado, o que esta haciendo si no lo esta. */
    private static String reason(DiverseCritter critter) {
        if (!critter.isWary())   return "§cOFF §7- species has not opted in (isWary() = false)";
        if (critter.isTame())    return "§cOFF §7- tamed; this system is for wild critters only";
        if (critter.isNewborn()) return "§cOFF §7- newborn";
        if (!critter.isAlive())  return "§cOFF §7- dead";
        return "§aACTIVE";
    }

    private static String fmt(double v) {
        return String.format("%.1f", v);
    }

    private static void send(CommandSourceStack source, String msg) {
        source.sendSuccess(() -> Component.literal(msg), false);
    }
}
