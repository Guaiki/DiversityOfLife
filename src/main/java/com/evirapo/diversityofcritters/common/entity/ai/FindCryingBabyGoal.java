package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public class FindCryingBabyGoal extends Goal {
    private final DiverseCritter parent;
    private final double speedModifier;
    private final double searchRange;
    private DiverseCritter targetBaby;
    private int feedTimer;

    private static final int FEEDING_DURATION = 60;

    public FindCryingBabyGoal(DiverseCritter parent, double speed, double range) {
        this.parent = parent;
        this.speedModifier = speed;
        this.searchRange = range;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (parent.isBaby() || parent.isSleeping() || parent.isOrderedToSit()
                || parent.isPassenger() || parent.getIsMale()) {
            return false;
        }

        // Una sola consulta, con el radio del VINCULO y no el de busqueda del goal, y sin filtrar por
        // isCrying: mi propia cria puede estar bien alimentada y en ese caso no llora, pero sigue
        // ocupando mi plaza. Que los dos lados usen CAREGIVER_RANGE es lo que evita la banda de
        // distancias en la que la madre se cree libre y acaba con dos crias.
        List<DiverseCritter> newborns = parent.level().getEntitiesOfClass(
                DiverseCritter.class,
                parent.getBoundingBox().inflate(DiverseCritter.CAREGIVER_RANGE),
                entity -> entity.getType() == parent.getType() && entity.isNewborn());

        boolean hasWard = false;
        DiverseCritter ownCryingChild = null;
        DiverseCritter cryingOrphan = null;

        for (DiverseCritter baby : newborns) {
            if (parent.getUUID().equals(baby.getCaregiver())) {
                hasWard = true;
                if (baby.isCrying() && ownCryingChild == null) ownCryingChild = baby;
            } else if (baby.isOrphan() && baby.isCrying() && cryingOrphan == null
                    && parent.distanceTo(baby) <= this.searchRange) {
                cryingOrphan = baby;
            }
        }

        // La propia cria manda sobre cualquier huerfano: relevar a otra es un extra, alimentar a la
        // tuya es la obligacion. Y un huerfano solo se considera dentro de searchRange, para que el
        // goal no camine mas lejos de lo que caminaba antes: el radio grande sirve para contar la
        // plaza, no para elegir destino.
        this.targetBaby = ownCryingChild != null ? ownCryingChild
                : (hasWard ? null : cryingOrphan);
        return this.targetBaby != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (targetBaby == null || !targetBaby.isAlive() || !targetBaby.isCrying()) return false;
        if (this.feedTimer >= FEEDING_DURATION + 10) return false;

        // Si otra hembra lo adopto mientras yo iba de camino, dejo de ir. No hay reserva sobre el
        // huerfano a proposito: la primera que lo amamanta lo vincula y esta comprobacion hace que la
        // segunda lo suelte. Sin ella acabarian las dos plantadas encima del mismo bebe.
        UUID caregiver = targetBaby.getCaregiver();
        return caregiver == null || parent.getUUID().equals(caregiver);
    }

    @Override
    public void start() {
        this.feedTimer = 0;
        this.parent.getNavigation().moveTo(this.targetBaby, this.speedModifier);
    }

    @Override
    public void stop() {
        this.targetBaby = null;
        this.feedTimer = 0;
        this.parent.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (targetBaby == null) return;

        this.parent.getLookControl().setLookAt(targetBaby, 30.0F, 30.0F);

        double distSqr = this.parent.distanceToSqr(targetBaby);

        if (distSqr <= 5.0D) {
            this.parent.getNavigation().stop();
            this.feedTimer++;

            if (this.feedTimer >= FEEDING_DURATION) {
                performNursing();
            }
        } else {
            this.feedTimer = 0;
            if (this.parent.getNavigation().isDone()) {
                this.parent.getNavigation().moveTo(this.targetBaby, this.speedModifier);
            }
        }
    }

    private void performNursing() {
        // Si era huerfano, amamantarlo lo adopta: el vinculo es permanente y sustituye al anterior. No
        // hace falta comprobar si ya era mio, porque reasignar el mismo UUID no cambia nada y ademas
        // refresca el contador de ausencia, que es justo lo que toca al haber estado pegados.
        targetBaby.setCaregiver(parent.getUUID());

        targetBaby.setHunger(targetBaby.maxHunger());
        targetBaby.setThirst(targetBaby.maxThirst());
        targetBaby.setCrying(false);

        Level level = parent.level();
        level.broadcastEntityEvent(targetBaby, (byte) 7);
        parent.playSound(SoundEvents.CAT_PURR, 1.0F, 1.0F);

    }
}