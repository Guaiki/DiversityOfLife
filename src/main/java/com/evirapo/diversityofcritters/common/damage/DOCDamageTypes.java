package com.evirapo.diversityofcritters.common.damage;

import com.evirapo.diversityofcritters.DiversityOfCritters;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;

/**
 * The critters' own causes of death.
 *
 * They exist so the wild population can tell them apart: with vanilla's freeze, onFire, dryOut and
 * starve, an animal killed by its habitat was indistinguishable from one a player set on fire or
 * buried in powder snow. The types themselves are data, in {@code data/diversityofcritters/damage_type/}.
 */
public final class DOCDamageTypes {

    /** Hunger at zero. Counts against the wild population. */
    public static final ResourceKey<DamageType> STARVATION = key("starvation");
    /** Thirst at zero. Does NOT count: excluded by the client. */
    public static final ResourceKey<DamageType> DEHYDRATION = key("dehydration");
    /** Temperature below the comfort range. */
    public static final ResourceKey<DamageType> HYPOTHERMIA = key("hypothermia");
    /** Temperature above the comfort range. */
    public static final ResourceKey<DamageType> HEATSTROKE = key("heatstroke");
    /** Humidity outside the comfort range, either way. */
    public static final ResourceKey<DamageType> HUMIDITY = key("humidity");

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(DiversityOfCritters.MODID, name));
    }

    public static DamageSource source(Level level, ResourceKey<DamageType> type) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type));
    }

    private DOCDamageTypes() {}
}
