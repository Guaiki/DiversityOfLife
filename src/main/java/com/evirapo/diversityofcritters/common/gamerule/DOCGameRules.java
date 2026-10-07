package com.evirapo.diversityofcritters.common.gamerule;

import net.minecraft.world.level.GameRules;

/**
 * The mod's gamerules. They use the standard syntax, {@code /gamerule doCritterNeeds false}, and
 * show up under Mobs in the Edit Game Rules screen.
 */
public final class DOCGameRules {

    /** Hunger and thirst. Off: both stay full, so no critter starves or dies of thirst. */
    public static final GameRules.Key<GameRules.BooleanValue> CRITTER_NEEDS =
            GameRules.register("doCritterNeeds", GameRules.Category.MOBS, GameRules.BooleanValue.create(true));

    /**
     * Temperature and humidity. Off: every critter is comfortable anywhere, so no climate damage,
     * no hunger/thirst multipliers and no climate block on breeding.
     */
    public static final GameRules.Key<GameRules.BooleanValue> CRITTER_CLIMATE =
            GameRules.register("doCritterClimate", GameRules.Category.MOBS, GameRules.BooleanValue.create(true));

    /**
     * Loads this class, which registers the rules. Must run from the mod constructor: a gamerule
     * registered after a world loads does not exist in that world.
     */
    public static void register() {}

    private DOCGameRules() {}
}
