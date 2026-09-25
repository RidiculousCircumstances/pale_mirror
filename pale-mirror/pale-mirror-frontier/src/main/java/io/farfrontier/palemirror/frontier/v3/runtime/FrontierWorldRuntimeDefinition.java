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
import io.farfrontier.palemirror.frontier.v3.model.FrontierRuleset;
import io.farfrontier.palemirror.frontier.v3.model.FrontierRulesets;
import io.farfrontier.palemirror.frontier.v3.process.FrontierWorldCommandPlanner;
import io.farfrontier.palemirror.frontier.v3.process.FrontierDurationProcessDriverRegistry;
import io.farfrontier.palemirror.frontier.v3.process.FrontierWorldEventReducer;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldPayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldProcessCodecs;
import io.farfrontier.palemirror.frontier.v3.process.FrontierWorldProcessCatalog;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjectionCompiler;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateTransitionValidator;
import java.util.List;
import java.util.Set;
/** Pure composition root for the fresh 1024x1024 Frontier v3 profile. */
public final class FrontierWorldRuntimeDefinition {
    public static final SubjectId PHYSICAL_EXECUTOR = FrontierExecutionSubjects.PHYSICAL_EXECUTOR;
    private static final PayloadCodecs PAYLOAD_CODECS = FrontierWorldPayloadCodecs.create();
    private static final DeterministicProcessRegistry PROCESS_REGISTRY = processRegistry();
    private FrontierWorldRuntimeDefinition() { }
    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration(WorldId worldId, long seed) {
        return configuration(worldId, seed, FrontierRulesets.production(), true);
    }
    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration(WorldId worldId, long seed, FrontierRuleset ruleset) {
        return configuration(worldId, seed, ruleset, true);
    }
    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration(WorldId worldId, long seed, boolean autonomousInterception) {
        return configuration(worldId, seed, FrontierRulesets.production(), autonomousInterception);
    }
    private static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration(WorldId worldId, long seed,
                                                                                                           FrontierRuleset ruleset, boolean autonomousInterception) {
        return configuration(FrontierBootstrapper.create(worldId, seed, ruleset), autonomousInterception);
    }
    /** Explicit fresh-world manifest; its field geometry is persisted and pinned at recovery. */
    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration(FrontierBootstrap bootstrap) {
        return configuration(bootstrap, true);
    }
    private static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration(FrontierBootstrap bootstrap,
                                                                                                           boolean autonomousInterception) {
        FrontierWorldState initial = FrontierWorldState.initial(bootstrap);
        return new FrontierEngineConfiguration<>(bootstrap.worldId(), initial, SimInstant.ZERO, FrontierWorldRuntimeDefinition::planCommand,
                new io.farfrontier.palemirror.frontier.v3.kernel.ScheduledActionPlanner<FrontierWorldState>() {
                    @Override public List<ProposedEvent> plan(FrontierWorldState state, ScheduledAction action) {
                        return planScheduled(state, action, autonomousInterception);
                    }
                    @Override public boolean held(FrontierWorldState state, ScheduledAction action) {
                        return scheduledHeld(state, action);
                    }
                    @Override public List<ScheduledAction> retiredBy(FrontierWorldState previous, FrontierWorldState next,
                                                                    io.farfrontier.palemirror.frontier.v3.api.FrontierEvent event,
                                                                    java.util.function.Supplier<List<ScheduledAction>> pending) {
                        return retiredSchedules(previous, next, event, pending);
                    }
                }, FrontierWorldRuntimeDefinition::reduce, new FrontierWorldStateCodec(bootstrap), FrontierWorldProjectionCompiler::compile,
                new EngineLimits(4_096, 1_200L, 4_096, 4_096), FrontierWorldProcessCatalog.initialSchedule(bootstrap), TransactionCommitter.noOp(), FrontierWorldStateTransitionValidator.INSTANCE)
                .withKernelQuarantineReporter((state, world, causes, instant, boundary, failure) -> java.util.Optional.of(new ProposedEvent(
                        new SubjectId(world.value()), new io.farfrontier.palemirror.frontier.v3.model.KernelQuarantineObserved(new SubjectId(world.value()),
                        switch (boundary) { case COMMAND_TRANSACTION -> io.farfrontier.palemirror.frontier.v3.model.KernelQuarantineObserved.Producer.COMMAND_TRANSACTION;
                            case DUE_CAPACITY -> io.farfrontier.palemirror.frontier.v3.model.KernelQuarantineObserved.Producer.DUE_CAPACITY;
                            case DUE_TRANSACTION -> io.farfrontier.palemirror.frontier.v3.model.KernelQuarantineObserved.Producer.DUE_TRANSACTION; },
                        failure.getClass().getSimpleName())))); }
    public static PayloadCodecs payloadCodecs() { return PAYLOAD_CODECS; }
    public static boolean scheduledHeld(FrontierWorldState state, ScheduledAction action) {
        return FrontierWorldProcessCatalog.scheduledHeld(PROCESS_REGISTRY, state, action);
    }

    public static List<ScheduledAction> retiredSchedules(FrontierWorldState previous, FrontierWorldState next,
            io.farfrontier.palemirror.frontier.v3.api.FrontierEvent event,
            java.util.function.Supplier<List<ScheduledAction>> pending) {
        return FrontierWorldProcessCatalog.retiredSchedules(PROCESS_REGISTRY, previous, next, event, pending);
    }

    public static DeterministicProcessRegistry processRegistry() {
        FrontierWorldProcessCatalog.requirePhysicalLifecycleComposition();
        List<io.farfrontier.palemirror.frontier.v3.kernel.DeterministicProcessDescriptor> descriptors = FrontierWorldProcessCatalog.descriptors();
        DeterministicProcessRegistry registry = new DeterministicProcessRegistry(descriptors, PAYLOAD_CODECS,
                FrontierWorldProcessCodecs.typesByProcess());
        FrontierDurationProcessDriverRegistry.requireCurrentComposition(descriptors, FrontierWorldProcessCatalog.scheduledKinds(),
                Set.of(io.farfrontier.palemirror.frontier.v3.model.SceneCauseKind.values()), PAYLOAD_CODECS.types());
        return registry;
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
