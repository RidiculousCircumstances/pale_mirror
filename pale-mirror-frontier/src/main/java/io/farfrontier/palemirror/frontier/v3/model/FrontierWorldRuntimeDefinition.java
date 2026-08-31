package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.FrontierProjection; import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedPosition; import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent; import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind; import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition; import io.farfrontier.palemirror.frontier.v3.api.ProjectionQuery;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.kernel.DeterministicProcessRegistry;
import io.farfrontier.palemirror.frontier.v3.kernel.EngineLimits;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionCommitter;
import java.util.List;
/** Pure composition root for the fresh 1024x1024 Frontier v3 profile. */
public final class FrontierWorldRuntimeDefinition {
    public static final SubjectId PHYSICAL_EXECUTOR = new SubjectId("system:physical_executor");
    private static final PayloadCodecs PAYLOAD_CODECS = FrontierWorldPayloadCodecs.create();
    private static final DeterministicProcessRegistry PROCESS_REGISTRY = processRegistry();
    private FrontierWorldRuntimeDefinition() { }
    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration(WorldId worldId, long seed) { return configuration(worldId, seed, true); }
    /** Shared internal composition used by the test-fixture catalog without creating a second runtime. */
    static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration(WorldId worldId, long seed, boolean autonomousInterception) {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(worldId, seed); FrontierWorldState initial = FrontierWorldState.initial(bootstrap);
        return new FrontierEngineConfiguration<>(worldId, initial, SimInstant.ZERO, FrontierWorldRuntimeDefinition::planCommand,
                (state, action) -> planScheduled(state, action, autonomousInterception), FrontierWorldRuntimeDefinition::reduce, new FrontierWorldStateCodec(bootstrap), FrontierWorldProjectionCompiler::compile,
                new EngineLimits(4_096, 1_200L, 4_096), initialSchedule(bootstrap), TransactionCommitter.noOp(), FrontierWorldStateTransitionValidator.INSTANCE); }
    private static List<ScheduledAction> initialSchedule(FrontierBootstrap bootstrap) {
        List<ScheduledAction> actions = new java.util.ArrayList<>(List.of(StructuralRepairProcess.scan(1, 800),
                RouteConstructionProcess.scan(1, 900), DecontaminationProcess.scan(1, 1_000)));
        for (int index = 0; index < bootstrap.settlements().size(); index++) {
            actions.add(StrategicObjectiveProcess.review(bootstrap.settlements().get(index).id(), 1, 2_000L + index * 100L));
            actions.add(PopulationBirthProcess.review(bootstrap.settlements().get(index).id(), 1, 6_000L + index * 100L));
            actions.add(SettlementProvisionProcess.review(bootstrap.settlements().get(index).id(), 1,
                    SettlementProvisionProcess.INITIAL_REVIEW_TICK + index * 100L));
            actions.add(CompanyFoundationProcess.review(bootstrap.settlements().get(index).id(), 1, 1_000L + index * 100L));
        }
        actions.add(PopulationMigrationProcess.review(1, 8_000L));
        actions.add(TerminalLogisticsProcess.review(1, 8_100L));
        FrontierResourceSitePlan.compile(bootstrap).keySet().stream().sorted().forEach(site -> actions.add(ResourceSiteProcess.preparation(site, ResourceSiteProcess.INITIAL_PREPARATION_TICK)));
        bootstrap.hive().bioforms().stream().filter(value -> value.role() == BioformRole.SCOUT).sorted(java.util.Comparator.comparing(Bioform::id))
                .forEach(scout -> actions.add(HiveScoutPatrolProcess.patrol(scout.id(), 1, 1_600L + actions.size() * 20L)));
        actions.add(StrategicObjectiveProcess.review(bootstrap.hive().id(), 1, 3_200L)); return List.copyOf(actions);
    }
    public static PayloadCodecs payloadCodecs() { return PAYLOAD_CODECS; }
    static DeterministicProcessRegistry processRegistry() {
        return new DeterministicProcessRegistry(FrontierWorldProcessCatalog.descriptors(), PAYLOAD_CODECS);
    }
    static CommandPlan planCommand(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.FrontierCommand command) {
        return FrontierWorldCommandPlanner.plan(state, command, PROCESS_REGISTRY);
    }
    static List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planScheduled(FrontierWorldState state, ScheduledAction action) {
        return planScheduled(state, action, true);
    }
    static List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planScheduled(FrontierWorldState state, ScheduledAction action,
                                                                                               boolean autonomousInterception) {
        return FrontierWorldProcessCatalog.planScheduled(PROCESS_REGISTRY, state, action, autonomousInterception);
    }
    static FrontierWorldState reduce(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.FrontierEvent event) {
        return FrontierWorldEventReducer.reduce(state, event, PROCESS_REGISTRY);
    }
}
