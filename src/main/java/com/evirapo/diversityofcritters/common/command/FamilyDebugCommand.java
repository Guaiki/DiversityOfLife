package com.evirapo.diversityofcritters.common.command;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * {@code /doc_family}: quien es cria de quien, y por que una cria es o no huerfana.
 *
 * <p>Igual que con el sensor de amenazas, lo que mas tiempo ahorra no es ver el vinculo cuando
 * funciona, sino saber <b>que puerta esta cerrada</b> cuando una cria llora y nadie acude: puede que
 * su madre siga viva y lejos, que el contador aun no haya expirado, o que la unica hembra libre ya
 * tenga cria.
 */
public class FamilyDebugCommand {

    private static final double RADIUS = 24.0D;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("doc_family")
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
                    "[Family] No critters within " + (int) RADIUS + " blocks."));
            return 0;
        }

        send(source, "§e=== FAMILY (" + critters.size() + " critters) ===");
        for (DiverseCritter critter : critters) {
            send(source, "§f[" + critter.getId() + "] " + critter.getType().getDescriptionId()
                    + "  " + (critter.getIsMale() ? "male" : "female")
                    + (critter.isNewborn() ? " §dNEWBORN" : critter.isBaby() ? " §djuvenile" : ""));

            if (critter.isNewborn()) {
                baby(source, critter);
            } else if (!critter.getIsMale()) {
                adult(source, critter);
            }
        }
        send(source, "§e=== END ===");
        return 1;
    }

    /** Estado del vinculo desde el lado de la cria. */
    private static void baby(CommandSourceStack source, DiverseCritter baby) {
        UUID uuid = baby.getCaregiver();
        if (uuid == null) {
            send(source, "  §bcaregiver: none §c[ORPHAN - adoptable]");
            return;
        }

        Entity mother = source.getLevel().getEntity(uuid);
        String where;
        if (mother == null) {
            where = "§7not loaded or dead";
        } else {
            double dist = baby.distanceTo(mother);
            where = String.format("§7[%d] at %.1f %s", mother.getId(), dist,
                    dist <= DiverseCritter.CAREGIVER_RANGE ? "§aIN RANGE" : "§cOUT OF RANGE");
        }

        int since = baby.ticksSinceCaregiverSeen();
        send(source, "  §bcaregiver: " + where);
        send(source, "  §borphan in: " + Math.max(0, DiverseCritter.ORPHAN_TIMEOUT_TICKS - since)
                + "t  (unseen for " + since + "t of " + DiverseCritter.ORPHAN_TIMEOUT_TICKS + ")");
    }

    /** Estado del vinculo desde el lado de la cuidadora. */
    private static void adult(CommandSourceStack source, DiverseCritter adult) {
        List<DiverseCritter> wards = source.getLevel().getEntitiesOfClass(
                DiverseCritter.class,
                adult.getBoundingBox().inflate(DiverseCritter.CAREGIVER_RANGE),
                other -> other.isNewborn() && adult.getUUID().equals(other.getCaregiver()));

        if (wards.isEmpty()) {
            send(source, "  §7no ward §a(free to adopt)");
        } else {
            StringBuilder ids = new StringBuilder();
            for (DiverseCritter ward : wards) {
                if (ids.length() > 0) ids.append(", ");
                ids.append(ward.getId());
            }
            send(source, "  §bward: " + ids + " §c(will not adopt)");
        }
    }

    private static void send(CommandSourceStack source, String msg) {
        source.sendSuccess(() -> Component.literal(msg), false);
    }
}
