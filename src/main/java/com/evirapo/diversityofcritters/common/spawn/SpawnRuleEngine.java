package com.evirapo.diversityofcritters.common.spawn;

import com.evirapo.diversityofcritters.common.config.DOCServerConfig;
import com.evirapo.diversityofcritters.common.config.SpeciesSpawnSettings.Snapshot;
import com.evirapo.diversityofcritters.misc.tags.DoCTags;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralised evaluation of the natural spawn rules.
 *
 * The three {@code checkXxxSpawnRules} methods delegate here.
 *
 * The filters live in {@link #CHECKS}, ordered from cheap to expensive. The spawner walks them
 * short-circuiting on the first failure; {@code /doc_spawn here} walks the same list without
 * short-circuiting, so it can show the state of all of them. Because both share one list, the
 * diagnostic can never drift from what the spawner actually does.
 *
 * Careful: this is also called from the chunk generation threads, so every piece of mutable state
 * in here has to be thread-safe.
 */
public final class SpawnRuleEngine {

    // Tree scan for arboreal species. Sparse sampling with an early exit: in a forest it hits
    // on almost the first block.
    private static final int TREE_SCAN_RADIUS = 9;
    private static final int TREE_SCAN_STEP = 3;
    private static final int TREE_SCAN_HEIGHT = 8;
    private static final int TREE_SCAN_HEIGHT_STEP = 2;

    private static final int TRACE_CAPACITY = 20;

    private static final Set<DOCSpecies> TRACING = ConcurrentHashMap.newKeySet();
    private static final Map<DOCSpecies, Deque<SpawnCheckResult>> TRACE = new EnumMap<>(DOCSpecies.class);

    static {
        for (DOCSpecies species : DOCSpecies.values()) {
            TRACE.put(species, new ArrayDeque<>(TRACE_CAPACITY));
        }
    }

    // ==============================
    //       FILTER CHAIN
    // ==============================

    /** One filter. Returns null when it passes, or the rejection when it fails. */
    @FunctionalInterface
    private interface Check {
        @Nullable SpawnCheckResult test(DOCSpecies species, Snapshot cfg, LevelAccessor level, BlockPos pos);
    }

    private record NamedCheck(String label, Check check) {}

    private static final List<NamedCheck> CHECKS = List.of(
            new NamedCheck("enabled",        SpawnRuleEngine::checkEnabled),
            new NamedCheck("dimension",      SpawnRuleEngine::checkDimension),
            new NamedCheck("Y range",        SpawnRuleEngine::checkY),
            new NamedCheck("block light",    SpawnRuleEngine::checkLight),
            new NamedCheck("sky access",     SpawnRuleEngine::checkSky),
            new NamedCheck("valid ground",   SpawnRuleEngine::checkGround),
            new NamedCheck("nearby trees",   SpawnRuleEngine::checkTrees),
            new NamedCheck("group density",  SpawnRuleEngine::checkDensity)
    );

    // ==============================
    //       SPAWNER API
    // ==============================

    /**
     * Evaluates whether a species may appear at a position.
     *
     * @return the verdict. The spawner only needs {@link SpawnCheckResult#allowed()}.
     */
    public static SpawnCheckResult evaluate(DOCSpecies species, LevelAccessor level,
                                            MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        SpawnCheckResult result = run(species, level, spawnType, pos, random);

        // Only record when someone turned tracing on: without this we would pay one allocation
        // per call on a path walked hundreds of times per tick.
        if (!TRACING.isEmpty() && TRACING.contains(species)) {
            record(species, result);
        }

        return result;
    }

    private static SpawnCheckResult run(DOCSpecies species, LevelAccessor level,
                                        MobSpawnType spawnType, BlockPos pos, RandomSource random) {

        // We only gate natural spawning. Eggs, spawners, /summon and breeding always pass:
        // blocking those would break gameplay and creative mode.
        if (spawnType != MobSpawnType.NATURAL && spawnType != MobSpawnType.CHUNK_GENERATION) {
            return SpawnCheckResult.of(species, SpawnRejectReason.BYPASS_EXPLICIT, pos, spawnType.name());
        }

        Snapshot cfg = species.settings();

        for (NamedCheck named : CHECKS) {
            SpawnCheckResult rejection = named.check().test(species, cfg, level, pos);
            if (rejection != null) return rejection;
        }

        return roll(species, cfg, pos, random);
    }

    /** Final roll, modulated by the wild population (or by the debug override). */
    private static SpawnCheckResult roll(DOCSpecies species, Snapshot cfg, BlockPos pos, RandomSource random) {
        int population = WildPopulation.cachedPopulation(species);
        double chanceBase = cfg.spawnChance();
        double bonus = SpawnBonusFormula.bonus(population);
        double chanceFinal = effectiveChance(species, chanceBase, population);

        boolean won = random.nextDouble() < chanceFinal;

        return new SpawnCheckResult(
                species,
                won ? SpawnRejectReason.PASSED : SpawnRejectReason.CHANCE_ROLL_FAILED,
                chanceBase, bonus, chanceFinal, population, pos.immutable(),
                String.format("%.3f -> %.3f", chanceBase, chanceFinal)
        );
    }

    // ==============================
    //       CHANCE OVERRIDE (debug)
    // ==============================

    // Temporary override of a species' final chance, set by /doc_spawn chance|max.
    // Not persisted and it never touches the TOML: it exists to test at runtime without editing
    // files. Copy-on-write, like the counter cache, because it is read from the worldgen threads.
    private static final double NO_OVERRIDE = -1.0D;
    private static volatile double[] chanceOverrides = freshOverrides();

    private static double[] freshOverrides() {
        double[] array = new double[DOCSpecies.values().length];
        java.util.Arrays.fill(array, NO_OVERRIDE);
        return array;
    }

    /** @param chance 0.0-1.0, or a negative value to remove the override. */
    public static void setChanceOverride(DOCSpecies species, double chance) {
        double[] updated = chanceOverrides.clone();
        updated[species.ordinal()] = chance < 0.0D ? NO_OVERRIDE : chance;
        chanceOverrides = updated;
    }

    /** @return the active override, or a negative value when there is none. */
    public static double chanceOverride(DOCSpecies species) {
        return chanceOverrides[species.ordinal()];
    }

    public static boolean hasChanceOverride(DOCSpecies species) {
        return chanceOverrides[species.ordinal()] >= 0.0D;
    }

    /** Called when the server stops: overrides do not survive the session. */
    public static void clearChanceOverrides() {
        chanceOverrides = freshOverrides();
    }

    /**
     * The chance actually used in the roll.
     *
     * When an override is set it replaces the formula's result, so the population is ignored while
     * it is in place. That is deliberate, so that "set it to 100%" means exactly that.
     */
    private static double effectiveChance(DOCSpecies species, double chanceBase, int population) {
        double override = chanceOverrides[species.ordinal()];
        if (override >= 0.0D) return override;
        return SpawnBonusFormula.finalChance(chanceBase, population);
    }

    // ==============================
    //       FILTERS
    // ==============================

    private static @Nullable SpawnCheckResult checkEnabled(DOCSpecies species, Snapshot cfg,
                                                           LevelAccessor level, BlockPos pos) {
        if (cfg.enabled()) return null;
        return SpawnCheckResult.of(species, SpawnRejectReason.DISABLED, pos, "enabled = false");
    }

    /** During chunk generation the level is still a ServerLevelAccessor. */
    private static @Nullable SpawnCheckResult checkDimension(DOCSpecies species, Snapshot cfg,
                                                             LevelAccessor level, BlockPos pos) {
        if (!(level instanceof ServerLevelAccessor serverLevel)) return null;

        ResourceKey<Level> dimension = serverLevel.getLevel().dimension();
        if (dimension == Level.OVERWORLD) return null;

        return SpawnCheckResult.of(species, SpawnRejectReason.WRONG_DIMENSION, pos,
                dimension.location().toString());
    }

    private static @Nullable SpawnCheckResult checkY(DOCSpecies species, Snapshot cfg,
                                                     LevelAccessor level, BlockPos pos) {
        int y = pos.getY();
        if (y >= cfg.minY() && y <= cfg.maxY()) return null;

        return SpawnCheckResult.of(species, SpawnRejectReason.Y_OUT_OF_RANGE, pos,
                y + " not in [" + cfg.minY() + ", " + cfg.maxY() + "]");
    }

    private static @Nullable SpawnCheckResult checkLight(DOCSpecies species, Snapshot cfg,
                                                         LevelAccessor level, BlockPos pos) {
        int blockLight = level.getBrightness(LightLayer.BLOCK, pos);
        if (blockLight <= cfg.maxBlockLight()) return null;

        return SpawnCheckResult.of(species, SpawnRejectReason.TOO_BRIGHT, pos,
                blockLight + " > " + cfg.maxBlockLight());
    }

    private static @Nullable SpawnCheckResult checkSky(DOCSpecies species, Snapshot cfg,
                                                       LevelAccessor level, BlockPos pos) {
        boolean seesSky = level.canSeeSky(pos);

        return switch (cfg.skyAccess()) {
            case REQUIRED -> seesSky ? null
                    : SpawnCheckResult.of(species, SpawnRejectReason.SKY_REQUIRED, pos, "covered");
            case FORBIDDEN -> !seesSky ? null
                    : SpawnCheckResult.of(species, SpawnRejectReason.SKY_FORBIDDEN, pos, "open sky");
            case NONE -> null;
        };
    }

    /** The free space above is already checked by SpawnPlacements.Type.ON_GROUND. */
    private static @Nullable SpawnCheckResult checkGround(DOCSpecies species, Snapshot cfg,
                                                          LevelAccessor level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState ground = level.getBlockState(below);
        if (ground.is(DoCTags.Blocks.CRITTERS_SPAWNABLE_ON)) return null;

        return SpawnCheckResult.of(species, SpawnRejectReason.NO_VALID_GROUND, pos,
                ground.getBlock().getDescriptionId());
    }

    /** The squirrel's arboreal requirement: with no logs nearby it has nowhere to live. */
    private static @Nullable SpawnCheckResult checkTrees(DOCSpecies species, Snapshot cfg,
                                                         LevelAccessor level, BlockPos pos) {
        if (species != DOCSpecies.SQUIRREL) return null;
        if (hasTreesNearby(level, pos)) return null;

        return SpawnCheckResult.of(species, SpawnRejectReason.NO_TREES_NEARBY, pos,
                "none within " + TREE_SCAN_RADIUS);
    }

    /** The most expensive filter, which is why it runs last before the roll. */
    private static @Nullable SpawnCheckResult checkDensity(DOCSpecies species, Snapshot cfg,
                                                           LevelAccessor level, BlockPos pos) {
        int radius = DOCServerConfig.globals().groupCheckRadius();
        int nearby = countNearby(species, level, pos, radius);
        if (nearby < cfg.groupMax()) return null;

        return SpawnCheckResult.of(species, SpawnRejectReason.GROUP_LIMIT, pos,
                nearby + " within " + radius + " (max " + cfg.groupMax() + ")");
    }

    /** Looks for logs in a sparse sample around the position, exiting early on the first hit. */
    private static boolean hasTreesNearby(LevelAccessor level, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -TREE_SCAN_RADIUS; dx <= TREE_SCAN_RADIUS; dx += TREE_SCAN_STEP) {
            for (int dz = -TREE_SCAN_RADIUS; dz <= TREE_SCAN_RADIUS; dz += TREE_SCAN_STEP) {
                cursor.set(pos.getX() + dx, pos.getY(), pos.getZ() + dz);

                // During chunk generation the level only exposes a window of chunks: reading
                // outside it would error, so we skip the column.
                if (!level.hasChunkAt(cursor)) continue;

                for (int dy = 0; dy <= TREE_SCAN_HEIGHT; dy += TREE_SCAN_HEIGHT_STEP) {
                    cursor.setY(pos.getY() + dy);
                    if (level.getBlockState(cursor).is(DoCTags.Blocks.CIVET_CLIMBABLE)) return true;
                }
            }
        }

        return false;
    }

    /**
     * Counts animals of the species within a radius.
     *
     * During chunk generation it returns 0: WorldGenRegion.getEntities() always hands back an empty
     * list. That is the correct behaviour, the density cap only makes sense in a live world.
     */
    private static int countNearby(DOCSpecies species, LevelAccessor level, BlockPos pos, int radius) {
        AABB box = new AABB(pos).inflate(radius);
        return level.getEntitiesOfClass(species.entityClass(), box).size();
    }

    // ==============================
    //       DIAGNOSTICS (/doc_spawn here)
    // ==============================

    /** One line of the {@code /doc_spawn here} report. */
    public record DiagnosticLine(String label, boolean passed, String detail) {}

    /** The full report: every filter evaluated, without short-circuiting. */
    public record SpawnDiagnostic(DOCSpecies species, BlockPos pos, List<DiagnosticLine> lines,
                                  @Nullable SpawnRejectReason firstFailure,
                                  double chanceBase, double bonus, double chanceFinal, int population) {

        public boolean wouldSpawn() {
            return this.firstFailure == null;
        }
    }

    /**
     * Evaluates every filter at a position without stopping at the first failure.
     *
     * It does not roll the dice: it reports the chance that would be used. Meant to answer
     * "why is nothing spawning here?" at a glance.
     */
    public static SpawnDiagnostic diagnose(DOCSpecies species, LevelAccessor level, BlockPos pos) {
        Snapshot cfg = species.settings();

        List<DiagnosticLine> lines = new ArrayList<>(CHECKS.size());
        SpawnRejectReason firstFailure = null;

        for (NamedCheck named : CHECKS) {
            SpawnCheckResult rejection = named.check().test(species, cfg, level, pos);

            if (rejection == null) {
                lines.add(new DiagnosticLine(named.label(), true, describePass(named.label(), species, cfg, level, pos)));
            } else {
                lines.add(new DiagnosticLine(named.label(), false, rejection.detail()));
                if (firstFailure == null) firstFailure = rejection.reason();
            }
        }

        int population = WildPopulation.cachedPopulation(species);

        return new SpawnDiagnostic(species, pos.immutable(), lines, firstFailure,
                cfg.spawnChance(), SpawnBonusFormula.bonus(population),
                effectiveChance(species, cfg.spawnChance(), population), population);
    }

    /** The concrete value observed by a filter that passed, so the report says more than "OK". */
    private static String describePass(String label, DOCSpecies species, Snapshot cfg,
                                       LevelAccessor level, BlockPos pos) {
        int radius = DOCServerConfig.globals().groupCheckRadius();

        return switch (label) {
            case "enabled" -> "true";
            case "dimension" -> level instanceof ServerLevelAccessor sla
                    ? sla.getLevel().dimension().location().toString() : "unknown";
            case "Y range" -> pos.getY() + " in [" + cfg.minY() + ", " + cfg.maxY() + "]";
            case "block light" -> level.getBrightness(LightLayer.BLOCK, pos) + " <= " + cfg.maxBlockLight();
            case "sky access" -> cfg.skyAccess().name() + (level.canSeeSky(pos) ? " (open sky)" : " (covered)");
            case "valid ground" -> level.getBlockState(pos.below()).getBlock().getDescriptionId();
            case "nearby trees" -> species == DOCSpecies.SQUIRREL
                    ? "found within " + TREE_SCAN_RADIUS : "n/a for this species";
            case "group density" -> countNearby(species, level, pos, radius)
                    + " within " + radius + " (max " + cfg.groupMax() + ")";
            default -> "ok";
        };
    }

    // ==============================
    //       TRACE (/doc_spawn why)
    // ==============================

    public static void setTracing(DOCSpecies species, boolean enabled) {
        if (enabled) {
            TRACING.add(species);
        } else {
            TRACING.remove(species);
            clearTrace(species);
        }
    }

    public static boolean isTracing(DOCSpecies species) {
        return TRACING.contains(species);
    }

    /** A copy of the last recorded attempts, newest first. */
    public static List<SpawnCheckResult> trace(DOCSpecies species) {
        Deque<SpawnCheckResult> buffer = TRACE.get(species);
        synchronized (buffer) {
            return new ArrayList<>(buffer);
        }
    }

    public static void clearTrace(DOCSpecies species) {
        Deque<SpawnCheckResult> buffer = TRACE.get(species);
        synchronized (buffer) {
            buffer.clear();
        }
    }

    private static void record(DOCSpecies species, SpawnCheckResult result) {
        Deque<SpawnCheckResult> buffer = TRACE.get(species);
        synchronized (buffer) {
            buffer.addFirst(result);
            while (buffer.size() > TRACE_CAPACITY) buffer.removeLast();
        }
    }

    private SpawnRuleEngine() {}
}
