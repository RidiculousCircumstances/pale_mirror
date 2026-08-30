package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
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
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.developmentUncontestedSupplyConfiguration(worldId, seed));
        FrontierWorldState state = null; RouteOperation operation = null;
        for (long tick = 1L; tick <= 12_000L; tick++) {
            engine.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));
            if (tick % 20L != 0L) continue;
            state = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
            RouteOperation candidate = state.operations().get(new SubjectId("operation:supply-1-2"));
            if (candidate != null && candidate.stage() == OperationStage.EN_ROUTE && candidate.activeTravel().isPresent()
                    && candidate.activeTravel().orElseThrow().cursor() == 0) {
                operation = candidate;
                break;
            }
        }
        if (state == null || operation == null) throw new IllegalStateException("development scene needs one en-route operation");
        BlockPosition intercept = operation.currentPosition();
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
        io.farfrontier.palemirror.frontier.v3.api.CheckpointImage checkpoint = null;
        FrontierWorldState state = null; RouteOperation operation = null;
        for (long tick = 1L; tick <= 12_000L; tick++) {
            engine.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));
            if (tick % 20L != 0L) continue;
            checkpoint = engine.checkpoint(); state = new FrontierWorldStateCodec().decode(checkpoint.canonicalState());
            RouteOperation candidate = state.operations().get(new SubjectId("operation:supply-1-2"));
            if (candidate != null && candidate.stage() == OperationStage.EN_ROUTE && candidate.routeIndex() == 0
                    && candidate.activeTravel().isPresent() && candidate.activeTravel().orElseThrow().cursor() == 0) {
                operation = candidate; break;
            }
        }
        BlockPosition start = new BlockPosition(-366, 64, -340);
        BlockPosition next = new BlockPosition(-366, 64, -304);
        if (checkpoint == null || state == null || operation == null || !operation.route().getFirst().equals(start) || !operation.route().get(1).equals(next)
                || !operation.participantIds().equals(List.of(new SubjectId("resident:1-30"), new SubjectId("resident:1-16")))) {
            throw new IllegalStateException("development route-return fixture did not retain its exact assembled Northwatch shipment");
        }
        RouteOperation activeOperation = operation;
        var schedules = checkpoint.schedules().stream()
                .filter(action -> !action.subject().equals(activeOperation.id()) || !action.kind().equals("frontier.operation.progress"))
                .toList();
        if (schedules.stream().anyMatch(action -> action.subject().equals(activeOperation.id()) && action.kind().equals("frontier.operation.progress"))) {
            throw new IllegalStateException("development route-return fixture retained a pre-HOT route progression");
        }
        return new RouteSceneReturnFixture(state, checkpoint.instant(), schedules);
    }

    /**
     * Stops at the ordinary cargo-loaded assembly boundary before its first COLD step.  The
     * disposable native pilot must load the port and advance these exact people through normal
     * HOT movement; it cannot use the fixture to start travel or move a resident.
     */
    static OperationAssemblyFixture operationAssemblyFixture(WorldId worldId, long seed) {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.developmentUncontestedSupplyConfiguration(worldId, seed));
        for (long tick = 1L; tick <= 12_000L; tick++) {
            engine.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));
            var checkpoint = engine.checkpoint();
            FrontierWorldState state = new FrontierWorldStateCodec().decode(checkpoint.canonicalState());
            RouteOperation operation = state.operations().get(new SubjectId("operation:supply-1-2"));
            if (operation == null || operation.stage() != OperationStage.ASSEMBLING || operation.activeAssembly().isEmpty()) continue;
            var schedules = checkpoint.schedules().stream().filter(action -> !action.subject().equals(operation.id())
                    || !action.kind().equals("frontier.operation.assembly")).toList();
            if (schedules.size() == checkpoint.schedules().size()) throw new IllegalStateException("assembly fixture has no pending COLD assembly action");
            return new OperationAssemblyFixture(state, checkpoint.instant(), schedules, operation.id());
        }
        throw new IllegalStateException("development assembly fixture did not reach its exact cargo-loaded boundary by 12000 ticks");
    }

    /**
     * Retains the real 12-settlement schedule through the first hive decision, stopping only
     * at the durable exact-biomass physical boundary.  The native pilot must still load the
     * east store and let its ordinary executor consume the real tagged stack.
     */
    static HiveGrowthFixture hiveGrowthFixture(WorldId worldId, long seed) {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.developmentUncontestedSupplyConfiguration(worldId, seed));
        for (long tick = 1L; tick <= 12_000L; tick++) {
            engine.advanceTo(new SimInstant(tick), new WorkBudget(32, 256));
            if (tick % 20L != 0L) continue;
            var checkpoint = engine.checkpoint();
            FrontierWorldState state = new FrontierWorldStateCodec().decode(checkpoint.canonicalState());
            HiveGrowthJob job = state.hiveColony().growthJobs().get(new SubjectId("job:hive-growth-1"));
            if (job != null && state.physicalIntents().get(job.consumptionIntentId()) != null) {
                return new HiveGrowthFixture(state, checkpoint.instant(), checkpoint.schedules());
            }
        }
        throw new IllegalStateException("development hive-growth fixture did not reach its exact biomass boundary by 12000 ticks");
    }

    /**
     * Read-only starting condition for one real settlement assessment.  The fixture does not
     * pre-write a disease result: the ordinary objective review must still emit the exact
     * exposure and quarantine transition after the server starts.
     */
    static HealthQuarantineFixture healthQuarantineFixture(WorldId worldId, long seed) {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(worldId, seed);
        FrontierWorldState state = FrontierWorldState.initial(bootstrap);
        Settlement settlement = bootstrap.settlements().getFirst();
        SettlementStructure infirmary = settlement.structures().stream().filter(value -> value.kind() == StructureKind.INFIRMARY)
                .findFirst().orElseThrow(() -> new IllegalStateException("health fixture needs one infirmary"));
        InfectionCell contact = FrontierGrayboxPlan.compile(state).cells().values().stream()
                .filter(cell -> cell.ownerId().equals(infirmary.id())).map(cell -> InfectionCell.at(cell.position()))
                .sorted(java.util.Comparator.comparingInt(InfectionCell::x).thenComparingInt(InfectionCell::z)).findFirst()
                .orElseThrow(() -> new IllegalStateException("health fixture infirmary lacks semantic contact"));
        state = state.withInfection(contact, new FixedRatio(new FixedScalar(FixedScalar.SCALE)));
        return new HealthQuarantineFixture(state, SimInstant.ZERO, List.of(StrategicObjectiveProcess.review(settlement.id(), 1, 1L)), settlement.id(), contact);
    }

    /**
     * Read-only starting condition for one real displaced resident.  The fixture creates the
     * ordinary bounded route and its exact bed reservation, but deliberately owns no HOT body:
     * a visiting player must cause the normal ambient executor to materialize and advance it.
     */
    static ResidentTransitFixture residentTransitFixture(WorldId worldId, long seed) {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(worldId, seed));
        Settlement source = state.bootstrap().settlements().getFirst();
        SubjectId housing = source.structures().stream().filter(value -> value.kind() == StructureKind.HOUSING).findFirst()
                .orElseThrow(() -> new IllegalStateException("transit fixture needs source housing")).id();
        state = state.withStructureCondition(housing, StructureCondition.DESTROYED);
        ResidentMigrationStarted started = PopulationMigrationProcess.planReview(state, PopulationMigrationProcess.review(1, 1L)).stream()
                .map(ProposedEvent::payload).filter(ResidentMigrationStarted.class::isInstance).map(ResidentMigrationStarted.class::cast)
                .findFirst().orElseThrow(() -> new IllegalStateException("transit fixture needs one displaced resident"));
        state = HumanPopulationStateSupport.startMigration(state, started.journey());
        return new ResidentTransitFixture(state, SimInstant.ZERO, List.of(), started.journey());
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

    record OperationAssemblyFixture(FrontierWorldState state, SimInstant instant, List<ScheduledAction> schedules, SubjectId operationId) {
        OperationAssemblyFixture {
            schedules = List.copyOf(schedules);
        }
    }

    record HealthQuarantineFixture(FrontierWorldState state, SimInstant instant, List<ScheduledAction> schedules,
                                   SubjectId settlementId, InfectionCell contact) {
        HealthQuarantineFixture {
            schedules = List.copyOf(schedules);
        }
    }

    record ResidentTransitFixture(FrontierWorldState state, SimInstant instant, List<ScheduledAction> schedules,
                                  ResidentMigrationJourney journey) {
        ResidentTransitFixture {
            schedules = List.copyOf(schedules);
        }
    }
}
