package io.farfrontier.palemirror.frontier.v3.process;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Internal, pure contract surface for a duration-bearing canonical process and its temporary
 * physical authority.
 *
 * <p>This is deliberately not an adapter API for mods.  It is the one testable vocabulary used
 * by Frontier's process/scene conformance suite.  A descriptor owns all family-specific
 * production calls; this class knows only the semantic lifecycle.  Consequently adding a new
 * descriptor cannot make generic lifecycle execution acquire a type switch or a second process
 * state machine.</p>
 */
public final class FrontierProcessSceneSdk {
    private FrontierProcessSceneSdk() { }

    /** A closed registry key, rather than a Java class name or an ad-hoc test label. */
    public record FamilyKey(String value) {
        public FamilyKey {
            if (value == null || !value.matches("[a-z0-9][a-z0-9_.-]{2,95}")) {
                throw new IllegalArgumentException("process/scene family key");
            }
        }
    }

    /**
     * The six execution shapes Frontier is permitted to compose.  This is deliberately a
     * semantic classification, not a scene-cause enum: a provider cannot make a duration job
     * look like an intent merely to fit a convenient executor.
     */
    public enum ExecutionArchetype {
        DURATION_WORK(Set.of(ExecutionPrimitive.COLD_DRIVER, ExecutionPrimitive.HOT_DRIVER)),
        COORDINATED_TRAVERSAL(Set.of(ExecutionPrimitive.COLD_DRIVER, ExecutionPrimitive.HOT_DRIVER)),
        COMPOSITE_OPERATION(Set.of(ExecutionPrimitive.PARENT_PHASES, ExecutionPrimitive.CHILD_FRONTS)),
        ATOMIC_INTENT(Set.of(ExecutionPrimitive.DURABLE_INTENT, ExecutionPrimitive.POSTCONDITION)),
        SPATIAL_FRONTIER(Set.of(ExecutionPrimitive.RETAINED_FRONTIER)),
        AMBIENT_CUSTODY(Set.of(ExecutionPrimitive.CUSTODY_LEASE));

        private final Set<ExecutionPrimitive> requiredPrimitives;

        ExecutionArchetype(Set<ExecutionPrimitive> requiredPrimitives) {
            this.requiredPrimitives = Set.copyOf(requiredPrimitives);
        }

        void validate(ExecutionBinding binding) {
            if (!requiredPrimitives.equals(binding.providers().keySet())) {
                throw new IllegalArgumentException("process/scene execution primitive mismatch for " + this
                        + ": required=" + requiredPrimitives + " actual=" + binding.providers().keySet());
            }
        }
    }

    /** Stable execution primitive names, each resolved to one provider identity by a descriptor. */
    public enum ExecutionPrimitive {
        COLD_DRIVER,
        HOT_DRIVER,
        PARENT_PHASES,
        CHILD_FRONTS,
        DURABLE_INTENT,
        POSTCONDITION,
        RETAINED_FRONTIER,
        CUSTODY_LEASE
    }

    /** Exact named production bindings required by an execution archetype. */
    public record ExecutionBinding(Map<ExecutionPrimitive, String> providers) {
        public ExecutionBinding {
            providers = Map.copyOf(Objects.requireNonNull(providers, "process/scene providers"));
            for (Map.Entry<ExecutionPrimitive, String> provider : providers.entrySet()) {
                if (provider.getKey() == null || provider.getValue() == null
                        || !provider.getValue().matches("[a-z0-9][a-z0-9_.:-]{2,127}")) {
                    throw new IllegalArgumentException("process/scene provider identity");
                }
            }
        }

        public static ExecutionBinding paired(String coldDriver, String hotDriver) {
            return new ExecutionBinding(Map.of(ExecutionPrimitive.COLD_DRIVER, coldDriver,
                    ExecutionPrimitive.HOT_DRIVER, hotDriver));
        }

        public static ExecutionBinding composite(String parentPhases, String childFronts) {
            return new ExecutionBinding(Map.of(ExecutionPrimitive.PARENT_PHASES, parentPhases,
                    ExecutionPrimitive.CHILD_FRONTS, childFronts));
        }

        public static ExecutionBinding intent(String durableIntent, String postcondition) {
            return new ExecutionBinding(Map.of(ExecutionPrimitive.DURABLE_INTENT, durableIntent,
                    ExecutionPrimitive.POSTCONDITION, postcondition));
        }

