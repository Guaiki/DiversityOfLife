package com.evirapo.diversityofcritters.mixin.compat;

import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tschipp.carryon.common.carry.CarryOnData;

/**
 * {@code remap = false} a nivel de clase: tanto {@code getEntity} como el campo {@code nbt} son de
 * CarryOn, no de Minecraft, y no están en la tabla de ofuscación. Sin esto el procesador de
 * anotaciones aborta con "Unable to locate obfuscation mapping for @Inject target getEntity" y el
 * jar sale sin refmap, que es lo que hacía crashear el mod en producción.
 */
@Mixin(value = CarryOnData.class, remap = false)
public class CarryOnDataMixin {

    @Shadow private CompoundTag nbt;

    @Inject(method = "getEntity", at = @At("RETURN"))
    private void fixDiverseCritterAge(Level level, CallbackInfoReturnable<Entity> cir) {
        Entity entity = cir.getReturnValue();

        if (entity == null) return;

        if (entity instanceof DiverseCritter critter) {

            if (this.nbt != null && this.nbt.contains("entity")) {
                CompoundTag entityTag = this.nbt.getCompound("entity");

                if (entityTag.contains("DiverseAge")) {
                    int correctAge = entityTag.getInt("DiverseAge");
                    int currentAge = critter.getAge();

                    if (currentAge != correctAge) {
                        critter.setAge(correctAge);
                    }
                }
            }
        }
    }
}