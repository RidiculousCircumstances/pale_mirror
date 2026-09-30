package io.farfrontier.palemirror.frontier.v3.runtime;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.kernel.DeterministicProcessRegistry;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrap;
import io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper;
import io.farfrontier.palemirror.frontier.v3.model.FrontierExecutionSubjects;
import io.farfrontier.palemirror.frontier.v3.model.FrontierRuleset;
import io.farfrontier.palemirror.frontier.v3.model.FrontierRulesets;
import io.farfrontier.palemirror.frontier.v3.process.FrontierWorldCommandPlanner;
import io.farfrontier.palemirror.frontier.v3.process.FrontierWorldEventReducer;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldPayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.process.FrontierWorldProcessCatalog;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldProjection;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import java.util.List;
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
        return FrontierWorldConfigurationFactory.create(bootstrap, autonomousInterception);
    }
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
        return FrontierWorldConfigurationFactory.processRegistry(PAYLOAD_CODECS);
    }
    public static CommandPlan planCommand(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.FrontierCommand command) {
        return FrontierWorldCommandPlanner.plan(state, command, PROCESS_REGISTRY, PHYSICAL_EXECUTOR);
    }
    public static List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planScheduled(FrontierWorldState state, ScheduledAction action) {
        return planScheduled(state, action, true);
    }
    public static List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planScheduled(FrontierWorldState state, ScheduledAction action,
                                                                                                      boolean autonomousInterception) {
        return planScheduled(state, action, autonomousInterception, action.dueAt());
    }
    public static List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planScheduled(FrontierWorldState state, ScheduledAction action,
                                                                                                      boolean autonomousInterception,
                                                                                                      io.farfrontier.palemirror.frontier.v3.api.SimInstant currentInstant) {
        return FrontierWorldProcessCatalog.planScheduled(PROCESS_REGISTRY, state, action, autonomousInterception, currentInstant);
    }
    public static FrontierWorldState reduce(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.FrontierEvent event) {
        return FrontierWorldEventReducer.reduce(state, event, PROCESS_REGISTRY);
    }
}