        public static ExecutionBinding frontier(String retainedFrontier) {
            return new ExecutionBinding(Map.of(ExecutionPrimitive.RETAINED_FRONTIER, retainedFrontier));
        }

        public static ExecutionBinding custody(String custodyLease) {
            return new ExecutionBinding(Map.of(ExecutionPrimitive.CUSTODY_LEASE, custodyLease));
        }
    }

    /** Stable production vocabulary: commands, observations, durable payload codecs and lifecycle facts. */
    public record Vocabulary(Set<String> commands, Set<String> observations, Set<String> payloadCodecs,
                             Set<String> lifecycleTransitions) {
        public Vocabulary {
            commands = vocabulary(commands, "commands");
            observations = vocabulary(observations, "observations");
            payloadCodecs = vocabulary(payloadCodecs, "payload codecs");
            lifecycleTransitions = vocabulary(lifecycleTransitions, "lifecycle transitions");
        }

        private static Set<String> vocabulary(Set<String> values, String name) {
            values = Set.copyOf(Objects.requireNonNull(values, "process/scene " + name));
            if (values.isEmpty() || values.stream().anyMatch(value -> value == null
                    || !value.matches("[a-z0-9][a-z0-9_.:-]{2,127}"))) {
                throw new IllegalArgumentException("process/scene " + name);
            }
            return values;
        }
    }

    /**
     * Hard descriptor ceilings.  Zero/negative values and sentinel maxima are forbidden so an
     * adapter cannot enter the internal SDK with an unbounded local scene disguised as metadata.
     */
    public record Limits(int maxActors, int maxCargo, int maxEffects, int maxLocalChunks,
                         int maxNavigationNodes, int maxObservationsPerTick, int maxRetainedRecords,
                         int maxRetainedBytes, int cadenceTicks, int maxWorkWeight) {
        private static final int MAX_ACTORS = 256;
        private static final int MAX_CARGO = 256;
        private static final int MAX_EFFECTS = 512;
        private static final int MAX_LOCAL_CHUNKS = 64;
        private static final int MAX_NAVIGATION_NODES = 4_096;
        private static final int MAX_OBSERVATIONS_PER_TICK = 128;
        private static final int MAX_RETAINED_RECORDS = 4_096;
        private static final int MAX_RETAINED_BYTES = 1_048_576;
        private static final int MAX_CADENCE_TICKS = 24_000;
        private static final int MAX_WORK_WEIGHT = 4_096;

        public Limits {
            requireRange(maxActors, MAX_ACTORS, "actors");
            requireRange(maxCargo, MAX_CARGO, "cargo");
            requireRange(maxEffects, MAX_EFFECTS, "effects");
            requireRange(maxLocalChunks, MAX_LOCAL_CHUNKS, "local chunks");
            requireRange(maxNavigationNodes, MAX_NAVIGATION_NODES, "navigation nodes");
            requireRange(maxObservationsPerTick, MAX_OBSERVATIONS_PER_TICK, "observations per tick");
            requireRange(maxRetainedRecords, MAX_RETAINED_RECORDS, "retained records");
            requireRange(maxRetainedBytes, MAX_RETAINED_BYTES, "retained bytes");
            requireRange(cadenceTicks, MAX_CADENCE_TICKS, "cadence ticks");
            requireRange(maxWorkWeight, MAX_WORK_WEIGHT, "work weight");
        }

        private static void requireRange(int value, int maximum, String name) {
            if (value < 1 || value > maximum) throw new IllegalArgumentException("bounded process/scene " + name);
        }
    }

