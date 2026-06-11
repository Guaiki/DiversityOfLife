package com.evirapo.diversityofcritters.common.entity.ai;

import com.evirapo.diversityofcritters.common.entity.custom.SquirrelEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SquirrelNavigation extends CritterNavigation {

    private final SquirrelEntity squirrel;

    public SquirrelNavigation(Mob mob, Level level) {
        super(mob, level);
        this.squirrel = (SquirrelEntity) mob;
    }

    @Override
    protected boolean canUpdatePath() {
        return this.squirrel.isClimbing()
                || this.squirrel.isGliding()
                || this.mob.onGround()
                || this.isInLiquid()
                || this.mob.isPassenger();
    }

    @Override
    protected double getGroundY(Vec3 pVec) {
        if (this.squirrel.isClimbing() || this.squirrel.isGliding()) return pVec.y;
        return super.getGroundY(pVec);
    }

    @Override
    protected Vec3 getTempMobPos() {
        if (this.squirrel.isClimbing() || this.squirrel.isGliding()) return this.mob.position();
        return super.getTempMobPos();
    }
}
