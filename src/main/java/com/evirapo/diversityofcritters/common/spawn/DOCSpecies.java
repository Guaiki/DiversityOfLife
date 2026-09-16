package com.evirapo.diversityofcritters.common.spawn;

import com.evirapo.diversityofcritters.common.config.DOCServerConfig;
import com.evirapo.diversityofcritters.common.config.SpeciesSpawnSettings.Snapshot;
import com.evirapo.diversityofcritters.common.entity.DOCEntities;
import com.evirapo.diversityofcritters.common.entity.custom.CivetEntity;
import com.evirapo.diversityofcritters.common.entity.custom.SifakaEntity;
import com.evirapo.diversityofcritters.common.entity.custom.SquirrelEntity;
import com.evirapo.diversityofcritters.common.entity.custom.base.DiverseCritter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/**
 * The species covered by the configurable spawn system and the release registry.
 *
 * The lion is deliberately left out: it has neither spawn rules nor a release button.
 */
public enum DOCSpecies {

    CIVET("civet", "Banded Palm Civet", DOCEntities.CIVET, CivetEntity.class),
    SIFAKA("sifaka", "Verreaux's Sifaka", DOCEntities.SIFAKA, SifakaEntity.class),
    SQUIRREL("squirrel", "Red and White Giant Flying Squirrel", DOCEntities.SQUIRREL, SquirrelEntity.class);

    private final String key;
    private final String displayName;
    private final RegistryObject<? extends EntityType<? extends DiverseCritter>> type;
    private final Class<? extends DiverseCritter> entityClass;

    DOCSpecies(String key,
               String displayName,
               RegistryObject<? extends EntityType<? extends DiverseCritter>> type,
               Class<? extends DiverseCritter> entityClass) {
        this.key = key;
        this.displayName = displayName;
        this.type = type;
        this.entityClass = entityClass;
    }

    /** Identifier used in the config TOML and as the key in the release registry's NBT. */
    public String key() {
        return this.key;
    }

    /** Human-readable name, used only for command output. */
    public String displayName() {
        return this.displayName;
    }

    public EntityType<? extends DiverseCritter> entityType() {
        return this.type.get();
    }

    public Class<? extends DiverseCritter> entityClass() {
        return this.entityClass;
    }

    /** The spawn settings currently in effect for this species. */
    public Snapshot settings() {
        return DOCServerConfig.forSpecies(this.key);
    }

    @Nullable
    public static DOCSpecies byKey(String key) {
        for (DOCSpecies species : values()) {
            if (species.key.equalsIgnoreCase(key)) return species;
        }
        return null;
    }

    /** Returns an entity's species, or null if it is not one of the covered ones. */
    @Nullable
    public static DOCSpecies of(@Nullable Entity entity) {
        if (entity == null) return null;
        return of(entity.getType());
    }

    @Nullable
    public static DOCSpecies of(@Nullable EntityType<?> type) {
        if (type == null) return null;
        for (DOCSpecies species : values()) {
            if (species.type.isPresent() && species.type.get() == type) return species;
        }
        return null;
    }
}
