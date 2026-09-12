package io.farfrontier.palemirror.frontier.v3.process;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Closed, versioned comparison declaration for every physical-capable Frontier family.
 *
 * <p>The declaration deliberately compares the durable semantic boundary rather than a world
 * digest.  Exact identities, custody and completed effects therefore cannot be hidden behind a
 * calibration tolerance.  Only the explicitly combat-shaped families use the fixed-seed
 * distribution rule; quiet work has no statistical escape hatch.</p>
 */
public final class FrontierObserverNeutralityContract {
    public static final int VERSION = 1;

    public enum Measurement { EXACT_QUIET, CALIBRATED_COMBAT }

    /** Fixed before a run.  A caller cannot widen it from observations. */
    public record Calibration(int samples, int maximumSuccessDelta, int maximumCasualtyDelta,
                              int maximumDurationDelta) {
        public Calibration {
            if (samples < 8 || samples > 4_096 || maximumSuccessDelta < 0 || maximumCasualtyDelta < 0
                    || maximumDurationDelta < 0) throw new IllegalArgumentException("observer calibration");
        }
    }

    public record Declaration(FrontierDurationProcessDriverRegistry.Family family, int version,
                              Measurement measurement, Calibration calibration) {
        public Declaration {
            family = Objects.requireNonNull(family, "observer family");
            if (version != VERSION) throw new IllegalArgumentException("observer contract version");
            measurement = Objects.requireNonNull(measurement, "observer measurement");
            calibration = Objects.requireNonNull(calibration, "observer calibration");
            if (measurement == Measurement.EXACT_QUIET && !calibration.equals(QUIET_CALIBRATION)) {
                throw new IllegalArgumentException("quiet observer comparison has no tolerance");
            }
        }
    }

    /** Immutable run facts supplied by the owning family, never derived from player presence. */
    public record Run(Declaration declaration, Set<String> actorIds, Set<String> objectIds,
                      Map<String, Long> claims, Map<String, Long> custody, Set<String> completedStages,
                      Map<String, Long> retainedWork, Set<String> legalTopology, Set<String> confirmedEffects,
                      Map<String, String> recoveryDiscriminators, Set<String> randomOpportunityKeys,
                      CalibrationSample calibration) {
        public Run {
            declaration = Objects.requireNonNull(declaration, "observer declaration");
            actorIds = immutable(actorIds, "actor ids"); objectIds = immutable(objectIds, "object ids");
            claims = immutableMap(claims, "claims"); custody = immutableMap(custody, "custody");
            completedStages = immutable(completedStages, "completed stages"); retainedWork = immutableMap(retainedWork, "retained work");
            legalTopology = immutable(legalTopology, "legal topology"); confirmedEffects = immutable(confirmedEffects, "confirmed effects");
            recoveryDiscriminators = immutableStringMap(recoveryDiscriminators, "recovery discriminators");
            randomOpportunityKeys = immutable(randomOpportunityKeys, "random opportunity keys");
            calibration = Objects.requireNonNull(calibration, "calibration sample");
        }
    }

    /** One aggregate fixed-seed sample set.  Its component identities are retained above. */
    public record CalibrationSample(int samples, int successes, int casualties, long totalDuration) {
        public CalibrationSample {
            if (samples < 0 || successes < 0 || successes > samples || casualties < 0 || totalDuration < 0L) {
                throw new IllegalArgumentException("observer calibration sample");
            }
        }
    }

    private static final Calibration QUIET_CALIBRATION = new Calibration(8, 0, 0, 0);
    private static final Calibration COMBAT_CALIBRATION = new Calibration(16, 3, 3, 1_200);
    private static final Map<FrontierDurationProcessDriverRegistry.Family, Declaration> DECLARATIONS = declarations();

    private FrontierObserverNeutralityContract() { }

    public static List<Declaration> inventory() {
        return DECLARATIONS.values().stream().sorted(Comparator.comparing(value -> value.family().sdkFamilyKey().value())).toList();
    }

    public static Declaration declaration(FrontierDurationProcessDriverRegistry.Family family) {
        Declaration value = DECLARATIONS.get(Objects.requireNonNull(family, "observer family"));
        if (value == null) throw new IllegalArgumentException("missing observer comparison declaration: " + family);
        return value;
    }

    /** Closed-composition fence invoked before a world may execute or recover its descriptors. */
    public static void requireComplete(List<FrontierProcessSceneSdk.DescriptorDefinition> descriptors) {
        Objects.requireNonNull(descriptors, "process descriptors");
        if (DECLARATIONS.size() != FrontierDurationProcessDriverRegistry.Family.values().length) {
            throw new IllegalStateException("observer comparison family inventory is incomplete");
        }
        Map<FrontierProcessSceneSdk.FamilyKey, FrontierProcessSceneSdk.DescriptorDefinition> byKey = new LinkedHashMap<>();
        for (FrontierProcessSceneSdk.DescriptorDefinition descriptor : descriptors) {
            if (byKey.putIfAbsent(descriptor.family(), descriptor) != null) throw new IllegalArgumentException("duplicate process descriptor");
        }
        for (FrontierDurationProcessDriverRegistry.Family family : FrontierDurationProcessDriverRegistry.Family.values()) {
            Declaration declaration = declaration(family);
            if (declaration.version() != VERSION || !byKey.containsKey(family.sdkFamilyKey())) {
                throw new IllegalArgumentException("observer comparison declaration drift: " + family);
            }
        }
    }

