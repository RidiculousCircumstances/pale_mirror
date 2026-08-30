package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;

import java.util.List;
import java.util.Optional;

/** Small deterministic v3 scenes for development verification; never selected by the production bootstrap. */
final class FrontierDevelopmentScenarios {
    private FrontierDevelopmentScenarios() { }

    static FrontierWorldState hotSceneStrikeState(WorldId worldId, long seed) {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(worldId, seed));
        for (long tick = 100L; tick <= 2_550L; tick += 50L) {
            engine.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));
        }
        FrontierWorldState state = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        RouteOperation operation = state.operations().values().stream().filter(value -> value.stage() == OperationStage.EN_ROUTE).findFirst()
                .orElseThrow(() -> new IllegalStateException("development scene needs one en-route operation"));
        BlockPosition intercept = operation.route().get(operation.routeIndex());
        for (Bioform bioform : state.bootstrap().hive().bioforms()) {
            if (bioform.role() == BioformRole.GUARD || bioform.role() == BioformRole.BOMBER) state = state.withActorLocation(bioform.id(), intercept);
        }
        SubjectId hive = state.bootstrap().hive().id();
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:development-hot-strike"), hive,
                StrategicObjectiveKind.HIVE_INTERCEPT_ROUTE_OPERATION, Optional.empty(), 99, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:development-hot-strike"), objective.id(), hive,
                StrategicTaskKind.INTERCEPT_ROUTE_OPERATION, Optional.empty(), Optional.of(operation.id()),
                List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD), List.of(), StrategicTaskStatus.PENDING);
        state = state.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task));
        ScheduledAction action = HiveRouteEngagementProcess.start(task, 2_600L);
        for (ProposedEvent event : HiveRouteEngagementProcess.planStart(state, action)) {
            if (event.payload() instanceof StrategicTaskTransition transition) state = StrategicObjectiveProcess.reduceTaskTransition(state, hive, transition);
            if (event.payload() instanceof RouteEngagementStarted started) state = HiveRouteEngagementProcess.reduceStarted(state, hive, started);
            if (event.payload() instanceof RouteEngagementTransition transition) state = HiveRouteEngagementProcess.reduceTransition(state, hive, transition);
        }
        if (state.coldEngagementSceneCandidates().isEmpty()) throw new IllegalStateException("development scene did not enter COLD engagement");
        return state;
    }

    /**
     * A read-only bootstrap at the first ordinary Northwatch shipment.  Unlike the strike
     * fixture, this begins with the real route at its first hand-off but deliberately removes
     * its already-due COLD progress action.  A native client needs time to connect before it
     * can create the HOT scene; after that scene releases, the production release path creates
     * the normal next COLD action.  This is a test-clock admission detail, not a production
     * route rule.
     */
    static RouteSceneReturnFixture routeSceneReturnFixture(WorldId worldId, long seed) {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.developmentUncontestedSupplyConfiguration(worldId, seed));
        for (long tick = 100L; tick <= 2_550L; tick += 50L) engine.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));
        var checkpoint = engine.checkpoint();
        FrontierWorldState state = new FrontierWorldStateCodec().decode(checkpoint.canonicalState());
        RouteOperation operation = state.operations().get(new SubjectId("operation:supply-1-2"));
        BlockPosition start = new BlockPosition(-360, 64, -340);
        BlockPosition next = new BlockPosition(-366, 64, -340);
        if (operation == null || operation.stage() != OperationStage.EN_ROUTE || operation.routeIndex() != 0
                || !operation.route().getFirst().equals(start) || !operation.route().get(1).equals(next)
                || !operation.participantIds().equals(List.of(new SubjectId("resident:1-30"), new SubjectId("resident:1-16")))) {
            throw new IllegalStateException("development route-return fixture did not retain its exact Northwatch shipment");
        }
        var schedules = checkpoint.schedules().stream()
                .filter(action -> !action.subject().equals(operation.id()) || !action.kind().equals("frontier.operation.progress"))
                .toList();
        if (schedules.stream().anyMatch(action -> action.subject().equals(operation.id()) && action.kind().equals("frontier.operation.progress"))) {
            throw new IllegalStateException("development route-return fixture retained a pre-HOT route progression");
        }
        return new RouteSceneReturnFixture(state, checkpoint.instant(), schedules);
    }

    /**
     * Retains the real 12-settlement schedule through the first hive decision, stopping only
     * at the durable exact-biomass physical boundary.  The native pilot must still load the
     * east store and let its ordinary executor consume the real tagged stack.
     */
    static HiveGrowthFixture hiveGrowthFixture(WorldId worldId, long seed) {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.developmentUncontestedSupplyConfiguration(worldId, seed));
        for (long tick = 100L; tick <= 3_600L; tick += 100L) engine.advanceTo(new SimInstant(tick), new WorkBudget(32, 256));
        var checkpoint = engine.checkpoint();
        FrontierWorldState state = new FrontierWorldStateCodec().decode(checkpoint.canonicalState());
        HiveGrowthJob job = state.hiveColony().growthJobs().get(new SubjectId("job:hive-growth-1"));
        if (job == null || state.physicalIntents().get(job.consumptionIntentId()) == null) {
            throw new IllegalStateException("development hive-growth fixture did not reach its exact biomass boundary");
        }
        return new HiveGrowthFixture(state, checkpoint.instant(), checkpoint.schedules());
    }

    record HiveGrowthFixture(FrontierWorldState state, SimInstant instant, List<ScheduledAction> schedules) {
        HiveGrowthFixture {
            schedules = List.copyOf(schedules);
        }
    }

    record RouteSceneReturnFixture(FrontierWorldState state, SimInstant instant, List<ScheduledAction> schedules) {
        RouteSceneReturnFixture {
            schedules = List.copyOf(schedules);
        }
    }
}