    /**
     * Versioned internal declaration for one canonical owner.  It is compared before the
     * descriptor is used for recovery or execution; a retained-meaning change is therefore a
     * versioned migration decision, never an implicit rebind to a newer adapter.
     */
    public record DescriptorDefinition(FamilyKey family, int schemaVersion, ExecutionArchetype archetype,
                                       String canonicalOwner, Vocabulary vocabulary, ExecutionBinding binding,
                                       Limits limits) {
        public DescriptorDefinition {
            family = Objects.requireNonNull(family, "process/scene family");
            if (schemaVersion < 1 || schemaVersion > 65_535) throw new IllegalArgumentException("process/scene descriptor schema");
            archetype = Objects.requireNonNull(archetype, "process/scene archetype");
            canonicalOwner = SemanticCheckpoint.required(canonicalOwner, "canonical process owner");
            vocabulary = Objects.requireNonNull(vocabulary, "process/scene vocabulary");
            binding = Objects.requireNonNull(binding, "process/scene execution binding");
            limits = Objects.requireNonNull(limits, "process/scene limits");
            archetype.validate(binding);
        }

        /** Deterministic persisted-inventory material; collection iteration order is never used. */
        public String canonicalFingerprintMaterial() {
            return family.value() + "|" + schemaVersion + "|" + archetype + "|" + canonicalOwner + "|"
                    + join(vocabulary.commands()) + "|" + join(vocabulary.observations()) + "|" + join(vocabulary.payloadCodecs()) + "|"
                    + join(vocabulary.lifecycleTransitions()) + "|" + binding.providers().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).map(entry -> entry.getKey() + "=" + entry.getValue()).collect(java.util.stream.Collectors.joining(","))
                    + "|" + limits.maxActors() + ":" + limits.maxCargo() + ":" + limits.maxEffects() + ":" + limits.maxLocalChunks()
                    + ":" + limits.maxNavigationNodes() + ":" + limits.maxObservationsPerTick() + ":" + limits.maxRetainedRecords()
                    + ":" + limits.maxRetainedBytes() + ":" + limits.cadenceTicks() + ":" + limits.maxWorkWeight();
        }