    /** Exact continuity plus the predeclared quiet/combat decision rule. */
    public static void requireComparable(Run baseline, Run candidate) {
        Objects.requireNonNull(baseline, "observer baseline"); Objects.requireNonNull(candidate, "observer candidate");
        if (!baseline.declaration().equals(candidate.declaration())) throw new IllegalArgumentException("observer declaration mismatch");
        exact(baseline.actorIds(), candidate.actorIds(), "actor identities");
        exact(baseline.objectIds(), candidate.objectIds(), "object identities");
        exact(baseline.claims(), candidate.claims(), "claims"); exact(baseline.custody(), candidate.custody(), "custody");
        exact(baseline.completedStages(), candidate.completedStages(), "completed stages");
        exact(baseline.retainedWork(), candidate.retainedWork(), "retained work");
        exact(baseline.legalTopology(), candidate.legalTopology(), "legal topology");
        exact(baseline.confirmedEffects(), candidate.confirmedEffects(), "confirmed effects");
        exact(baseline.recoveryDiscriminators(), candidate.recoveryDiscriminators(), "recovery discriminators");
        exact(baseline.randomOpportunityKeys(), candidate.randomOpportunityKeys(), "keyed random opportunities");
        Calibration rule = baseline.declaration().calibration();
        if (baseline.calibration().samples() != rule.samples() || candidate.calibration().samples() != rule.samples()) {
            throw new IllegalArgumentException("observer calibration sample count");
        }
        if (baseline.declaration().measurement() == Measurement.EXACT_QUIET) {
            exact(baseline.calibration(), candidate.calibration(), "quiet labor norm"); return;
        }
        within(baseline.calibration().successes(), candidate.calibration().successes(), rule.maximumSuccessDelta(), "combat successes");
        within(baseline.calibration().casualties(), candidate.calibration().casualties(), rule.maximumCasualtyDelta(), "combat casualties");
        within(baseline.calibration().totalDuration(), candidate.calibration().totalDuration(), rule.maximumDurationDelta(), "combat duration");
    }

    private static Map<FrontierDurationProcessDriverRegistry.Family, Declaration> declarations() {
        Map<FrontierDurationProcessDriverRegistry.Family, Declaration> values = new EnumMap<>(FrontierDurationProcessDriverRegistry.Family.class);
        for (FrontierDurationProcessDriverRegistry.Family family : FrontierDurationProcessDriverRegistry.Family.values()) {
            Measurement measurement = family == FrontierDurationProcessDriverRegistry.Family.SETTLEMENT_ASSAULT
                    || family == FrontierDurationProcessDriverRegistry.Family.ROUTE_ENGAGEMENT
                    ? Measurement.CALIBRATED_COMBAT : Measurement.EXACT_QUIET;
            values.put(family, new Declaration(family, VERSION, measurement,
                    measurement == Measurement.CALIBRATED_COMBAT ? COMBAT_CALIBRATION : QUIET_CALIBRATION));
        }
        return Map.copyOf(values);
    }

    private static Set<String> immutable(Set<String> values, String label) {
        values = Set.copyOf(Objects.requireNonNull(values, label));
        if (values.stream().anyMatch(value -> value == null || value.isBlank())) throw new IllegalArgumentException(label);
        return values;
    }
    private static Map<String, Long> immutableMap(Map<String, Long> values, String label) {
        values = Map.copyOf(Objects.requireNonNull(values, label));
        if (values.entrySet().stream().anyMatch(value -> value.getKey() == null || value.getKey().isBlank() || value.getValue() == null || value.getValue() < 0L)) throw new IllegalArgumentException(label);
        return values;
    }
    private static Map<String, String> immutableStringMap(Map<String, String> values, String label) {
        values = Map.copyOf(Objects.requireNonNull(values, label));
        if (values.entrySet().stream().anyMatch(value -> value.getKey() == null || value.getKey().isBlank() || value.getValue() == null || value.getValue().isBlank())) throw new IllegalArgumentException(label);
        return values;
    }
    private static void exact(Object baseline, Object candidate, String label) {
        if (!baseline.equals(candidate)) throw new IllegalArgumentException("observer exact invariant drift: " + label);
    }
    private static void within(long baseline, long candidate, long tolerance, String label) {
        if (Math.abs(Math.subtractExact(baseline, candidate)) > tolerance) throw new IllegalArgumentException("observer calibration failed: " + label);
    }
}
