package io.farfrontier.palemirror.frontier.v3.model;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;

/** Closed composition root, not a coordinator-side concrete-family dispatch. */
public final class ActivityExecutionCapabilities {
    private static final ActivityExecutionCapabilities CURRENT = new ActivityExecutionCapabilities(List.of(
            registration(HumanAssignmentKind.IDLE, (state, assignment) -> new ActivityExecutionCheckpoint(
                    state, assignment, ResidentWorkYield.Status.READY)),
            registration(HumanAssignmentKind.PRODUCTION, FrontierProductionWorkSceneSupport::executionCheckpoint),
            registration(HumanAssignmentKind.FIELD_HARVEST, FrontierResourceSiteHarvestSceneSupport::executionCheckpoint,
                    ResourceSiteHarvestLabour::pause, ResourceSiteHarvestLabour::workStatsChanged,
                    FrontierResourceSiteHarvestSceneSupport::waitingForServiceResource),
            held(HumanAssignmentKind.CARGO_TRANSPORT), held(HumanAssignmentKind.ESCORT),
            held(HumanAssignmentKind.ROUTE_PATROL), held(HumanAssignmentKind.SETTLEMENT_DEFENCE),
            held(HumanAssignmentKind.ENGINEERING_RECOVERY), held(HumanAssignmentKind.SETTLEMENT_SERVICE),
            held(HumanAssignmentKind.MEDICAL_EVACUATION), held(HumanAssignmentKind.TRANSIT)));

    private final Map<HumanAssignmentKind, ActivityExecutionCapability> capabilities;

    ActivityExecutionCapabilities(List<? extends ActivityExecutionCapability> registrations) {
        EnumMap<HumanAssignmentKind, ActivityExecutionCapability> values = new EnumMap<>(HumanAssignmentKind.class);
        for (ActivityExecutionCapability capability : registrations) {
            Objects.requireNonNull(capability, "execution capability");
            if (values.putIfAbsent(Objects.requireNonNull(capability.kind(), "declared kind"), capability) != null)
                throw new IllegalArgumentException("duplicate activity execution capability");
        }
        if (!values.keySet().equals(Set.of(HumanAssignmentKind.values())))
            throw new IllegalArgumentException("missing activity execution capability");
        capabilities = Map.copyOf(values);
    }

    public static ResidentWorkYield assess(FrontierWorldState state, HumanAssignment assignment) {
        return CURRENT.evaluate(state, assignment);
    }
    public static boolean waitingForServiceResource(FrontierWorldState state, HumanAssignment assignment) {
        CURRENT.evaluate(state, assignment); // validate exact current assignment and owner checkpoint
        return CURRENT.capabilities.get(assignment.kind()).waitingForServiceResource(state, assignment);
    }

    @FunctionalInterface interface LabourPause {
        FrontierWorldState apply(FrontierWorldState state, HumanAssignment assignment, long tick);
    }
    @FunctionalInterface interface WorkStatsWake {
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> apply(
                FrontierWorldState state, HumanAssignment assignment, long tick);
    }
    public static List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> workStatsChanged(
            FrontierWorldState state, HumanAssignment assignment, long tick) {
        if (!HumanAssignmentProjection.compile(state).assignment(assignment.residentId()).equals(assignment))
            throw new IllegalArgumentException("work-stat wake has a stale assignment");
        return CURRENT.capabilities.get(assignment.kind()).workStatsChanged(state, assignment, tick);
    }
    public static FrontierWorldState pauseLabour(FrontierWorldState state, HumanAssignment assignment, long tick) {
        if (!HumanAssignmentProjection.compile(state).assignment(assignment.residentId()).equals(assignment))
            throw new IllegalArgumentException("labour suspension has a stale assignment");
        return CURRENT.capabilities.get(assignment.kind()).pauseLabour(state, assignment, tick);
    }

    ResidentWorkYield evaluate(FrontierWorldState state, HumanAssignment assignment) {
        if (!HumanAssignmentProjection.compile(state).assignment(assignment.residentId()).equals(assignment))
            throw new IllegalArgumentException("checkpoint requested for a foreign current assignment");
        return Objects.requireNonNull(capabilities.get(assignment.kind()).checkpoint(state, assignment),
                "owner checkpoint").validate(state, assignment);
    }

    /** Explicit unadapted-owner declarations retain the existing fail-closed suspension policy. */
    private static ActivityExecutionCapability held(HumanAssignmentKind kind) {
        return registration(kind, (state, assignment) -> new ActivityExecutionCheckpoint(
                state, assignment, ResidentWorkYield.Status.OWNER_SAFETY_HOLD));
    }

    static ActivityExecutionCapability registration(HumanAssignmentKind kind,
            BiFunction<FrontierWorldState, HumanAssignment, ActivityExecutionCheckpoint> strategy) {
        return registration(kind, strategy, (state, assignment, tick) -> state, (state, assignment, tick) -> List.of(),
                (state, assignment) -> false);
    }

    private static ActivityExecutionCapability registration(HumanAssignmentKind kind,
            BiFunction<FrontierWorldState, HumanAssignment, ActivityExecutionCheckpoint> strategy,
            LabourPause pause, WorkStatsWake wake,
            java.util.function.BiPredicate<FrontierWorldState, HumanAssignment> serviceWait) {
        Objects.requireNonNull(kind, "declared kind"); Objects.requireNonNull(strategy, "strategy"); Objects.requireNonNull(pause);
        return new ActivityExecutionCapability() {
            @Override public HumanAssignmentKind kind() { return kind; }
            @Override public boolean waitingForServiceResource(FrontierWorldState state, HumanAssignment assignment) {
                return serviceWait.test(state, assignment);
            }
            @Override public List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> workStatsChanged(
                    FrontierWorldState state, HumanAssignment assignment, long tick) {
                if (assignment.kind() != kind) throw new IllegalArgumentException("work-stat wake kind mismatch");
                return wake.apply(state, assignment, tick);
            }
            @Override public FrontierWorldState pauseLabour(FrontierWorldState state, HumanAssignment assignment, long tick) {
                if (assignment.kind() != kind) throw new IllegalArgumentException("labour suspension kind mismatch");
                return pause.apply(state, assignment, tick);
            }
            @Override public ActivityExecutionCheckpoint checkpoint(FrontierWorldState state, HumanAssignment assignment) {
                if (assignment.kind() != kind) throw new IllegalArgumentException("capability assignment kind mismatch");
                return strategy.apply(state, assignment);
            }
        };
    }
}
