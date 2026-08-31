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
    private FrontierWorldRuntimeDefinition() { }
    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration(WorldId worldId, long seed) { return configuration(worldId, seed, true); }
    /** Development-only uncontested logistics fixture; production always uses {@link #configuration(WorldId, long)}. */
    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> developmentUncontestedSupplyConfiguration(WorldId worldId, long seed) {
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = configuration(worldId, seed, false);
        FrontierWorldState initial = developmentSupplyReserve(base.initialState());
        return new FrontierEngineConfiguration<>(base.worldId(), initial, base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(), base.reducer(),
                base.stateCodec(), base.projectionMapper(), base.limits(), base.initialSchedules(), base.transactionCommitter(), base.stateValidator());
    }

    /** Fixture-only autonomous interception profile with the same exact food reserve as supply tests. */
    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> developmentAutonomousSupplyInterceptionConfiguration(WorldId worldId, long seed) {
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = configuration(worldId, seed, true);
        FrontierWorldState initial = developmentSupplyReserve(base.initialState());
        return new FrontierEngineConfiguration<>(base.worldId(), initial, base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(), base.reducer(),
                base.stateCodec(), base.projectionMapper(), base.limits(), base.initialSchedules(), base.transactionCommitter(), base.stateValidator());
    }

    /** Fixture-only exact reserve keeps logistics tests independent from the human food reserve policy. */
    private static FrontierWorldState developmentSupplyReserve(FrontierWorldState state) {
        Settlement settlement = state.bootstrap().settlements().getFirst(); SubjectId depot = FrontierWorldState.depotId(settlement.id());
        int remaining = SettlementProvisionProcess.reserveRequirement(state, settlement.id()); ExactInventory inventory = state.inventory(); int ordinal = 0;
        while (remaining > 0) {
            // Keep every fixture-reserve stack below one export shipment: the production output is
            // then the only 64-item candidate, without granting the fixture fictitious food.
            int count = Math.min(63, remaining);
            inventory = inventory.store(new ExactItemStack(new SubjectId("item:development-supply-reserve-" + ordinal), settlement.id(), "minecraft:bread", count,
                    new InventoryCustody.ContainerSlot(depot, ordinal + 1)));
            remaining -= count; ordinal++;
        }
        return state.withInventory(inventory);
    }
    private static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration(WorldId worldId, long seed, boolean autonomousInterception) {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(worldId, seed); FrontierWorldState initial = FrontierWorldState.initial(bootstrap);
        return new FrontierEngineConfiguration<>(worldId, initial, SimInstant.ZERO, FrontierWorldRuntimeDefinition::planCommand,
                (state, action) -> planScheduled(state, action, autonomousInterception), FrontierWorldRuntimeDefinition::reduce, new FrontierWorldStateCodec(bootstrap), FrontierWorldProjectionCompiler::compile,
                new EngineLimits(4_096, 1_200L, 4_096), initialSchedule(bootstrap), TransactionCommitter.noOp(), FrontierWorldStateTransitionValidator.INSTANCE); }
    /** Development-only deterministic scene fixture; production always uses {@link #configuration(WorldId, long)}. */
    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> developmentHotSceneStrikeConfiguration(WorldId worldId, long seed) {
        FrontierWorldState initial = FrontierDevelopmentScenarios.hotSceneStrikeState(worldId, seed);
        return new FrontierEngineConfiguration<>(worldId, initial, new SimInstant(2_600L), FrontierWorldRuntimeDefinition::planCommand,
                (state, action) -> planScheduled(state, action, true), FrontierWorldRuntimeDefinition::reduce, new FrontierWorldStateCodec(initial.bootstrap()), FrontierWorldProjectionCompiler::compile,
                new EngineLimits(4_096, 1_200L, 4_096), List.of(), TransactionCommitter.noOp(), FrontierWorldStateTransitionValidator.INSTANCE); }
    /** Disposable-only settled assault fixture; ordinary loaded demand must create its typed cargo-free HOT battle. */
    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> developmentSettlementAssaultConfiguration(WorldId worldId, long seed) {
        FrontierDevelopmentScenarios.SettlementAssaultFixture fixture = FrontierDevelopmentScenarios.settlementAssaultFixture(worldId, seed);
        return new FrontierEngineConfiguration<>(worldId, fixture.state(), fixture.instant(), FrontierWorldRuntimeDefinition::planCommand,
                (state, action) -> planScheduled(state, action, true), FrontierWorldRuntimeDefinition::reduce, new FrontierWorldStateCodec(fixture.state().bootstrap()), FrontierWorldProjectionCompiler::compile,
                new EngineLimits(4_096, 1_200L, 4_096), fixture.schedules(), TransactionCommitter.noOp(), FrontierWorldStateTransitionValidator.INSTANCE); }
    /** Development-only real-economy fixture; the named runner must physically consume biomass before outputs exist. */
    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> developmentHiveGrowthConfiguration(WorldId worldId, long seed) {
        FrontierDevelopmentScenarios.HiveGrowthFixture fixture = FrontierDevelopmentScenarios.hiveGrowthFixture(worldId, seed);
        return new FrontierEngineConfiguration<>(worldId, fixture.state(), fixture.instant(), FrontierWorldRuntimeDefinition::planCommand,
                (state, action) -> planScheduled(state, action, false), FrontierWorldRuntimeDefinition::reduce, new FrontierWorldStateCodec(fixture.state().bootstrap()), FrontierWorldProjectionCompiler::compile,
                new EngineLimits(4_096, 1_200L, 4_096), fixture.schedules(), TransactionCommitter.noOp(), FrontierWorldStateTransitionValidator.INSTANCE); }
    /** Disposable-only physical inter-nest nutrient fixture; normal production never selects it. */
    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> developmentHiveNutrientTransferConfiguration(WorldId worldId, long seed) {
        FrontierDevelopmentScenarios.HiveNutrientTransferFixture fixture = FrontierDevelopmentScenarios.hiveNutrientTransferFixture(worldId, seed);
        return new FrontierEngineConfiguration<>(worldId, fixture.state(), fixture.instant(), FrontierWorldRuntimeDefinition::planCommand,
                (state, action) -> planScheduled(state, action, false), FrontierWorldRuntimeDefinition::reduce, new FrontierWorldStateCodec(fixture.state().bootstrap()), FrontierWorldProjectionCompiler::compile,
                new EngineLimits(4_096, 1_200L, 4_096), fixture.schedules(), TransactionCommitter.noOp(), FrontierWorldStateTransitionValidator.INSTANCE); }
    /** Development-only exact ration fixture; an ordinary loaded depot must consume the named bread before residents become secure. */
    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> developmentSettlementProvisionConfiguration(WorldId worldId, long seed) {
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = configuration(worldId, seed, false);
        FrontierWorldState state = base.initialState(); Settlement settlement = state.bootstrap().settlements().getFirst(); SubjectId depot = FrontierWorldState.depotId(settlement.id());
        SubjectId bread = new SubjectId("item:provision-fixture-bread"); int rations = settlement.residents().size();
        ExactItemStack stack = new ExactItemStack(bread, settlement.id(), SettlementProvisionProcess.BREAD, 64, new InventoryCustody.ContainerSlot(depot, 1));
        List<SubjectId> recipients = state.humanPopulation().residents().values().stream().filter(resident -> resident.settlementId().equals(settlement.id()))
                .map(ResidentProfile::id).sorted().toList();
        HumanPopulation population = state.humanPopulation();
        for (SubjectId recipient : recipients) population = population.resolveNutrition(recipient, 1, false);
        SettlementProvision provision = SettlementProvision.started(settlement.id(), 2, 0L, rations, recipients,
                List.of(new SettlementRationAllocation(bread, recipients)));
        PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:settlement-provision-1-2-0"), PhysicalIntentKind.EXACT_ITEM_CONSUMPTION,
                PhysicalIntentStatus.PREPARED, settlement.id(), List.of(settlement.id(), bread), new FixedPosition(FixedScalar.whole(settlement.anchor().x()),
                FixedScalar.whole(settlement.anchor().y()), FixedScalar.whole(settlement.anchor().z())), 0, PhysicalPostcondition.EXACT_ITEM_CONSUMED_OBSERVED);
        state = state.withInventory(state.inventory().store(stack)).withHumanPopulation(population.withProvision(provision.beginPhysical(intent.id())))
                .preparePhysicalIntent(intent);
        return new FrontierEngineConfiguration<>(base.worldId(), state, SimInstant.ZERO, base.commandPlanner(), base.scheduledPlanner(), base.reducer(),
                new FrontierWorldStateCodec(state.bootstrap()), base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter(), base.stateValidator());
    }
    /** Development-only HOT/COLD continuity fixture; production always begins at the normal world bootstrap. */
    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> developmentRouteSceneReturnConfiguration(WorldId worldId, long seed) {
        FrontierDevelopmentScenarios.RouteSceneReturnFixture fixture = FrontierDevelopmentScenarios.routeSceneReturnFixture(worldId, seed);
        return new FrontierEngineConfiguration<>(worldId, fixture.state(), fixture.instant(), FrontierWorldRuntimeDefinition::planCommand,
                (state, action) -> planScheduled(state, action, false), FrontierWorldRuntimeDefinition::reduce, new FrontierWorldStateCodec(fixture.state().bootstrap()), FrontierWorldProjectionCompiler::compile,
                new EngineLimits(4_096, 1_200L, 4_096), fixture.schedules(), TransactionCommitter.noOp(), FrontierWorldStateTransitionValidator.INSTANCE); }
    /** Disposable-only physical Scout-perception fixture; production never relocates a Scout for a carrier. */
    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> developmentHotScoutSightingConfiguration(WorldId worldId, long seed) {
        FrontierDevelopmentScenarios.RouteSceneReturnFixture fixture = FrontierDevelopmentScenarios.hotScoutSightingFixture(worldId, seed);
        return new FrontierEngineConfiguration<>(worldId, fixture.state(), fixture.instant(), FrontierWorldRuntimeDefinition::planCommand,
                (state, action) -> frozenScoutSightingProgress(state, action), FrontierWorldRuntimeDefinition::reduce, new FrontierWorldStateCodec(fixture.state().bootstrap()), FrontierWorldProjectionCompiler::compile,
                new EngineLimits(4_096, 1_200L, 4_096), fixture.schedules(), TransactionCommitter.noOp(), FrontierWorldStateTransitionValidator.INSTANCE); }
    /** Disposable-only end-to-end Scout perception/intercept fixture; the planner remains the sole engagement authority. */
    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> developmentHotScoutInterceptConfiguration(WorldId worldId, long seed) {
        FrontierDevelopmentScenarios.RouteSceneReturnFixture fixture = FrontierDevelopmentScenarios.hotScoutInterceptFixture(worldId, seed);
        return new FrontierEngineConfiguration<>(worldId, fixture.state(), fixture.instant(), FrontierWorldRuntimeDefinition::planCommand,
                (state, action) -> frozenScoutSightingProgress(state, action), FrontierWorldRuntimeDefinition::reduce, new FrontierWorldStateCodec(fixture.state().bootstrap()), FrontierWorldProjectionCompiler::compile,
                new EngineLimits(4_096, 1_200L, 4_096), fixture.schedules(), TransactionCommitter.noOp(), FrontierWorldStateTransitionValidator.INSTANCE); }

    /**
     * The disposable recovery proof observes one physical carrier fact, not a whole caravan
     * lifecycle.  Once its HOT scene drains, consume only that operation's newly scheduled COLD
     * progress action so elapsed native-client restart time cannot replace the asserted fact.
     */
    private static List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> frozenScoutSightingProgress(FrontierWorldState state, ScheduledAction action) {
        if (action.subject().equals(new SubjectId("operation:supply-1-2")) && action.kind().equals("frontier.operation.progress")) {
            return List.of(new io.farfrontier.palemirror.frontier.v3.api.ProposedEvent(action.subject(), new ScheduleEffect.Cancelled(action.id())));
        }
        return planScheduled(state, action, false);
    }
    /** Development-only exact HOT assembly fixture; the pilot supplies every movement observation. */
    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> developmentOperationAssemblyConfiguration(WorldId worldId, long seed) {
        FrontierDevelopmentScenarios.OperationAssemblyFixture fixture = FrontierDevelopmentScenarios.operationAssemblyFixture(worldId, seed);
        return new FrontierEngineConfiguration<>(worldId, fixture.state(), fixture.instant(), FrontierWorldRuntimeDefinition::planCommand,
                (state, action) -> planScheduled(state, action, false), FrontierWorldRuntimeDefinition::reduce, new FrontierWorldStateCodec(fixture.state().bootstrap()), FrontierWorldProjectionCompiler::compile,
                new EngineLimits(4_096, 1_200L, 4_096), fixture.schedules(), TransactionCommitter.noOp(), FrontierWorldStateTransitionValidator.INSTANCE); }
    /** Development-only exposure fixture; the first ordinary settlement review produces the health result. */
    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> developmentHealthQuarantineConfiguration(WorldId worldId, long seed) {
        FrontierDevelopmentScenarios.HealthQuarantineFixture fixture = FrontierDevelopmentScenarios.healthQuarantineFixture(worldId, seed);
        return new FrontierEngineConfiguration<>(worldId, fixture.state(), fixture.instant(), FrontierWorldRuntimeDefinition::planCommand,
                (state, action) -> planScheduled(state, action, true), FrontierWorldRuntimeDefinition::reduce, new FrontierWorldStateCodec(fixture.state().bootstrap()), FrontierWorldProjectionCompiler::compile,
                new EngineLimits(4_096, 1_200L, 4_096), fixture.schedules(), TransactionCommitter.noOp(), FrontierWorldStateTransitionValidator.INSTANCE); }
    /** Development-only exact-person Transit fixture; it owns no materialized body or shortcut. */
    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> developmentResidentTransitConfiguration(WorldId worldId, long seed) {
        FrontierDevelopmentScenarios.ResidentTransitFixture fixture = FrontierDevelopmentScenarios.residentTransitFixture(worldId, seed);
        return new FrontierEngineConfiguration<>(worldId, fixture.state(), fixture.instant(), FrontierWorldRuntimeDefinition::planCommand,
                (state, action) -> planScheduled(state, action, true), FrontierWorldRuntimeDefinition::reduce, new FrontierWorldStateCodec(fixture.state().bootstrap()), FrontierWorldProjectionCompiler::compile,
                new EngineLimits(4_096, 1_200L, 4_096), fixture.schedules(), TransactionCommitter.noOp(), FrontierWorldStateTransitionValidator.INSTANCE); }
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
    /** Disposable-only exact player-withdrawal fixture; production never selects this bootstrap. */
    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> developmentMaterializedProductionInputTheftConfiguration(WorldId worldId, long seed) {
        FrontierDevelopmentScenarios.MaterializedProductionFixture fixture = FrontierDevelopmentScenarios.materializedProductionInputTheftFixture(worldId, seed);
        return new FrontierEngineConfiguration<>(worldId, fixture.state(), fixture.instant(), FrontierWorldRuntimeDefinition::planCommand,
                (state, action) -> planScheduled(state, action, false), FrontierWorldRuntimeDefinition::reduce, new FrontierWorldStateCodec(fixture.state().bootstrap()),
                FrontierWorldProjectionCompiler::compile, new EngineLimits(4_096, 1_200L, 4_096), fixture.schedules(), TransactionCommitter.noOp(), FrontierWorldStateTransitionValidator.INSTANCE);
    }
    /** Disposable-only ordinary-combat fixture; the one nearby Villager is the exact reserved worker. */
    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> developmentMaterializedProductionWorkerDeathConfiguration(WorldId worldId, long seed) {
        FrontierDevelopmentScenarios.MaterializedProductionFixture fixture = FrontierDevelopmentScenarios.materializedProductionWorkerDeathFixture(worldId, seed);
        return new FrontierEngineConfiguration<>(worldId, fixture.state(), fixture.instant(), FrontierWorldRuntimeDefinition::planCommand,
                (state, action) -> planScheduled(state, action, false), FrontierWorldRuntimeDefinition::reduce, new FrontierWorldStateCodec(fixture.state().bootstrap()),
                FrontierWorldProjectionCompiler::compile, new EngineLimits(4_096, 1_200L, 4_096), fixture.schedules(), TransactionCommitter.noOp(), FrontierWorldStateTransitionValidator.INSTANCE);
    }
    public static PayloadCodecs payloadCodecs() { return FrontierWorldPayloadCodecs.create(); } static CommandPlan planCommand(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.FrontierCommand command) {
        if (!PHYSICAL_EXECUTOR.equals(command.actor())) {
            return new CommandPlan.Rejected(new io.farfrontier.palemirror.frontier.v3.api.CommandRejection(
                    io.farfrontier.palemirror.frontier.v3.api.RejectionCode.REJECTED_BY_POLICY, "command is not from the trusted physical executor"));
        }
        if (command.payload() instanceof ResidentBorn birth) {
            return rejected("resident birth is emitted only by a confirmed population permit");
        }
        if (command.payload() instanceof ResidentMigrated migration) {
            try { state.recordResidentMigration(migration); } catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(migration.destinationSettlementId(), migration)));
        }
        if (command.payload() instanceof ResidentTransitAdvanced advanced) {
            ResidentMigrationJourney journey = state.humanPopulation().migration(advanced.residentId());
            if (journey == null) return rejected("HOT transit observation has no active migration journey");
            try { PopulationMigrationProcess.reduceHotAdvance(state, advanced); } catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(journey.originSettlementId(), advanced)));
        }
        if (command.payload() instanceof ScoutPatrolAdvanced advanced) {
            try { HiveScoutPatrolProcess.reduce(state, state.bootstrap().hive().id(), advanced); }
            catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(state.bootstrap().hive().id(), advanced)));
        }
        if (command.payload() instanceof HotScoutOperationObserved observed) {
            try { HivePerceptionProcess.reduceHot(state, state.bootstrap().hive().id(), observed); }
            catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
            HiveOperationKnowledge.Sighting sighting = new HiveOperationKnowledge.Sighting(observed.operationId(), observed.scoutId(),
                    observed.seenCarrierPosition(), observed.observedAt());
            return new CommandPlan.Accepted(List.of(new ProposedEvent(state.bootstrap().hive().id(), observed),
                    new ProposedEvent(state.bootstrap().hive().id(), new ScheduleEffect.Created(
                            StrategicObjectiveProcess.interceptOpportunity(state.bootstrap().hive().id(), sighting,
                                    Math.addExact(command.submittedAt().ticks(), 1L))))));
        }
        if (command.payload() instanceof OperationAssemblyAdvanced advanced) {
            RouteOperation operation = state.operations().get(advanced.operationId());
            if (operation == null) return rejected("operation assembly observation has no active operation");
            try {
                validateHotAssemblyObservation(state, operation, advanced.assembly());
                state.advanceOperationAssembly(advanced.operationId(), advanced.assembly());
            } catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
            List<ProposedEvent> events = new java.util.ArrayList<>(List.of(new ProposedEvent(operation.settlementId(), advanced)));
            if (advanced.assembly().complete()) {
                events.add(new ProposedEvent(operation.settlementId(), new OperationTravelStarted(operation.id(),
                        SupplyOperationProcess.travelForCompletedAssembly(operation, advanced.assembly()))));
                events.add(new ProposedEvent(operation.id(), new ScheduleEffect.Created(
                        SupplyOperationProcess.operationProgress(operation, command.submittedAt().ticks() + 20L))));
            }
            return new CommandPlan.Accepted(List.copyOf(events));
        }
        if (command.payload() instanceof OperationAssemblyDeferred deferred) {
            RouteOperation operation = state.operations().get(deferred.operationId());
            if (operation == null) return rejected("operation assembly deferral has no active operation");
            try {
                validateHotAssemblyDeferral(state, operation, deferred.deferral());
                state.deferOperationAssembly(deferred.operationId(), deferred.deferral());
            } catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), deferred)));
        }
        if (command.payload() instanceof OperationTravelSegmentCompleted completed) {
            RouteOperation operation = state.operations().get(completed.operationId());
            if (operation == null) return rejected("operation travel completion has no active operation");
            try { state.completeOperationTravelSegment(completed.operationId()); } catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), completed)));
        }
        if (command.payload() instanceof OperationTravelAdvanced advanced) {
            RouteOperation operation = state.operations().get(advanced.operationId());
            if (operation == null) return rejected("operation travel observation has no active operation");
            try { state.advanceOperationTravel(advanced.operationId(), advanced.travel()); } catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), advanced)));
        }
        if (command.payload() instanceof OperationTravelStarted started) {
            RouteOperation operation = state.operations().get(started.operationId());
            if (operation == null) return rejected("operation travel start has no active operation");
            try { state.startOperationTravel(started.operationId(), started.travel()); } catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), started)));
        }
        if (command.payload() instanceof PhysicalIntentTransition || command.payload() instanceof PhysicalIntentPrepared) {
            return FrontierPhysicalIntentCommandProcess.plan(state, command);
        }
        if (command.payload() instanceof SceneLeasePrepared prepared) {
            RouteOperation operation = state.operations().get(FrontierSceneBehaviors.logistics(prepared.lease()).operationId()); if (operation == null) return rejected("scene lease has no owning operation");
            return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), prepared)));
        }
        if (command.payload() instanceof SettlementAssaultSceneLeasePrepared prepared) {
            try { return new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierSettlementAssaultSceneSupport.owner(state, prepared.lease()), prepared))); }
            catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof SceneLeaseHandoff handoff) {
            RouteOperation operation = state.operations().get(FrontierSceneBehaviors.logistics(handoff.lease()).operationId()); if (operation == null) return rejected("scene hand-off has no owning operation");
            return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), handoff)));
        }
        if (command.payload() instanceof SettlementAssaultSceneLeaseHandoff handoff) {
            try { return new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierSettlementAssaultSceneSupport.owner(state, handoff.lease()), handoff))); }
            catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof SceneLeaseTransition transition) {
            SceneLease lease = state.sceneLeases().get(transition.leaseId()); if (lease == null) return rejected("scene lease is unknown");
            try { return new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierSceneOwnerSupport.owner(state, lease), transition))); }
            catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof SceneLeaseReleased released) {
            SceneLease lease = state.sceneLeases().get(released.leaseId()); if (lease == null) return rejected("scene lease is unknown");
            if (FrontierSceneBehaviors.isSettlementAssault(lease)) {
                try {
                    SettlementAssault assault = FrontierSettlementAssaultSceneSupport.require(state, FrontierSceneBehaviors.settlementAssault(lease));
                    return new CommandPlan.Accepted(List.of(new ProposedEvent(assault.hiveId(), released), new ProposedEvent(assault.hiveId(),
                            new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created(HiveSettlementAssaultProcess.combat(assault, command.submittedAt().ticks() + 20L)))));
                } catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
            }
            LogisticsSceneCause logistics = FrontierSceneBehaviors.logistics(lease);
            RouteOperation operation = state.operations().get(logistics.operationId());
            if (operation == null) return rejected("scene lease has no owning operation");
            if (logistics.engagementId().isPresent()) {
                RouteEngagement engagement = state.strategicPlans().routeEngagements().get(logistics.engagementId().orElseThrow());
                if (engagement == null) return rejected("scene lease has no canonical engagement");
                if (engagement.status() == RouteEngagementStatus.HOT) {
                    return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), released),
                            new ProposedEvent(operation.settlementId(), new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created(
                                    HiveRouteEngagementProcess.combat(engagement, command.submittedAt().ticks() + 20L)))));
                }
                // A real blast/player interaction may legitimately destroy the cargo while the
                // scene is HOT. Cargo release atomically interrupts the operation and aborts its
                // engagement; remaining bodies must drain and close, never restart COLD combat.
                if (operation.stage() == OperationStage.INTERRUPTED && engagement.status() == RouteEngagementStatus.RESOLVED
                        && engagement.outcome().filter(outcome -> outcome == RouteEngagementOutcome.ABORTED).isPresent()) {
                    return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), released)));
                }
                return rejected("scene lease cannot resume its interrupted engagement");
            }
            if (operation.participantIds().stream().anyMatch(actor -> state.actorLocations().get(actor).condition().status() == ActorLifeStatus.DEAD)) {
                List<ProposedEvent> events = new java.util.ArrayList<>(); events.add(new ProposedEvent(operation.settlementId(), released));
                events.addAll(SupplyOperationProcess.failed(state, operation, "actor-death")); return new CommandPlan.Accepted(List.copyOf(events));
            }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), released),
                    new ProposedEvent(operation.settlementId(), new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created(
                            SupplyOperationProcess.operationProgress(operation, command.submittedAt().ticks() + 100L)))));
        }
        if (command.payload() instanceof SceneLeaseRecoveryUnresolved unresolved) {
            SceneLease lease = state.sceneLeases().get(unresolved.leaseId());
            if (lease == null || lease.status() != SceneLeaseStatus.UNKNOWN_AFTER_RESTART || lease.recoveryEvidence().isPresent()) {
                return rejected("scene recovery evidence does not bind one unresolved restart lease");
            }
            if (FrontierSceneBehaviors.isSettlementAssault(lease)) {
                try { return new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierSceneOwnerSupport.owner(state, lease), unresolved))); }
                catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
            }
            LogisticsSceneCause logistics = FrontierSceneBehaviors.logistics(lease);
            if (logistics.engagementId().isPresent()) return rejected("engagement scene recovery needs its own outcome policy");
            RouteOperation operation = state.operations().get(logistics.operationId());
            if (operation == null || operation.stage() != OperationStage.EN_ROUTE) return rejected("scene recovery evidence has no active route operation");
            try {
                List<ProposedEvent> events = new java.util.ArrayList<>();
                events.add(new ProposedEvent(operation.settlementId(), unresolved));
                events.addAll(SupplyOperationProcess.failed(state, operation, "scene-recovery-unresolved"));
                return new CommandPlan.Accepted(List.copyOf(events));
            } catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ActorDied death) {
            SceneLease lease = state.sceneLeases().get(death.leaseId());
            if (lease == null || (lease.status() != SceneLeaseStatus.HOT && lease.status() != SceneLeaseStatus.DRAINING)
                    || lease.members().stream().noneMatch(member -> member.actorId().equals(death.actorId()))) {
                return rejected("actor death is not evidence for an active scene member");
            }
            SubjectId owner;
            try { owner = FrontierSceneOwnerSupport.owner(state, lease); } catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
            List<ProposedEvent> events = new java.util.ArrayList<>();
            events.add(new ProposedEvent(owner, death));
            CompanyFoundationProcess.terminationForDeath(state, death.actorId()).ifPresent(events::add);
            events.addAll(ProductionProcess.failPreEffectWorkForDeath(state, death.actorId()));
            if (lease.status() == SceneLeaseStatus.HOT) {
                events.add(new ProposedEvent(owner, new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING)));
            }
            return new CommandPlan.Accepted(List.copyOf(events));
        }
        if (command.payload() instanceof AmbientActorDied death) return AmbientActorProcess.plan(state, death);
        if (command.payload() instanceof AmbientActorObserved observation) return AmbientActorProcess.plan(state, observation);
        if (command.payload() instanceof AmbientLeasePrepared || command.payload() instanceof AmbientLeaseTransition || command.payload() instanceof AmbientLeaseReleased) return AmbientActorProcess.planLease(state, command.payload());
        if (command.payload() instanceof ResourceSiteConflictObserved conflict) try { return new CommandPlan.Accepted(ResourceSiteProcess.planConflict(state, conflict)); }
        catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
        if (command.payload() instanceof StructureDamaged damage) {
            try {
                state.recordStructureDamage(damage);
            } catch (IllegalArgumentException invalid) {
                return rejected(invalid.getMessage());
            }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(FrontierWorldStateSupport.structureSettlement(state.bootstrap(), damage.structureId()), damage)));
        }
        if (command.payload() instanceof PhysicalDeltaObserved observed) return FrontierWorldPhysicalObservationProcess.plan(state, observed, command.submittedAt().ticks());
        if (command.payload() instanceof ResourceDeposited deposited) return FrontierWorldPhysicalObservationProcess.planResourceDeposit(state, deposited);
        if (command.payload() instanceof ExactItemCustodyChanged changed) {
            ExactItemStack item = state.inventory().items().get(changed.itemId());
            if (item == null || !item.custody().equals(changed.from())) return rejected("observed item source differs from canonical custody");
            ProposedEvent observation = new ProposedEvent(FrontierWorldStateSupport.itemOwner(state, changed), changed);
            try { return new CommandPlan.Accepted(ProductionProcess.planMaterializedInputDeparture(state, changed.itemId(), observation)); }
            catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof ExactItemDestroyed destroyed) {
            ExactItemStack item = state.inventory().items().get(destroyed.itemId());
            if (item == null || !item.custody().equals(destroyed.source())) return rejected("destroyed item source differs from canonical custody");
            ProposedEvent observation = new ProposedEvent(FrontierWorldStateSupport.itemOwner(state, destroyed), destroyed);
            try { return new CommandPlan.Accepted(ProductionProcess.planMaterializedInputDeparture(state, destroyed.itemId(), observation)); }
            catch (IllegalArgumentException invalid) { return rejected(invalid.getMessage()); }
        }
        if (command.payload() instanceof CargoCarrierReleased released) {
            SceneLease lease = state.sceneLeases().get(released.leaseId());
            if (lease == null || !FrontierSceneBehaviors.isLogistics(lease) || !FrontierSceneBehaviors.logistics(lease).cargoId().equals(released.cargoId()) || lease.status() != SceneLeaseStatus.HOT) {
                return rejected("cargo carrier release lacks one HOT matching scene lease");
            }
            if (!CargoCarrierIdentity.id(lease).equals(released.carrierId())) return rejected("cargo carrier identity is not canonical for its scene");
            RouteOperation operation = state.operations().get(FrontierSceneBehaviors.logistics(lease).operationId());
            if (operation == null) return rejected("cargo carrier release has no owning operation");
            return new CommandPlan.Accepted(List.of(new ProposedEvent(operation.settlementId(), released)));
        }
        if (command.payload() instanceof InventoryConflictObserved observed) {
            InventoryConflict conflict = observed.conflict();
            ContainerRecord container = state.inventory().containers().get(conflict.containerId());
            if (container == null || conflict.slot() >= container.slotCount()
                    || (!state.inventory().items().containsKey(conflict.subjectId()) && !state.inventory().containers().containsKey(conflict.subjectId()))) {
                return rejected("inventory conflict references an unknown exact surface");
            }
            return new CommandPlan.Accepted(List.of(new ProposedEvent(container.ownerId(), observed)));
        }
        if (command.payload() instanceof ContainerSurfaceTransition transition) {
            return ContainerSurfaceProcess.plan(state, transition);
        }
        return rejected("command is not a trusted physical transition or scene lease");
    }
    private static CommandPlan.Rejected rejected(String message) { return new CommandPlan.Rejected(new io.farfrontier.palemirror.frontier.v3.api.CommandRejection(
                io.farfrontier.palemirror.frontier.v3.api.RejectionCode.REJECTED_BY_POLICY, message));
    }
    static List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planScheduled(FrontierWorldState state, ScheduledAction action) {
        return planScheduled(state, action, true);
    }
    private static List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planScheduled(FrontierWorldState state, ScheduledAction action,
                                                                                               boolean autonomousInterception) {
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planned = switch (action.kind()) {
            case "frontier.hive.infection.task" -> HiveInfectionProcess.plan(state, action);
            case "frontier.settlement.production.task.start" -> ProductionProcess.planStart(state, action);
            case "frontier.settlement.production.task.complete" -> ProductionProcess.planCompletion(state, action);
            case "frontier.supply.task.start" -> SupplyOperationProcess.planStart(state, action);
            case "frontier.supply.cargo.load" -> SupplyOperationProcess.planCargoLoad(state, action, autonomousInterception);
            case "frontier.operation.assembly" -> SupplyOperationProcess.planAssembly(state, action);
            case "frontier.operation.progress" -> SupplyOperationProcess.planProgress(state, action);
            case "frontier.terminal_logistics.retention" -> TerminalLogisticsProcess.plan(state, action);
            case "frontier.hive.growth.task.start" -> HiveGrowthProcess.planStart(state, action);
            case "frontier.hive.growth.task.complete" -> HiveGrowthProcess.planCompletion(state, action);
            case "frontier.hive.nutrient.transfer.progress" -> HiveNutrientTransferProcess.plan(state, action);
            case "frontier.population.birth.review" -> PopulationBirthProcess.planReview(state, action);
            case "frontier.population.birth.complete" -> PopulationBirthProcess.planCompletion(state, action);
            case "frontier.population.migration.review" -> PopulationMigrationProcess.planReview(state, action);
            case "frontier.population.migration.progress" -> PopulationMigrationProcess.planProgress(state, action);
            case "frontier.settlement.provision.review" -> SettlementProvisionProcess.planReview(state, action);
            case "frontier.settlement.provision.progress" -> SettlementProvisionProcess.planProgress(state, action);
            case "frontier.company.foundation.review" -> CompanyFoundationProcess.plan(state, action);
            case "frontier.market.clear" -> MarketClearingProcess.plan(state, action);
            case "frontier.resource_site.growth" -> ResourceSiteProcess.planGrowth(state, action);
            case "frontier.resource_site.prepare" -> ResourceSiteProcess.planPreparation(state, action);
            case "frontier.resource_site.harvest" -> ResourceSiteHarvestProcess.plan(state, action);
            case "frontier.objective.resource_harvest" -> StrategicObjectiveProcess.planResourceHarvestOpportunity(state, action);
            case "frontier.structural_repair.scan" -> StructuralRepairProcess.plan(state, action);
            case "frontier.route_construction.scan" -> RouteConstructionProcess.plan(state, action);
            case "frontier.route_construction.start" -> RouteConstructionProcess.planStart(state, action);
            case "frontier.route_patrol.start" -> RoutePatrolProcess.planStart(state, action);
            case "frontier.route_patrol.progress" -> RoutePatrolProcess.planProgress(state, action);
            case "frontier.hive_route_engagement.start" -> HiveRouteEngagementProcess.planStart(state, action);
            case "frontier.hive_route_engagement.progress" -> HiveRouteEngagementProcess.planProgress(state, action);
            case "frontier.hive_route_engagement.readiness" -> HiveRouteEngagementProcess.planReadiness(state, action);
            case "frontier.hive_route_engagement.combat" -> HiveRouteEngagementProcess.planCombat(state, action);
            case "frontier.hive.scout.patrol" -> HiveScoutPatrolProcess.plan(state, action);
            case "frontier.decontamination.scan" -> DecontaminationProcess.plan(state, action);
            case "frontier.objective.review" -> StrategicObjectiveProcess.plan(state, action, autonomousInterception);
            case "frontier.objective.reconsider" -> StrategicObjectiveProcess.planReconsideration(state, action);
            case "frontier.objective.interrupt" -> StrategicObjectiveProcess.planOpportunity(state, action);
            case "frontier.objective.assault" -> StrategicObjectiveProcess.planAssaultOpportunity(state, action);
            case "frontier.settlement_assault.start" -> HiveSettlementAssaultProcess.planStart(state, action);
            case "frontier.settlement_assault.progress" -> HiveSettlementAssaultProcess.planProgress(state, action);
            case "frontier.settlement_assault.combat" -> HiveSettlementAssaultProcess.planCombat(state, action);
            default -> throw new IllegalStateException("unknown v3 scheduled action: " + action.kind());
        };
        // A known planner can deliberately find that a durable physical observation has already
        // invalidated its work. That no-op must still become a persisted schedule transition:
        // otherwise a later tick/restart would rediscover the same head and quarantine the world.
        return planned.isEmpty() ? List.of(new ProposedEvent(action.subject(), new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Cancelled(action.id()))) : planned;
    }
    /** A HOT observer may acknowledge exactly one visible next-cursor arrival, never a COLD batch. */
    private static void validateHotAssemblyObservation(FrontierWorldState state, RouteOperation operation, OperationAssembly next) {
        OperationAssembly current = operation.activeAssembly().orElseThrow(() -> new IllegalArgumentException("operation has no active assembly"));
        if (!current.members().keySet().equals(next.members().keySet())) throw new IllegalArgumentException("HOT assembly observation changes formation");
        SubjectId observed = null;
        for (SubjectId actor : current.members().keySet()) {
            OperationAssembly.Member before = current.members().get(actor), after = next.members().get(actor);
            if (!before.corridor().equals(after.corridor()) || after.cursor() < before.cursor() || after.cursor() > before.cursor() + 1) {
                throw new IllegalArgumentException("HOT assembly observation may advance only one adjacent cursor");
            }
            if (after.cursor() > before.cursor()) {
                if (observed != null) throw new IllegalArgumentException("HOT assembly observation may acknowledge only one actor");
                observed = actor;
            }
        }
        if (observed == null) throw new IllegalArgumentException("HOT assembly observation did not advance an actor");
        if (current.deferral().isPresent()) {
            OperationAssemblyDeferral blocked = current.deferral().orElseThrow();
            if (!blocked.actorId().equals(observed) || !next.members().get(observed).currentPosition().equals(blocked.target())) {
                throw new IllegalArgumentException("HOT assembly observation may not bypass a loaded-world assembly deferral");
            }
        }
        AmbientActorLease lease = state.ambientLeases().get(observed);
        OperationAssembly.Member arrived = next.members().get(observed);
        if (lease == null || lease.status() != AmbientLeaseStatus.HOT || lease.goal() != AmbientGoalKind.OPERATION_ASSEMBLY
                || !lease.goalPosition().equals(arrived.currentPosition())) {
            throw new IllegalArgumentException("HOT assembly observation lacks its exact active actor lease");
        }
    }
    private static void validateHotAssemblyDeferral(FrontierWorldState state, RouteOperation operation, OperationAssemblyDeferral deferral) {
        OperationAssembly assembly = operation.activeAssembly().orElseThrow(() -> new IllegalArgumentException("operation has no active assembly"));
        OperationAssembly.Member member = assembly.members().get(deferral.actorId());
        AmbientActorLease lease = state.ambientLeases().get(deferral.actorId());
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), operation.settlementId());
        SettlementStructure hall = settlement.structures().stream().filter(value -> value.kind() == StructureKind.HALL).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("assembly settlement has no Hall access port"));
        SettlementAccessPort access = SettlementAccessPort.forHall(hall);
        if (member == null || member.arrived() || !member.corridor().get(member.cursor() + 1).equals(deferral.target())
                || lease == null || lease.status() != AmbientLeaseStatus.HOT || lease.goal() != AmbientGoalKind.OPERATION_ASSEMBLY
                || !lease.goalPosition().equals(deferral.target())
                || (!deferral.obstructionFloor().equals(deferral.target()) && !deferral.obstructionFloor().equals(access.throatFloor()))) {
            throw new IllegalArgumentException("HOT assembly deferral lacks its exact active actor lease");
        }
    }
    private static FrontierWorldState reduce(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.FrontierEvent event) {
        return FrontierWorldState.duringReducerTransition(() -> reduceUnchecked(state, event));
    }
    private static FrontierWorldState reduceUnchecked(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.FrontierEvent event) {
        if (event.payload() instanceof AmbientLeasePrepared || event.payload() instanceof AmbientLeaseTransition || event.payload() instanceof AmbientLeaseReleased) {
            return AmbientActorProcess.reduceLease(state, event.subject(), event.instant(), event.payload());
        }
        return switch (event.payload()) {
            case InfectionChanged changed -> state.withInfection(changed.cell(), changed.intensity());
            case CompanyRegistered registered -> CompanyFoundationProcess.reduce(state, event.subject(), registered);
            case EmploymentContractOpened opened -> CompanyFoundationProcess.reduceEmployment(state, event.subject(), opened);
            case EmploymentContractTerminated terminated -> CompanyFoundationProcess.reduceEmploymentTermination(state, event.subject(), terminated);
            case MarketDemandOpened opened -> MarketClearingProcess.reduceOpened(state, event.subject(), opened);
            case MarketQuotePublished published -> MarketClearingProcess.reduceQuote(state, event.subject(), event.instant().ticks(), published);
            case MarketWorkOrderAccepted accepted -> MarketClearingProcess.reduceAccepted(state, event.subject(), event.instant().ticks(), accepted);
            case MarketWorkOrderCancelled cancelled -> MarketClearingProcess.reduceWorkOrderCancelled(state, event.subject(), cancelled);
            case MarketDemandExpired expired -> MarketClearingProcess.reduceExpired(state, event.subject(), event.instant().ticks(), expired);
            case MarketDemandCancelled cancelled -> MarketClearingProcess.reduceCancelled(state, event.subject(), cancelled);
            case ProductionStarted started -> ProductionProcess.reduceStarted(state, event.subject(), started);
            case ProductionCompleted completed -> ProductionProcess.reduceCompleted(state, event.subject(), completed);
            case ProductionBlocked blocked -> ProductionProcess.reduceBlocked(state, event.subject(), blocked);
            case SupplyContractCreated created -> reduceContractCreated(state, event.subject(), created);
            case SupplyContractAbandoned abandoned -> reduceContractAbandoned(state, event.subject(), abandoned);
            case CargoLoaded loaded -> reduceCargoLoaded(state, event.subject(), loaded);
            case CargoDelivered delivered -> SupplyOperationProcess.reduceDelivered(state, event.subject(), delivered);
            case OperationCreated created -> reduceOperationCreated(state, event.subject(), created);
            case OperationAdvanced advanced -> reduceOperationAdvanced(state, event.subject(), advanced);
            case OperationAssemblyAdvanced advanced -> reduceOperationAssemblyAdvanced(state, event.subject(), advanced);
            case OperationAssemblyDeferred deferred -> reduceOperationAssemblyDeferred(state, event.subject(), deferred);
            case OperationTravelStarted started -> reduceOperationTravelStarted(state, event.subject(), started);
            case OperationTravelAdvanced advanced -> reduceOperationTravelAdvanced(state, event.subject(), advanced);
            case OperationTravelSegmentCompleted completed -> reduceOperationTravelSegmentCompleted(state, event.subject(), completed);
            case OperationColdSuspended suspended -> reduceOperationColdSuspended(state, event.subject(), suspended);
            case PhysicalIntentPrepared prepared -> reducePhysicalIntentPrepared(state, event.subject(), prepared);
            case PhysicalIntentTransition transition -> reducePhysicalIntentTransition(state, event.subject(), transition);
            case SceneLeasePrepared prepared -> reduceSceneLeasePrepared(state, event.subject(), event.instant(), prepared);
            case SceneLeaseHandoff handoff -> reduceSceneLeaseHandoff(state, event.subject(), event.instant(), handoff);
            case SettlementAssaultSceneLeasePrepared prepared -> reduceAssaultSceneLeasePrepared(state, event.subject(), event.instant(), prepared);
            case SettlementAssaultSceneLeaseHandoff handoff -> reduceAssaultSceneLeaseHandoff(state, event.subject(), event.instant(), handoff);
            case SceneLeaseTransition transition -> reduceSceneLeaseTransition(state, event.subject(), transition);
            case SceneLeaseReleased released -> reduceSceneLeaseReleased(state, event.subject(), released);
            case SceneLeaseRecoveryUnresolved unresolved -> reduceSceneLeaseRecoveryUnresolved(state, event.subject(), unresolved);
            case ActorDied death -> reduceActorDied(state, event.subject(), death);
            case AmbientActorDied death -> AmbientActorProcess.reduce(state, event.subject(), death);
            case AmbientActorObserved observation -> AmbientActorProcess.reduce(state, event.subject(), observation);
            case ResidentBorn birth -> reduceResidentBorn(state, event.subject(), birth);
            case ResidentMigrated migration -> reduceResidentMigrated(state, event.subject(), migration);
            case ResidentMigrationStarted started -> reduceMigrationStarted(state, event.subject(), started);
            case ResidentMigrationAdvanced advanced -> reduceMigrationAdvanced(state, event.subject(), advanced);
            case ResidentTransitAdvanced advanced -> reduceTransitAdvanced(state, event.subject(), advanced);
            case ResidentMigrationBlocked blocked -> reduceMigrationBlocked(state, event.subject(), blocked);
            case ResidentMigrationResumed resumed -> reduceMigrationResumed(state, event.subject(), resumed);
            case ResidentBirthStarted started -> PopulationBirthProcess.reduceStarted(state, event.subject(), started);
            case ResidentBirthCancelled cancelled -> PopulationBirthProcess.reduceCancelled(state, event.subject(), cancelled);
            case LegacySettlementProvisionStarted started -> SettlementProvisionProcess.reduceLegacyStarted(state, event.subject(), started);
            case SettlementProvisionStarted started -> SettlementProvisionProcess.reduceStarted(state, event.subject(), started);
            case SettlementProvisionConsumed consumed -> SettlementProvisionProcess.reduceConsumed(state, event.subject(), consumed);
            case SettlementProvisionResolved resolved -> SettlementProvisionProcess.reduceResolved(state, event.subject(), resolved);
            case ResidentHealthTransition transition -> HumanHealthProcess.reduceResidentTransition(state, event.subject(), event.instant().ticks(), transition);
            case SettlementQuarantineTransition transition -> HumanHealthProcess.reduceQuarantineTransition(state, event.subject(), event.instant().ticks(), transition);
            case ResourceSiteGrowthAdvanced advanced -> ResourceSiteProcess.reduceGrowth(state, event.subject(), advanced);
            case ResourceSitePreparationStarted started -> ResourceSiteProcess.reducePreparationStarted(state, event.subject(), started);
            case ResourceSitePrepared prepared -> ResourceSiteProcess.reducePrepared(state, event.subject(), prepared);
            case ResourceSiteHarvestStarted started -> ResourceSiteHarvestProcess.reduceStarted(state, event.subject(), started);
            case ResourceSiteHarvested harvested -> ResourceSiteHarvestProcess.reduceHarvested(state, event.subject(), harvested);
            case ResourceSiteConflictObserved conflict -> ResourceSiteProcess.reduceConflict(state, event.subject(), conflict);
            case StructureDamaged damaged -> reduceStructureDamaged(state, event.subject(), damaged);
            case PhysicalDeltaObserved observed -> FrontierWorldPhysicalObservationProcess.reduce(state, event.subject(), observed);
            case ResourceDeposited deposited -> FrontierWorldPhysicalObservationProcess.reduceResourceDeposit(state, event.subject(), deposited);
            case OperationFailed failed -> reduceOperationFailed(state, event.subject(), failed);
            case TerminalLogisticsCompacted compacted -> reduceTerminalLogisticsCompacted(state, event.subject(), event.instant().ticks(), compacted);
            case ExactItemCustodyChanged changed -> reduceExactItemCustodyChanged(state, event.subject(), changed);
            case ExactItemDestroyed destroyed -> reduceExactItemDestroyed(state, event.subject(), destroyed);
            case CargoCarrierReleased released -> reduceCargoCarrierReleased(state, event.subject(), released);
            case InventoryConflictObserved observed -> reduceInventoryConflict(state, event.subject(), observed);
            case ContainerSurfaceTransition transition -> ContainerSurfaceProcess.reduce(state, event.subject(), transition);
            case HiveGrowthStarted started -> HiveGrowthProcess.reduceStarted(state, event.subject(), started);
            case HiveGrowthBiomassConsumed consumed -> HiveGrowthProcess.reduceConsumed(state, event.subject(), consumed);
            case HiveGrowthCompleted completed -> HiveGrowthProcess.reduceCompleted(state, event.subject(), completed);
            case HiveGrowthBlocked blocked -> HiveGrowthProcess.reduceBlocked(state, event.subject(), blocked);
            case HiveNutrientTransferStarted started -> HiveNutrientTransferProcess.reduceStarted(state, event.subject(), started.transfer());
            case HiveNutrientTransferAdvanced advanced -> HiveNutrientTransferProcess.reduceAdvanced(state, event.subject(), advanced);
            case HiveNutrientTransferCompleted completed -> HiveNutrientTransferProcess.reduceCompleted(state, event.subject(), completed);
            case HiveNutrientTransferBlocked blocked -> HiveNutrientTransferProcess.reduceBlocked(state, event.subject(), blocked);
            case HiveNutrientTransferEndpointPrepared prepared -> HiveNutrientTransferProcess.reduceEndpointPrepared(state, event.subject(), prepared);
            case RouteConstructionStarted started -> RouteConstructionStateSupport.reduceStarted(state, event.subject(), started);
            case RouteConstructionMaterialLoaded loaded -> RouteConstructionStateSupport.reduceMaterialLoaded(state, event.subject(), loaded);
            case RouteTopologyCutover cutover -> RouteConstructionStateSupport.reduceCutover(state, event.subject(), cutover);
            case RoutePatrolStarted started -> RoutePatrolProcess.reduceStarted(state, event.subject(), started);
            case RoutePatrolAdvanced advanced -> RoutePatrolProcess.reduceAdvanced(state, event.subject(), advanced);
            case RoutePatrolObstructionConfirmed confirmed -> RoutePatrolProcess.reduceObstruction(state, event.subject(), confirmed);
            case RoutePatrolFailed failed -> RoutePatrolProcess.reduceFailed(state, event.subject(), failed);
            case SettlementInfectionObserved observed -> SettlementPerceptionProcess.reduce(state, event.subject(), observed);
            case HiveOperationObserved observed -> HivePerceptionProcess.reduce(state, event.subject(), observed);
            case HiveTerritoryObserved observed -> HiveTerritoryPerceptionProcess.reduce(state, event.subject(), observed);
            case HiveSettlementObserved observed -> HiveSettlementPerceptionProcess.reduce(state, event.subject(), observed);
            case HiveDoctrineSelected selected -> HiveDoctrineProcess.reduce(state, event.subject(), selected);
            case HotScoutOperationObserved observed -> HivePerceptionProcess.reduceHot(state, event.subject(), observed);
            case ScoutPatrolAdvanced advanced -> HiveScoutPatrolProcess.reduce(state, event.subject(), advanced);
            case RouteEngagementStarted started -> HiveRouteEngagementProcess.reduceStarted(state, event.subject(), started);
            case RouteEngagementAttackerAdvanced advanced -> HiveRouteEngagementProcess.reduceAdvanced(state, event.subject(), advanced);
            case RouteEngagementTransition transition -> HiveRouteEngagementProcess.reduceTransition(state, event.subject(), transition);
            case RouteEngagementStrike strike -> HiveRouteEngagementProcess.reduceStrike(state, event.subject(), strike);
            case RouteEngagementResolved resolved -> HiveRouteEngagementProcess.reduceResolved(state, event.subject(), resolved);
            case SettlementAssaultStarted started -> HiveSettlementAssaultProcess.reduceStarted(state, event.subject(), started);
            case SettlementAssaultAttackerAdvanced advanced -> HiveSettlementAssaultProcess.reduceAdvanced(state, event.subject(), advanced);
            case SettlementAssaultTransition transition -> HiveSettlementAssaultProcess.reduceTransition(state, event.subject(), transition);
            case SettlementAssaultStrike strike -> HiveSettlementAssaultProcess.reduceStrike(state, event.subject(), strike);
            case SettlementAssaultResolved resolved -> HiveSettlementAssaultProcess.reduceResolved(state, event.subject(), resolved);
            case StrategicObjectiveSelected selected -> StrategicObjectiveProcess.reduceObjective(state, event.subject(), selected);
            case StrategicTaskPlanned planned -> StrategicObjectiveProcess.reduceTask(state, event.subject(), planned);
            case StrategicTaskTransition transition -> StrategicObjectiveProcess.reduceTaskTransition(state, event.subject(), transition);
            default -> fail(event.payload().type());
        };
    }
    private static FrontierWorldState reduceResidentBorn(FrontierWorldState state, SubjectId subject, ResidentBorn birth) {
        return PopulationBirthProcess.reduceBorn(state, subject, birth);
    }
    private static FrontierWorldState reduceResidentMigrated(FrontierWorldState state, SubjectId subject, ResidentMigrated migration) {
        if (!subject.equals(migration.destinationSettlementId())) throw new IllegalArgumentException("resident migration lacks destination settlement owner");
        return state.recordResidentMigration(migration);
    }
    private static FrontierWorldState reduceMigrationStarted(FrontierWorldState state, SubjectId subject, ResidentMigrationStarted started) {
        if (!subject.equals(started.journey().originSettlementId())) throw new IllegalArgumentException("migration start lacks its origin settlement owner");
        return HumanPopulationStateSupport.startMigration(state, started.journey());
    }
    private static FrontierWorldState reduceMigrationAdvanced(FrontierWorldState state, SubjectId subject, ResidentMigrationAdvanced advanced) {
        ResidentMigrationJourney journey = state.humanPopulation().migration(advanced.residentId());
        if (journey == null || !subject.equals(journey.originSettlementId())) throw new IllegalArgumentException("migration advance lacks its origin settlement owner");
        return HumanPopulationStateSupport.advanceMigration(state, advanced);
    }
    private static FrontierWorldState reduceTransitAdvanced(FrontierWorldState state, SubjectId subject, ResidentTransitAdvanced advanced) {
        ResidentMigrationJourney journey = state.humanPopulation().migration(advanced.residentId());
        if (journey == null || !subject.equals(journey.originSettlementId())) throw new IllegalArgumentException("HOT transit observation lacks its origin settlement owner");
        return PopulationMigrationProcess.reduceHotAdvance(state, advanced);
    }
    private static FrontierWorldState reduceMigrationBlocked(FrontierWorldState state, SubjectId subject, ResidentMigrationBlocked blocked) {
        ResidentMigrationJourney journey = state.humanPopulation().migration(blocked.residentId());
        if (journey == null || !subject.equals(journey.originSettlementId())) throw new IllegalArgumentException("migration block lacks its origin settlement owner");
        return HumanPopulationStateSupport.blockMigration(state, blocked);
    }
    private static FrontierWorldState reduceMigrationResumed(FrontierWorldState state, SubjectId subject, ResidentMigrationResumed resumed) {
        ResidentMigrationJourney journey = state.humanPopulation().migration(resumed.residentId());
        if (journey == null || !subject.equals(journey.originSettlementId())) throw new IllegalArgumentException("migration resume lacks its origin settlement owner");
        return HumanPopulationStateSupport.resumeMigration(state, resumed);
    }
    private static FrontierWorldState reduceContractCreated(FrontierWorldState state, SubjectId subject, SupplyContractCreated created) {
        SupplyContract contract = created.contract();
        if (!subject.equals(contract.settlementId())) throw new IllegalArgumentException("contract subject does not own settlement");
        SubjectId depot = FrontierWorldState.depotId(contract.settlementId());
        boolean backed = state.inventory().items().values().stream().anyMatch(item -> item.itemKind().equals(contract.itemKind())
                && item.count() == contract.itemCount() && item.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(depot));
        if (!backed) throw new IllegalArgumentException("supply contract has no exact depot-backed item");
        return state.createSupplyContract(created.contract());
    }
    private static FrontierWorldState reduceContractAbandoned(FrontierWorldState state, SubjectId subject, SupplyContractAbandoned abandoned) {
        SupplyContract contract = state.contracts().get(abandoned.contractId());
        if (contract == null || !subject.equals(contract.settlementId())) throw new IllegalArgumentException("abandoned contract has a foreign settlement owner");
        return state.abandonOrderedSupplyContract(abandoned.contractId());
    }
    private static FrontierWorldState reduceCargoLoaded(FrontierWorldState state, SubjectId subject, CargoLoaded loaded) {
        SupplyContract contract = state.contracts().get(loaded.contractId());
        if (contract == null || !subject.equals(contract.settlementId()) || !loaded.cargo().id().equals(contract.cargoId()) || loaded.cargo().itemIds().size() != 1) throw new IllegalArgumentException("cargo load does not match its contract");
        ExactItemStack item = state.inventory().items().get(loaded.cargo().itemIds().getFirst());
        if (item == null || !item.itemKind().equals(contract.itemKind()) || item.count() != contract.itemCount()) throw new IllegalArgumentException("cargo item does not match contract demand");
        return state.loadContractCargo(loaded.contractId(), loaded.cargo());
    }
    private static FrontierWorldState reduceOperationCreated(FrontierWorldState state, SubjectId subject, OperationCreated created) {
        RouteOperation operation = created.operation();
        if (!subject.equals(operation.settlementId()) || operation.stage() != OperationStage.ASSEMBLING || operation.routeIndex() != 0) {
            throw new IllegalArgumentException("route operation must begin assembling at its owning settlement");
        }
        SupplyContract contract = state.contracts().values().stream().filter(value -> value.cargoId().equals(operation.cargoId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("route operation cargo has no supply contract"));
        if (contract.status() != ContractStatus.LOADED || !contract.settlementId().equals(operation.settlementId()) || !contract.recipientId().equals(operation.destinationId())) {
            throw new IllegalArgumentException("route operation does not match its loaded supply contract");
        }
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), operation.settlementId());
        if (!operation.route().equals(state.routeTopology().supplyWaypoints(state.bootstrap(), settlement.id()))) {
            throw new IllegalArgumentException("route operation must use the deterministic settlement-to-nest route");
        }
        return state.createOperation(operation);
    }
    private static FrontierWorldState reduceOperationAdvanced(FrontierWorldState state, SubjectId subject, OperationAdvanced advanced) {
        RouteOperation operation = state.operations().get(advanced.operationId());
        if (operation == null || !subject.equals(operation.settlementId())) throw new IllegalArgumentException("route advancement subject does not own operation");
        return state.advanceOperation(advanced.operationId(), advanced.routeIndex(), advanced.stage());
    }
    private static FrontierWorldState reduceOperationAssemblyAdvanced(FrontierWorldState state, SubjectId subject, OperationAssemblyAdvanced advanced) {
        RouteOperation operation = state.operations().get(advanced.operationId());
        if (operation == null || !subject.equals(operation.settlementId())) throw new IllegalArgumentException("operation assembly subject does not own operation");
        return state.advanceOperationAssembly(advanced.operationId(), advanced.assembly());
    }
    private static FrontierWorldState reduceOperationAssemblyDeferred(FrontierWorldState state, SubjectId subject, OperationAssemblyDeferred deferred) {
        RouteOperation operation = state.operations().get(deferred.operationId());
        if (operation == null || !subject.equals(operation.settlementId())) throw new IllegalArgumentException("operation assembly deferral subject does not own operation");
        return state.deferOperationAssembly(deferred.operationId(), deferred.deferral());
    }
    private static FrontierWorldState reduceOperationTravelStarted(FrontierWorldState state, SubjectId subject, OperationTravelStarted started) {
        RouteOperation operation = state.operations().get(started.operationId());
        if (operation == null || !subject.equals(operation.settlementId())) throw new IllegalArgumentException("operation travel subject does not own operation");
        return state.startOperationTravel(started.operationId(), started.travel());
    }
    private static FrontierWorldState reduceOperationTravelAdvanced(FrontierWorldState state, SubjectId subject, OperationTravelAdvanced advanced) {
        RouteOperation operation = state.operations().get(advanced.operationId());
        if (operation == null || !subject.equals(operation.settlementId())) throw new IllegalArgumentException("operation travel subject does not own operation");
        return state.advanceOperationTravel(advanced.operationId(), advanced.travel());
    }
    private static FrontierWorldState reduceOperationTravelSegmentCompleted(FrontierWorldState state, SubjectId subject, OperationTravelSegmentCompleted completed) {
        RouteOperation operation = state.operations().get(completed.operationId());
        if (operation == null || !subject.equals(operation.settlementId())) throw new IllegalArgumentException("operation travel completion subject does not own operation");
        return state.completeOperationTravelSegment(completed.operationId());
    }
    private static FrontierWorldState reduceOperationColdSuspended(FrontierWorldState state, SubjectId subject, OperationColdSuspended suspended) {
        RouteOperation operation = state.operations().get(suspended.operationId());
        SceneLease lease = state.sceneLeases().get(suspended.leaseId());
        if (operation == null || !subject.equals(operation.settlementId()) || lease == null || !FrontierSceneBehaviors.isLogistics(lease) || !FrontierSceneBehaviors.logistics(lease).operationId().equals(operation.id())
                || lease.status() == SceneLeaseStatus.CLOSED || lease.status() == SceneLeaseStatus.UNKNOWN_AFTER_RESTART) {
            throw new IllegalArgumentException("cold operation suspension lacks an active matching scene lease");
        }
        return state;
    }
    private static FrontierWorldState reducePhysicalIntentPrepared(FrontierWorldState state, SubjectId subject, PhysicalIntentPrepared prepared) {
        PhysicalIntent intent = prepared.intent();
        if (intent.kind() == PhysicalIntentKind.STRUCTURAL_REPAIR) return StructuralRepairProcess.reducePrepared(state, subject, intent);
        if (intent.kind() == PhysicalIntentKind.ROUTE_CONSTRUCTION) return RouteConstructionProcess.reducePrepared(state, subject, intent);
        if (intent.kind() == PhysicalIntentKind.ROUTE_CONSTRUCTION_MATERIAL_LOADING) {
            if (!subject.equals(FrontierRouteNetwork.OWNER)) throw new IllegalArgumentException("route construction material pickup must be prepared by the route network");
            RouteConstructionStateSupport.validateMaterialLoadingIntent(state, intent); return state.preparePhysicalIntent(intent);
        }
        if (intent.kind() == PhysicalIntentKind.DECONTAMINATION) return DecontaminationProcess.reducePrepared(state, subject, intent);
        if (intent.kind() == PhysicalIntentKind.RESOURCE_SITE_PREPARATION) return ResourceSiteProcess.reducePrepared(state, subject, intent);
        if (intent.kind() == PhysicalIntentKind.RESOURCE_SITE_HARVEST) return ResourceSiteHarvestProcess.reducePrepared(state, subject, intent);
        if (intent.kind() == PhysicalIntentKind.PRODUCTION_TRANSFORMATION) {
            ProductionJob job = state.productionJobs().get(intent.causeSubjectId());
            if (job == null || !subject.equals(job.settlementId())) throw new IllegalArgumentException("production transformation must be prepared by its settlement");
            ProductionTransformationStateSupport.validateIntent(state, intent);
            return state.preparePhysicalIntent(intent);
        }
        if (intent.kind() == PhysicalIntentKind.CARGO_LOADING) return CargoLoadingStateSupport.reducePrepared(state, subject, intent);
        if (intent.kind() == PhysicalIntentKind.HIVE_NUTRIENT_DEPARTURE || intent.kind() == PhysicalIntentKind.HIVE_NUTRIENT_ARRIVAL) {
            if (!subject.equals(state.bootstrap().hive().id())) throw new IllegalArgumentException("hive nutrient endpoint intent must be prepared by the hive");
            return state.preparePhysicalIntent(intent);
        }
        if (intent.kind() == PhysicalIntentKind.EXACT_ITEM_CONSUMPTION) {
            if (state.hiveColony().growthJobs().containsKey(intent.causeSubjectId())) return HiveGrowthProcess.reducePrepared(state, subject, intent);
            if (state.humanPopulation().birthJobs().containsKey(intent.causeSubjectId())) return PopulationBirthProcess.reducePrepared(state, subject, intent);
            if (state.humanPopulation().provisions().containsKey(intent.causeSubjectId())) return SettlementProvisionProcess.reducePrepared(state, subject, intent);
            throw new IllegalArgumentException("exact consumption has no supported owning process");
        }
        if (intent.kind() == PhysicalIntentKind.SCENE_STRIKE) {
            SceneStrikeStateSupport.validateIntent(state, intent);
            if (!subject.equals(SceneStrikeStateSupport.owner(state, intent))) throw new IllegalArgumentException("scene strike must be prepared by its exact scene owner");
            return state.preparePhysicalIntent(intent);
        }
        if (intent.kind() == PhysicalIntentKind.EXPLOSION) {
            if (!subject.equals(state.bootstrap().hive().id())) throw new IllegalArgumentException("explosion intent must be prepared by the hive");
            ExplosionStateSupport.validateIntent(state, intent);
            return state.preparePhysicalIntent(intent);
        }
        RouteOperation operation = state.operations().get(intent.causeSubjectId());
        if (operation == null || operation.stage() != OperationStage.ARRIVED || !subject.equals(operation.settlementId())) {
            throw new IllegalArgumentException("physical intent must be prepared by an arrived route operation owner");
        }
        if (intent.kind() != PhysicalIntentKind.CARGO_HANDOFF || !intent.subjectIds().contains(operation.cargoId())
                || !intent.subjectIds().contains(operation.id())) throw new IllegalArgumentException("physical intent does not own arrived cargo hand-off");
        return state.preparePhysicalIntent(intent);
    }
    private static FrontierWorldState reducePhysicalIntentTransition(FrontierWorldState state, SubjectId subject, PhysicalIntentTransition transition) {
        PhysicalIntent intent = state.physicalIntents().get(transition.intentId());
        if (intent == null) throw new IllegalArgumentException("physical intent transition has no prepared intent");
        if (intent.kind() == PhysicalIntentKind.ROUTE_CONSTRUCTION_MATERIAL_LOADING) {
            if (!subject.equals(FrontierRouteNetwork.OWNER)) throw new IllegalArgumentException("route construction material pickup transition lacks route-network ownership");
            return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
        }
        if (intent.kind() == PhysicalIntentKind.STRUCTURAL_REPAIR || intent.kind() == PhysicalIntentKind.ROUTE_CONSTRUCTION || intent.kind() == PhysicalIntentKind.DECONTAMINATION) {
            SubjectId owner = intent.kind() == PhysicalIntentKind.DECONTAMINATION ? DecontaminationProcess.owner(state, intent.causeSubjectId()).id()
                    : FrontierWorldStateSupport.semanticOwner(state.bootstrap(), state.hiveColony(), intent.causeSubjectId());
            if (!subject.equals(owner)) {
                throw new IllegalArgumentException("structural repair transition lacks its owning settlement");
            }
            if (intent.kind() == PhysicalIntentKind.DECONTAMINATION) DecontaminationProcess.taskForIntent(state, intent, StrategicTaskStatus.ACTIVE);
            return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
        }
        if (intent.kind() == PhysicalIntentKind.EXPLOSION) {
            if (!subject.equals(state.bootstrap().hive().id())) throw new IllegalArgumentException("explosion transition lacks hive ownership");
            return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
        }
        if (intent.kind() == PhysicalIntentKind.SCENE_STRIKE) {
            if (!subject.equals(SceneStrikeStateSupport.owner(state, intent))) throw new IllegalArgumentException("scene strike transition lacks exact scene ownership");
            return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
        }
        if (intent.kind() == PhysicalIntentKind.RESOURCE_SITE_PREPARATION) {
            if (!subject.equals(intent.causeSubjectId())) {
                throw new IllegalArgumentException("resource-site preparation transition lacks site ownership");
            }
            return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
        }
        if (intent.kind() == PhysicalIntentKind.RESOURCE_SITE_HARVEST) {
            if (!subject.equals(intent.causeSubjectId())) throw new IllegalArgumentException("resource-site harvest transition lacks site ownership");
            return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
        }
        if (intent.kind() == PhysicalIntentKind.PRODUCTION_TRANSFORMATION) {
            ProductionJob job = state.productionJobs().get(intent.causeSubjectId());
            if (job == null || !subject.equals(job.settlementId())) throw new IllegalArgumentException("production transformation transition lacks settlement ownership");
            return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
        }
        if (intent.kind() == PhysicalIntentKind.CARGO_LOADING) return CargoLoadingStateSupport.reduceTransition(state, subject, intent, transition);
        if (intent.kind() == PhysicalIntentKind.HIVE_NUTRIENT_DEPARTURE || intent.kind() == PhysicalIntentKind.HIVE_NUTRIENT_ARRIVAL) {
            if (!subject.equals(state.bootstrap().hive().id())) throw new IllegalArgumentException("hive nutrient endpoint transition lacks hive ownership");
            return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
        }
        if (intent.kind() == PhysicalIntentKind.EXACT_ITEM_CONSUMPTION) {
            HiveGrowthJob job = state.hiveColony().growthJobs().get(intent.causeSubjectId());
            if (job != null) {
                if (!subject.equals(job.hiveId())) throw new IllegalArgumentException("hive growth consumption transition lacks hive ownership");
                return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
            }
            ResidentBirthJob birth = state.humanPopulation().birthJobs().get(intent.causeSubjectId());
            if (birth != null) {
                if (!subject.equals(birth.settlementId())) throw new IllegalArgumentException("resident birth consumption transition lacks settlement ownership");
                return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
            }
            if (!state.humanPopulation().provisions().containsKey(intent.causeSubjectId()) || !subject.equals(intent.causeSubjectId())) {
                throw new IllegalArgumentException("settlement provision consumption transition lacks settlement ownership");
            }
            return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
        }
        RouteOperation operation = state.operations().get(intent.causeSubjectId());
        if (operation == null || !subject.equals(operation.settlementId())) throw new IllegalArgumentException("physical intent transition subject does not own operation");
        return state.transitionPhysicalIntent(transition.intentId(), transition.status(), transition.observation());
    }
    private static FrontierWorldState reduceSceneLeasePrepared(FrontierWorldState state, SubjectId subject, SimInstant instant, SceneLeasePrepared prepared) {
        SceneLease lease = prepared.lease();
        RouteOperation operation = state.operations().get(FrontierSceneBehaviors.logistics(lease).operationId());
        if (operation == null || !subject.equals(operation.settlementId()) || !lease.handoffInstant().equals(instant)) {
            throw new IllegalArgumentException("scene lease does not match its current operation hand-off");
        }
        return state.prepareSceneLease(lease);
    }
    private static FrontierWorldState reduceSceneLeaseHandoff(FrontierWorldState state, SubjectId subject, SimInstant instant, SceneLeaseHandoff handoff) {
        SceneLease lease = handoff.lease();
        RouteOperation operation = state.operations().get(FrontierSceneBehaviors.logistics(lease).operationId());
        if (operation == null || !subject.equals(operation.settlementId()) || !lease.handoffInstant().equals(instant)) {
            throw new IllegalArgumentException("scene hand-off does not match its current operation hand-off");
        }
        return state.handoffAmbientScene(handoff);
    }
    private static FrontierWorldState reduceAssaultSceneLeasePrepared(FrontierWorldState state, SubjectId subject, SimInstant instant,
                                                                       SettlementAssaultSceneLeasePrepared prepared) {
        SceneLease lease = prepared.lease();
        if (!subject.equals(FrontierSettlementAssaultSceneSupport.owner(state, lease)) || !lease.handoffInstant().equals(instant)) {
            throw new IllegalArgumentException("assault scene lease does not match its retained battle hand-off");
        }
        return state.prepareSceneLease(lease);
    }
    private static FrontierWorldState reduceAssaultSceneLeaseHandoff(FrontierWorldState state, SubjectId subject, SimInstant instant,
                                                                       SettlementAssaultSceneLeaseHandoff handoff) {
        SceneLease lease = handoff.lease();
        if (!subject.equals(FrontierSettlementAssaultSceneSupport.owner(state, lease)) || !lease.handoffInstant().equals(instant)) {
            throw new IllegalArgumentException("assault scene hand-off does not match its retained battle");
        }
        return state.handoffAmbientScene(new SceneLeaseHandoff(lease, handoff.ambientMembers()));
    }
    private static FrontierWorldState reduceSceneLeaseTransition(FrontierWorldState state, SubjectId subject, SceneLeaseTransition transition) {
        SceneLease lease = state.sceneLeases().get(transition.leaseId());
        if (lease == null || !subject.equals(FrontierSceneOwnerSupport.owner(state, lease))) throw new IllegalArgumentException("scene lease transition lacks its owning scene");
        return state.transitionSceneLease(transition.leaseId(), transition.status());
    }
    private static FrontierWorldState reduceSceneLeaseReleased(FrontierWorldState state, SubjectId subject, SceneLeaseReleased released) {
        SceneLease lease = state.sceneLeases().get(released.leaseId());
        if (lease == null || !subject.equals(FrontierSceneOwnerSupport.owner(state, lease))) throw new IllegalArgumentException("scene release lacks its owning scene");
        return state.releaseSceneLease(released.leaseId(), released.members());
    }
    private static FrontierWorldState reduceActorDied(FrontierWorldState state, SubjectId subject, ActorDied death) {
        SceneLease lease = state.sceneLeases().get(death.leaseId());
        if (lease == null || !subject.equals(FrontierSceneOwnerSupport.owner(state, lease))) throw new IllegalArgumentException("actor death lacks its owning scene");
        return state.recordActorDeath(death);
    }
    private static FrontierWorldState reduceStructureDamaged(FrontierWorldState state, SubjectId subject, StructureDamaged damage) {
        if (!subject.equals(FrontierWorldStateSupport.structureSettlement(state.bootstrap(), damage.structureId()))) {
            throw new IllegalArgumentException("structure damage lacks its owning settlement");
        }
        return state.recordStructureDamage(damage);
    }
    private static FrontierWorldState reduceOperationFailed(FrontierWorldState state, SubjectId subject, OperationFailed failed) {
        RouteOperation operation = state.operations().get(failed.operationId());
        if (operation == null || !subject.equals(operation.settlementId())) throw new IllegalArgumentException("operation failure lacks its owning settlement");
        boolean death = operation.participantIds().stream().anyMatch(actor -> state.actorLocations().get(actor).condition().status() == ActorLifeStatus.DEAD);
        boolean obstruction = "route-obstructed".equals(failed.reason())
                && !FrontierRouteNetwork.isPassable(state.bootstrap(), operation.route(), state.physicalDeltas());
        boolean recoveryUnresolved = "scene-recovery-unresolved".equals(failed.reason()) && state.sceneLeases().values().stream().filter(FrontierSceneBehaviors::isLogistics)
                .anyMatch(lease -> FrontierSceneBehaviors.logistics(lease).operationId().equals(operation.id()) && lease.status() == SceneLeaseStatus.UNKNOWN_AFTER_RESTART
                        && lease.recoveryEvidence().isPresent());
        if (!death && !obstruction && !recoveryUnresolved) {
            throw new IllegalArgumentException("operation failure lacks a dead participant or observed route obstruction");
        }
        return state.failOperation(failed.operationId());
    }
    private static FrontierWorldState reduceTerminalLogisticsCompacted(FrontierWorldState state, SubjectId subject, long atTick,
                                                                        TerminalLogisticsCompacted compacted) {
        RouteOperation operation = state.operations().get(compacted.operationId());
        if (operation == null || !subject.equals(operation.settlementId())) {
            throw new IllegalArgumentException("terminal logistics receipt has a foreign operation owner");
        }
        return state.compactTerminalLogistics(compacted.operationId(), atTick);
    }
    private static FrontierWorldState reduceSceneLeaseRecoveryUnresolved(FrontierWorldState state, SubjectId subject, SceneLeaseRecoveryUnresolved unresolved) {
        SceneLease lease = state.sceneLeases().get(unresolved.leaseId());
        if (lease == null || !subject.equals(FrontierSceneOwnerSupport.owner(state, lease))) throw new IllegalArgumentException("scene recovery evidence lacks its owning scene");
        return FrontierSceneLeaseStateSupport.recoveryUnresolved(state, unresolved);
    }
    private static FrontierWorldState reduceExactItemCustodyChanged(FrontierWorldState state, SubjectId subject, ExactItemCustodyChanged changed) {
        if (!subject.equals(FrontierWorldStateSupport.itemOwner(state, changed))) throw new IllegalArgumentException("item custody observation lacks its canonical owner");
        return state.withInventory(state.inventory().moveObservedItem(changed.itemId(), changed.from(), changed.to()));
    }
    private static FrontierWorldState reduceExactItemDestroyed(FrontierWorldState state, SubjectId subject, ExactItemDestroyed destroyed) {
        ExactItemStack item = state.inventory().items().get(destroyed.itemId());
        if (item == null || !item.custody().equals(destroyed.source())
                || !subject.equals(FrontierWorldStateSupport.itemOwner(state, destroyed))) {
            throw new IllegalArgumentException("item destruction lacks its canonical owner");
        }
        return state.withInventory(state.inventory().destroyObservedItem(destroyed.itemId(), destroyed.source()));
    }
    private static FrontierWorldState reduceCargoCarrierReleased(FrontierWorldState state, SubjectId subject, CargoCarrierReleased released) {
        SceneLease lease = state.sceneLeases().get(released.leaseId());
        RouteOperation operation = lease == null || !FrontierSceneBehaviors.isLogistics(lease) ? null : state.operations().get(FrontierSceneBehaviors.logistics(lease).operationId());
        if (operation == null || !subject.equals(operation.settlementId())) throw new IllegalArgumentException("cargo carrier release lacks its owning settlement");
        return state.releaseCargoCarrier(released);
    }
    private static FrontierWorldState reduceInventoryConflict(FrontierWorldState state, SubjectId subject, InventoryConflictObserved observed) {
        InventoryConflict conflict = observed.conflict();
        ContainerRecord container = state.inventory().containers().get(conflict.containerId());
        if (container == null || !subject.equals(container.ownerId())) throw new IllegalArgumentException("inventory conflict lacks its container owner");
        return state.withInventory(state.inventory().recordConflict(conflict));
    }
    private static FrontierWorldState fail(String type) { throw new IllegalStateException("unregistered v3 world event: " + type); }
}