        private static String join(Set<String> values) {
            return values.stream().sorted().collect(java.util.stream.Collectors.joining(","));
        }
    }

    /**
     * Family-neutral canonical facts compared by the generic matrix.  The descriptor's digest
     * is computed from its real aggregate/reducer state; it is not a rendered trajectory.
     */
    public record SemanticCheckpoint(FamilyKey family, String ownerId, String actorId, int cursor,
                                     long authorityEpoch, boolean hotAuthority,
                                     Set<String> claims, Set<String> schedules,
                                     String custodyDigest, String canonicalDigest) {
        public SemanticCheckpoint {
            family = Objects.requireNonNull(family, "process family");
            ownerId = required(ownerId, "process owner");
            actorId = required(actorId, "process actor");
            if (cursor < 0 || authorityEpoch < 0) throw new IllegalArgumentException("process cursor or authority epoch");
            claims = Set.copyOf(Objects.requireNonNull(claims, "process claims"));
            schedules = Set.copyOf(Objects.requireNonNull(schedules, "process schedules"));
            custodyDigest = required(custodyDigest, "process custody digest");
            canonicalDigest = required(canonicalDigest, "process canonical digest");
        }

        private static String required(String value, String name) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException(name);
            return value;
        }
    }

    /**
     * A rejected attempt retains the same canonical test context and a bounded reason supplied
     * by the production admission boundary.  Generic code proves the retained semantic facts;
     * a family adapter supplies its own typed rejected command/codec/lease attempt.
     */
    public record RejectedAttempt<S>(S retained, String reason) {
        public RejectedAttempt {
            retained = Objects.requireNonNull(retained, "rejected retained state");
            reason = SemanticCheckpoint.required(reason, "rejection reason");
        }
    }

    /**
     * A typed external intervention admitted by the affected process itself.  It is deliberately
     * separate from {@link RejectedAttempt}: a death, obstruction or theft may correctly change
     * canonical state, but generic SDK code still proves that its resulting state survives the
     * same production snapshot/WAL recovery boundary.
     */
    public record ProcessOwnedIntervention<S>(S transitioned, String reason) {
        public ProcessOwnedIntervention {
            transitioned = Objects.requireNonNull(transitioned, "process-owned intervention state");
            reason = SemanticCheckpoint.required(reason, "process-owned intervention reason");
        }
    }

    /**
     * One production-backed binding.  {@code S} is an owning test context around real Frontier
     * state and records; it may not be a mirror state machine.  Every transition must invoke the
     * family's production planner/reducer/scheduler/codec boundary as applicable.
     */
    public interface Descriptor<S> {
        DescriptorDefinition definition();
        default FamilyKey family() { return definition().family(); }
        S initial();
        S coldAdvance(S state);
        S acquireHot(S state);
        S hotCheckpoint(S state);
        S releaseToCold(S state);
        S snapshotWalRecovery(S state);
        SemanticCheckpoint checkpoint(S state);

        RejectedAttempt<S> rejectStaleObservation(S state);
        RejectedAttempt<S> rejectSecondCursor(S state);
        RejectedAttempt<S> rejectDuplicateSchedule(S state);
        RejectedAttempt<S> rejectUnregisteredPayload(S state);
        RejectedAttempt<S> rejectMissingCodec(S state);
        RejectedAttempt<S> rejectConcurrentAuthority(S state);
        ProcessOwnedIntervention<S> interventionOwnedByProcess(S state);
    }

    /**
     * Executes the same HOT/COLD/recovery matrix for every descriptor.  It purposefully calls no
     * concrete process family and has no knowledge of a particular aggregate, lease class or
     * payload type.
     */
    public static <S> void verify(Descriptor<S> descriptor) {
        Objects.requireNonNull(descriptor, "process/scene descriptor");
        Objects.requireNonNull(descriptor.definition(), "process/scene descriptor definition");
        S initial = nonNull(descriptor.initial(), "initial");
        assertCheckpoint(descriptor, initial, false);

        S coldOne = nonNull(descriptor.coldAdvance(initial), "first COLD advance");
        assertCheckpoint(descriptor, coldOne, false);
        assertRecovery(descriptor, coldOne);
        S coldTwo = nonNull(descriptor.coldAdvance(coldOne), "second COLD advance");
        assertCheckpoint(descriptor, coldTwo, false);
        assertRecovery(descriptor, coldTwo);

        S firstHot = nonNull(descriptor.acquireHot(coldOne), "first HOT acquisition");
        assertCheckpoint(descriptor, firstHot, true);
        S firstCheckpoint = nonNull(descriptor.hotCheckpoint(firstHot), "first HOT checkpoint");
        assertCheckpoint(descriptor, firstCheckpoint, true);
        assertRecovery(descriptor, firstCheckpoint);
        assertRejected(descriptor, firstCheckpoint, descriptor.rejectStaleObservation(firstCheckpoint), "stale observation");
        assertRejected(descriptor, firstCheckpoint, descriptor.rejectSecondCursor(firstCheckpoint), "second cursor");
        assertRejected(descriptor, firstCheckpoint, descriptor.rejectDuplicateSchedule(firstCheckpoint), "duplicate schedule");
        assertRejected(descriptor, firstCheckpoint, descriptor.rejectUnregisteredPayload(firstCheckpoint), "unregistered payload");
        assertRejected(descriptor, firstCheckpoint, descriptor.rejectMissingCodec(firstCheckpoint), "missing codec");
        assertRejected(descriptor, firstCheckpoint, descriptor.rejectConcurrentAuthority(firstCheckpoint), "concurrent authority");
        ProcessOwnedIntervention<S> intervention = Objects.requireNonNull(descriptor.interventionOwnedByProcess(firstCheckpoint),
                "process-owned intervention outcome");
        // An intervention is permitted to alter the aggregate, but not to bypass durability.
        // Typed descriptors prove their own terminal ownership result; the family-neutral SDK
        // proves the exact resulting canonical state is snapshot/WAL recoverable.
        assertRecovery(descriptor, intervention.transitioned());
        S firstReleased = nonNull(descriptor.releaseToCold(firstCheckpoint), "first HOT release");
        assertCheckpoint(descriptor, firstReleased, false);
        assertRecovery(descriptor, firstReleased);

        // A release is not the end of a duration process.  The exact same retained owner must
        // be able to take its next real COLD step after the physical authority has gone away.
        // Keep an independent all-COLD control lane so a descriptor cannot merely make release
        // look plausible while leaving a stale schedule, claim or cursor behind.
        S coldThree = nonNull(descriptor.coldAdvance(coldTwo), "third COLD advance");
        assertCheckpoint(descriptor, coldThree, false);
        assertRecovery(descriptor, coldThree);
        S coldAfterRelease = nonNull(descriptor.coldAdvance(firstReleased), "COLD continuation after HOT release");
        assertCheckpoint(descriptor, coldAfterRelease, false);
        assertRecovery(descriptor, coldAfterRelease);
        assertProgressEquivalent(descriptor, descriptor.checkpoint(coldThree), descriptor.checkpoint(coldAfterRelease),
                "COLD continuation after HOT release diverged from COLD execution");

        S secondHot = nonNull(descriptor.acquireHot(coldAfterRelease), "second HOT acquisition");
        assertCheckpoint(descriptor, secondHot, true);
        S secondCheckpoint = nonNull(descriptor.hotCheckpoint(secondHot), "second HOT checkpoint");
        assertCheckpoint(descriptor, secondCheckpoint, true);
        S secondReleased = nonNull(descriptor.releaseToCold(secondCheckpoint), "second HOT release");
        assertCheckpoint(descriptor, secondReleased, false);
        assertRecovery(descriptor, secondReleased);

        // Selecting a non-intervening physical executor may not alter the semantic COLD step.
        assertProgressEquivalent(descriptor, descriptor.checkpoint(coldTwo), descriptor.checkpoint(firstReleased),
                "HOT/COLD hand-off diverged from COLD execution");
    }

    /** Validates an exact closed conformance set without branching on a family in generic code. */
    public static void requireDistinctFamilies(List<? extends Descriptor<?>> descriptors) {
        Objects.requireNonNull(descriptors, "process/scene descriptors");
        Set<FamilyKey> families = new java.util.HashSet<>();
        for (Descriptor<?> descriptor : List.copyOf(descriptors)) {
            if (descriptor == null || !families.add(descriptor.definition().family())) {
                throw new IllegalArgumentException("process/scene descriptor family must be present exactly once");
            }
        }
    }

    private static <S> void assertCheckpoint(Descriptor<S> descriptor, S state, boolean hot) {
        SemanticCheckpoint checkpoint = Objects.requireNonNull(descriptor.checkpoint(state), "semantic checkpoint");
        if (!descriptor.family().equals(checkpoint.family())) throw new AssertionError("descriptor returned a foreign family checkpoint");
        if (checkpoint.hotAuthority() != hot) throw new AssertionError("descriptor returned wrong HOT authority state for " + checkpoint.family());
    }

    private static <S> void assertRecovery(Descriptor<S> descriptor, S state) {
        S recovered = nonNull(descriptor.snapshotWalRecovery(state), "snapshot/WAL recovery");
        assertEquivalent(descriptor, descriptor.checkpoint(state), descriptor.checkpoint(recovered), "snapshot/WAL recovery drifted");
    }

    private static <S> void assertRejected(Descriptor<S> descriptor, S before, RejectedAttempt<S> attempt, String name) {
        RejectedAttempt<S> checked = Objects.requireNonNull(attempt, "rejected " + name + " outcome");
        assertEquivalent(descriptor, descriptor.checkpoint(before), descriptor.checkpoint(checked.retained()),
                name + " was accepted or changed canonical semantic state");
    }

    private static void assertEquivalent(Descriptor<?> descriptor, SemanticCheckpoint expected,
                                         SemanticCheckpoint actual, String message) {
        if (!expected.family().equals(actual.family()) || !expected.ownerId().equals(actual.ownerId())
                || !expected.actorId().equals(actual.actorId()) || expected.cursor() != actual.cursor()
                || expected.authorityEpoch() != actual.authorityEpoch()
                || expected.hotAuthority() != actual.hotAuthority()
                || !expected.claims().equals(actual.claims()) || !expected.schedules().equals(actual.schedules())
                || !expected.custodyDigest().equals(actual.custodyDigest())
                || !expected.canonicalDigest().equals(actual.canonicalDigest())) {
            throw new AssertionError(message + ": " + descriptor.family());
        }
    }

    /**
     * HOT/COLD differential comparison.  A completed HOT lease retains its own durable epoch
     * as audit provenance, while the all-COLD control lane has no such lease at all.  That
     * executor-local provenance is intentionally not an outcome difference; owner, actor,
     * cursor, custody, schedules and canonical aggregate must still agree exactly.
     */
    private static void assertProgressEquivalent(Descriptor<?> descriptor, SemanticCheckpoint expected,
                                                 SemanticCheckpoint actual, String message) {
        if (!expected.family().equals(actual.family()) || !expected.ownerId().equals(actual.ownerId())
                || !expected.actorId().equals(actual.actorId()) || expected.cursor() != actual.cursor()
                || !expected.claims().equals(actual.claims()) || !expected.schedules().equals(actual.schedules())
                || !expected.custodyDigest().equals(actual.custodyDigest())
                || !expected.canonicalDigest().equals(actual.canonicalDigest())) {
            throw new AssertionError(message + ": " + descriptor.family());
        }
    }

    private static <S> S nonNull(S value, String name) { return Objects.requireNonNull(value, name); }
}
