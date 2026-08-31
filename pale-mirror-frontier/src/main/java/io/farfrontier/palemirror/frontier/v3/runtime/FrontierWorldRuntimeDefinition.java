package io.farfrontier.palemirror.frontier.v3.runtime;
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
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrap;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper;
import io.farfrontier.palemirror.frontier.v3.model.FrontierExecutionSubjects;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldCommandPlanner;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldEventReducer;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldPayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProcessCatalog;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjectionCompiler;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateTransitionValidator;
import java.util.List;
/** Pure composition root for the fresh 1024x1024 Frontier v3 profile. */
public final class FrontierWorldRuntimeDefinition {
    public static final SubjectId PHYSICAL_EXECUTOR = FrontierExecutionSubjects.PHYSICAL_EXECUTOR;
    private static final PayloadCodecs PAYLOAD_CODECS = FrontierWorldPayloadCodecs.create();
    private static final DeterministicProcessRegistry PROCESS_REGISTRY = processRegistry();
    private FrontierWorldRuntimeDefinition() { }
    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration(WorldId worldId, long seed) { return configuration(worldId, seed, true); }
    /** Shared internal composition used by the test-fixture catalog without creating a second runtime. */
    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration(WorldId worldId, long seed, boolean autonomousInterception) {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(worldId, seed); FrontierWorldState initial = FrontierWorldState.initial(bootstrap);
        return new FrontierEngineConfiguration<>(worldId, initial, SimInstant.ZERO, FrontierWorldRuntimeDefinition::planCommand,
                (state, action) -> planScheduled(state, action, autonomousInterception), FrontierWorldRuntimeDefinition::reduce, new FrontierWorldStateCodec(bootstrap), FrontierWorldProjectionCompiler::compile,
                new EngineLimits(4_096, 1_200L, 4_096), FrontierWorldProcessCatalog.initialSchedule(bootstrap), TransactionCommitter.noOp(), FrontierWorldStateTransitionValidator.INSTANCE); }
    public static PayloadCodecs payloadCodecs() { return PAYLOAD_CODECS; }
    public static DeterministicProcessRegistry processRegistry() {
        return new DeterministicProcessRegistry(FrontierWorldProcessCatalog.descriptors(), PAYLOAD_CODECS);
    }
    public static CommandPlan planCommand(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.FrontierCommand command) {
        return FrontierWorldCommandPlanner.plan(state, command, PROCESS_REGISTRY, PHYSICAL_EXECUTOR);
    }
    public static List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planScheduled(FrontierWorldState state, ScheduledAction action) {
        return planScheduled(state, action, true);
    }
    public static List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planScheduled(FrontierWorldState state, ScheduledAction action,
                                                                                                      boolean autonomousInterception) {
        return FrontierWorldProcessCatalog.planScheduled(PROCESS_REGISTRY, state, action, autonomousInterception);
    }
    public static FrontierWorldState reduce(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.FrontierEvent event) {
        return FrontierWorldEventReducer.reduce(state, event, PROCESS_REGISTRY);
    }
}
