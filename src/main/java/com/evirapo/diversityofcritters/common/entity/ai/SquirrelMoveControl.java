package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.SquirrelEntity;
import com.evirapo.diversityofcritters.misc.tags.DoCTags;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public class SquirrelMoveControl extends MoveControl {
    private final SquirrelEntity squirrel;

    private static final double CLIMB_SPEED = 0.16D;
    private static final double STEP_UP_IMPULSE = 0.02D;
    private static final double STEP_UP_HORIZONTAL_DAMPING = 0.02D;
    private static final int POST_CLIMB_DAMPING_TICKS = 6;
    private static final double POST_CLIMB_HORIZONTAL_DAMPING = 0.65D;
    private static final int LOOKAHEAD_NODES = 5;

    private boolean wasClimbingLastTick = false;
    private boolean wasGlidingLastTick = false;
    private int postClimbDampingTicks = 0;

    private int landingCooldown = 0;
    private static final int LANDING_COOLDOWN_TICKS = 15; // limpia velocidad horizontal residual

    public SquirrelMoveControl(SquirrelEntity pMob) {
        super(pMob);
        this.squirrel = pMob;
    }

    @Override
    public void tick() {
        boolean isGlidingNow = this.squirrel.isGliding();

        // --- GLIDE: manos fuera. SquirrelGlideGoal es dueño de la física. ---
        if (isGlidingNow) {
            this.wasGlidingLastTick = true;
            this.wasClimbingLastTick = false;
            this.postClimbDampingTicks = 0;
            return;
        }

        // Aterrizaje del glide: mismo tratamiento que el aterrizaje del climb.
        // (Tercera capa de protección, junto a los dos cooldowns de la entidad.)
        if (this.wasGlidingLastTick) {
            this.wasGlidingLastTick = false;
            this.landingCooldown = LANDING_COOLDOWN_TICKS;
        }

        boolean isClimbingNow = this.squirrel.isClimbing();

        // Aterrizaje del climb: cuando bajaba y acaba de tocar suelo.
        if (this.wasClimbingLastTick && !isClimbingNow && this.squirrel.onGround()) {
            this.landingCooldown = LANDING_COOLDOWN_TICKS;
        }
        if (this.landingCooldown > 0) {
            this.landingCooldown--;
            // Cero velocidad horizontal durante el aterrizaje: el empuje
            // residual contra la pared no puede disparar CLIMB_UP vía
            // horizontalCollision (el bug que motivó el fix del Civet).
            Vec3 motion = this.squirrel.getDeltaMovement();
            this.squirrel.setDeltaMovement(0, motion.y, 0);
        }

        if (isClimbingNow) {
            this.wasClimbingLastTick = true;
            this.postClimbDampingTicks = 0;
            this.handleClimbingMovement();
            return;
        }

        if (this.wasClimbingLastTick) {
            this.postClimbDampingTicks = POST_CLIMB_DAMPING_TICKS;
            this.wasClimbingLastTick = false;
        }

        if (this.postClimbDampingTicks > 0) {
            Vec3 motion = this.squirrel.getDeltaMovement();
            this.squirrel.setDeltaMovement(
                    motion.x * POST_CLIMB_HORIZONTAL_DAMPING,
                    motion.y,
                    motion.z * POST_CLIMB_HORIZONTAL_DAMPING
            );
            this.postClimbDampingTicks--;
        }

        if (!this.squirrel.getNavigation().isDone()) {
            updateClimbDirectionFromPath();
            // updateClimbDirectionFromPath puede haber entregado el control al glide.
            if (this.squirrel.isGliding()) {
                this.wasGlidingLastTick = true;
                return;
            }
        }

        if (this.operation == Operation.MOVE_TO && !this.squirrel.getNavigation().isDone()) {
            double dx = this.wantedX - this.squirrel.getX();
            double dy = this.wantedY - this.squirrel.getY();
            double dz = this.wantedZ - this.squirrel.getZ();
            double distSqr = dx * dx + dy * dy + dz * dz;

            if (distSqr < 2.500000277905201E-7D) {
                this.mob.setZza(0.0F);
                return;
            }

            float targetYaw = (float)(Mth.atan2(dz, dx) * (double)(180F / (float)Math.PI)) - 90.0F;
            this.squirrel.setYRot(this.rotlerp(this.squirrel.getYRot(), targetYaw, 30.0F));
            this.squirrel.setYBodyRot(this.squirrel.getYRot());
            this.squirrel.setSpeed((float)(this.speedModifier * this.squirrel.getAttributeValue(Attributes.MOVEMENT_SPEED)));

            if (dy > (double)this.squirrel.maxUpStep()
                    && dx * dx + dz * dz < (double)Math.max(1.0F, this.squirrel.getBbWidth())) {
                if (!this.squirrel.isNewborn()) {
                    Vec3 motion = this.squirrel.getDeltaMovement();
                    this.squirrel.setDeltaMovement(
                            motion.x * STEP_UP_HORIZONTAL_DAMPING,
                            Math.max(motion.y, STEP_UP_IMPULSE),
                            motion.z * STEP_UP_HORIZONTAL_DAMPING
                    );
                }
            }
        } else {
            this.squirrel.setZza(0.0F);
        }
    }

    private void updateClimbDirectionFromPath() {
        Path path = this.squirrel.getNavigation().getPath();
        if (path == null || path.isDone()) return;

        int nextIdx = path.getNextNodeIndex();
        if (nextIdx >= path.getNodeCount()) return;

        Node nextNode = path.getNode(nextIdx);
        int currentY = (nextIdx > 0)
                ? path.getNode(nextIdx - 1).y
                : Mth.floor(this.squirrel.getY());

        int dy = nextNode.y - currentY;
        boolean adjacentToWall = this.squirrel.hasAdjacentClimbableBlock();
        boolean onGround = this.squirrel.onGround();
        boolean inCooldown = this.squirrel.isInPostDescentCooldown()
                || this.squirrel.isInPostGlideCooldown();

        // CLIMB_UP: el path sube Y hay pared Y sin cooldowns (idéntico al Civet).
        if (dy > 0 && adjacentToWall && !inCooldown) {
            this.squirrel.setClimbState(SquirrelEntity.CLIMB_UP);
            return;
        }

        // DESCENSO: el path baja, está en el aire y pegado a la pared.
        // Decisión clave del Módulo 4: glide para caídas largas (>= 3),
        // CLIMB_DOWN clásico solo para descensos cortos (1-2 bloques).
        if (dy < 0 && adjacentToWall && !onGround && !inCooldown) {
            if (tryHandOffToGlide()) return;
            // tryHandOffToGlide failed. Only enter CLIMB_DOWN for short structures.
            // For tall ones the velocity check is the only blocker (squirrel just stepped
            // off the edge — velocity hasn't built up yet). Returning here lets gravity
            // act for 1-2 ticks; the next call to tryHandOffToGlide will pass.
            if (this.squirrel.distanceToGround(SquirrelEntity.GLIDE_MIN_AIR + 1) < SquirrelEntity.GLIDE_MIN_AIR) {
                this.squirrel.setClimbState(SquirrelEntity.CLIMB_DOWN);
            }
            return;
        }

        // Caso especial: onGround SOBRE la cima de una columna trepable.
        // Igual que el Civet: caminar hacia el nodo de descenso. Al salir del
        // borde queda en el aire junto al tronco y la rama de arriba (o el
        // escenario 2 del GlideGoal) resuelve glide vs CLIMB_DOWN según altura.
        if (onGround && !inCooldown) {
            BlockPos below = this.squirrel.blockPosition().below();
            boolean onTopOfClimbable = this.squirrel.level()
                    .getBlockState(below).is(DoCTags.Blocks.CIVET_CLIMBABLE);

            if (onTopOfClimbable) {
                boolean pathHasDescent = false;
                for (int i = nextIdx; i < path.getNodeCount(); i++) {
                    if (path.getNode(i).y < currentY) {
                        pathHasDescent = true;
                        break;
                    }
                }

                if (pathHasDescent) {
                    for (int i = nextIdx; i < Math.min(nextIdx + LOOKAHEAD_NODES, path.getNodeCount()); i++) {
                        if (path.getNode(i).y < currentY) {
                            Node target = path.getNode(i);
                            float speed = (float)(this.speedModifier
                                    * this.squirrel.getAttributeValue(Attributes.MOVEMENT_SPEED));
                            double tx = target.x + 0.5 - this.squirrel.getX();
                            double tz = target.z + 0.5 - this.squirrel.getZ();
                            float yaw = (float)(Mth.atan2(tz, tx) * (180.0 / Math.PI)) - 90.0F;
                            this.squirrel.setYRot(this.rotlerp(this.squirrel.getYRot(), yaw, 30.0F));
                            this.squirrel.setYBodyRot(this.squirrel.getYRot());
                            this.squirrel.setSpeed(speed);
                            return;
                        }
                    }
                }
                // Sin descenso en el path: continuar con movimiento normal.
            }
        }

        // Lookahead de descenso próximo (ya en el aire, sin cooldowns).
        if (dy == 0 && adjacentToWall && !onGround && !inCooldown) {
            int limit = Math.min(nextIdx + LOOKAHEAD_NODES, path.getNodeCount());
            for (int i = nextIdx + 1; i < limit; i++) {
                int futureY = path.getNode(i).y;
                if (futureY < currentY) {
                    if (tryHandOffToGlide()) return;
                    if (this.squirrel.distanceToGround(SquirrelEntity.GLIDE_MIN_AIR + 1) < SquirrelEntity.GLIDE_MIN_AIR) {
                        this.squirrel.setClimbState(SquirrelEntity.CLIMB_DOWN);
                    }
                    return;
                }
                if (futureY > currentY) break;
            }
        }
    }

    /**
     * Si hay suficiente aire debajo, entrega el control al sistema de glide.
     * tryStartGlide() re-valida altura/agua/suelo internamente, así que esta
     * llamada es segura aunque las condiciones cambien en el mismo tick.
     *
     * @return true si el glide tomó el control (este MoveControl debe soltar).
     */
    private boolean tryHandOffToGlide() {
        // Don't glide while still rising from the climb (cresting a log top): the
        // squirrel needs to actually be falling before a handoff to glide makes sense.
        if (this.squirrel.getDeltaMovement().y > -0.05D) return false;
        if (this.squirrel.distanceToGround(SquirrelEntity.GLIDE_MIN_AIR + 1) < SquirrelEntity.GLIDE_MIN_AIR) {
            return false;
        }
        return this.squirrel.tryStartGlide();
    }

    private void handleClimbingMovement() {
        float horizontalSpeed = (float)(0.8 * this.squirrel.getAttributeValue(Attributes.MOVEMENT_SPEED)) * 0.5F;
        this.squirrel.setSpeed(horizontalSpeed);
        Vec3 motion = this.squirrel.getDeltaMovement();

        if (this.squirrel.isHanging()) {
            this.squirrel.setDeltaMovement(motion.x, 0.0D, motion.z);
        } else if (this.squirrel.isClimbingDown()) {
            // CLIMB_DOWN conservado SOLO para descensos cortos (1-2 bloques):
            // empuje hacia la pared para mantener el hitbox pegado, igual que
            // el Civet. Los descensos largos jamás llegan aquí — los intercepta
            // tryHandOffToGlide() en updateClimbDirectionFromPath().
            float yawRad = (float) Math.toRadians(this.squirrel.getClimbFacingYaw());
            double pushX = -Math.sin(yawRad) * 0.15;
            double pushZ = Math.cos(yawRad) * 0.15;
            this.squirrel.setDeltaMovement(pushX, -CLIMB_SPEED, pushZ);
        } else {
            // CLIMB_UP
            this.squirrel.setDeltaMovement(motion.x, CLIMB_SPEED, motion.z);
        }
    }
}
