package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Small deterministic v3 scenes for development verification; never selected by the production bootstrap. */
final class FrontierDevelopmentScenarios {
    private FrontierDevelopmentScenarios() { }

    static FrontierWorldState hotSceneStrikeState(WorldId worldId, long seed) {
        return hotSceneStrikeFixture(worldId, seed).state();
    }

    static RouteSceneReturnFixture hotSceneStrikeFixture(WorldId worldId, long seed) {
        return hotSceneStrikeFixture(routeSceneReturnFixture(worldId, seed));
    }

    static RouteSceneReturnFixture hotSceneStrikeFixture(FrontierBootstrap bootstrap) {
        return hotSceneStrikeFixture(routeSceneReturnFixture(routeCustodyConfiguration(
                FrontierV3FixtureCatalog.uncontestedSupplyConfiguration(bootstrap))));
    }

    private static RouteSceneReturnFixture hotSceneStrikeFixture(RouteSceneReturnFixture base) {
        FrontierWorldState state = base.state();
        RouteOperation operation = initialNorthwatchShipment(state).orElseThrow();
        BlockPosition intercept = operation.activeTravel().orElseThrow().cargoAnchor().surface().support();
        SubjectId operationId = operation.id();
        List<Bioform> bioforms = state.bootstrap().hive().bioforms();
        List<Bioform> exactRoster = java.util.stream.Stream.of(
                bioforms.stream().filter(Bioform::isOverseer).sorted(Comparator.comparing(Bioform::id)).limit(1),
                bioforms.stream().filter(Bioform::isExplosiveAssaulter).sorted(Comparator.comparing(Bioform::id)).limit(1),
                bioforms.stream().filter(Bioform::isDefender).sorted(Comparator.comparing(Bioform::id)).limit(2))
                .flatMap(java.util.function.Function.identity()).toList();
        if (exactRoster.size() != 4) throw new IllegalStateException("development scene needs one exact Overseer and three subordinate bodies");
        for (Bioform bioform : exactRoster) {
            state = deployFixtureBioform(state, bioform.id(), intercept);
        }
        SubjectId hive = state.bootstrap().hive().id();
        StrategicPlanState plans = state.strategicPlans();
        for (StrategicTask existing : plans.tasks().values()) {
            if (existing.ownerId().equals(hive) && plans.objectives().get(existing.objectiveId()).status() == StrategicObjectiveStatus.ACTIVE) {
                plans = plans.transitionTask(existing.id(), StrategicTaskStatus.BLOCKED);
            }
        }
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:development-hot-strike"), hive,
                StrategicObjectiveKind.HIVE_INTERCEPT_ROUTE_OPERATION, Optional.empty(), 99, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:development-hot-strike"), objective.id(), hive,
                StrategicTaskKind.INTERCEPT_ROUTE_OPERATION, Optional.empty(), Optional.of(operationId), Optional.empty(),
                List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD), List.of(), StrategicTaskStatus.PENDING, Optional.of(intercept));
        state = state.withStrategicPlans(plans.addObjective(objective).addTask(task));
        Bioform scout = state.bootstrap().hive().bioforms().stream().filter(Bioform::isScout).findFirst().orElseThrow();
        HiveOperationKnowledge.Sighting sighting = new HiveOperationKnowledge.Sighting(operation.id(), scout.id(), intercept, base.instant().ticks());
        state = state.withStrategicPlans(state.strategicPlans().withHiveOperationKnowledge(state.strategicPlans().hiveOperationKnowledge().observe(sighting)));
        ScheduledAction action = HiveRouteEngagementProcess.start(task, base.instant().ticks());
        for (ProposedEvent event : HiveRouteEngagementProcess.planStart(state, action)) {
            if (event.payload() instanceof StrategicTaskTransition transition) state = StrategicObjectiveProcess.reduceTaskTransition(state, hive, transition);
            if (event.payload() instanceof RouteEngagementStarted started) state = HiveRouteEngagementProcess.reduceStarted(state, hive, started);
            if (event.payload() instanceof RouteEngagementTransition transition) state = HiveRouteEngagementProcess.reduceTransition(state, hive, transition);
        }
        if (state.coldEngagementSceneCandidates().isEmpty()) throw new IllegalStateException("development scene did not enter COLD engagement");
        return new RouteSceneReturnFixture(state, base.instant(), List.of());
    }

    /**
     * Disposable-only class-D boundary.  The fixture advances only the ordinary retained COLD
     * ingress to its first route formation and then stops: no scene lease, body, world block or
     * outcome is injected.  A naturally visiting player is the sole admission demand for the
     * first observed HOT edge.
     */
    static RoutePatrolFixture routePatrolFixture(WorldId worldId, long seed) {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(worldId, seed));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        List<ResidentProfile> guards = FrontierWorldStateSupport.availableRouteResidents(state, settlement.id(), ResidentProfession.SECURITY_WORKER);
        if (guards.size() < 2) throw new IllegalStateException("route-patrol fixture needs two exact security residents");
        SubjectId objectiveId = new SubjectId("objective:development-route-patrol");
        SubjectId taskId = new SubjectId("task:development-route-patrol");
        StrategicObjective objective = new StrategicObjective(objectiveId, settlement.id(), StrategicObjectiveKind.SETTLEMENT_PATROL_OBSTRUCTED_ROUTE,
                Optional.empty(), 99, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(taskId, objectiveId, settlement.id(), StrategicTaskKind.PATROL_OBSTRUCTED_ROUTE,
                Optional.empty(), List.of(StrategicTaskRequirement.AVAILABLE_GUARD), List.of(), StrategicTaskStatus.ACTIVE);
        RoutePatrol patrol = RoutePatrol.planned(state, task, settlement,
                RouteUnitManifest.patrol(taskId, guards.getFirst().id(), List.of(guards.get(1).id())));
        state = state.withStrategicPlans(state.strategicPlans().addObjective(objective).addTask(task));
        state = RoutePatrolProcess.reduceStarted(state, settlement.id(),
                new RoutePatrolStarted(patrol, RoutePatrolExecutionAuthority.admission(state, patrol)));
        int maximumTransitions = patrol.assembly().members().values().stream()
                .mapToInt(member -> member.corridor().size() - 1).sum();
        for (int transition = 0; transition <= maximumTransitions; transition++) {
            RoutePatrol current = state.strategicPlans().routePatrols().get(taskId);
            if (current.status() == RoutePatrolStatus.EN_ROUTE) {
                return new RoutePatrolFixture(state, new SimInstant(1_000L), List.of(), taskId, current);
            }
            if (current.status() != RoutePatrolStatus.ASSEMBLING) {
                throw new IllegalStateException("route-patrol fixture ingress cannot reach its retained formation");
            }
            state = RoutePatrolProcess.reduceFormationAdvanced(state, settlement.id(),
                    new RoutePatrolFormationAdvanced(taskId, PatrolFormationStep.capture(current), RoutePatrolExecutionAuthority.current(state, current)));
        }
        throw new IllegalStateException("route-patrol fixture ingress did not reach its declared bounded formation");
    }

    /**
     * Disposable-only real assault boundary. The fixture advances the normal bounded Scout
     * sighting and attacker approach process to COLD_COMBAT, but creates neither a scene lease
     * nor a Minecraft body: a naturally visiting player must admit the typed HOT scene.
     */
    static SettlementAssaultFixture settlementAssaultFixture(WorldId worldId, long seed) {
        return settlementAssaultFixture(FrontierBootstrapper.create(worldId, seed));
    }

    static SettlementAssaultFixture settlementAssaultFixture(FrontierBootstrap bootstrap) {
        SettlementAssaultFixture started = startedSettlementAssaultFixture(bootstrap);
        FrontierWorldState state = started.state();
        ScheduledAction next = started.schedules().stream().filter(action -> action.kind().equals("frontier.settlement_assault.progress")).findFirst()
                .orElseThrow(() -> new IllegalStateException("development assault fixture did not schedule approach"));
        SubjectId hive = state.bootstrap().hive().id();
        // The retained approach now consists of genuine GROUND_BIOFORM topology edges rather
        // than the former six-point interpolation.  Keep this disposable pure fixture bounded,
        // but large enough to traverse the 1024-cell world without shortcutting its cursor.
        for (int step = 0; step < 4_096; step++) {
            List<ProposedEvent> progress = HiveSettlementAssaultProcess.planProgress(state, next);
            for (ProposedEvent event : progress) {
                if (event.payload() instanceof SettlementAssaultAttackerAdvanced advanced) state = HiveSettlementAssaultProcess.reduceAdvanced(state, hive, advanced);
                if (event.payload() instanceof SettlementAssaultTransition transition) state = HiveSettlementAssaultProcess.reduceTransition(state, hive, transition);
            }
            SettlementAssault assault = state.strategicPlans().settlementAssaults().values().stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("development assault fixture lost its assault"));
            if (assault.status() == SettlementAssaultStatus.COLD_COMBAT) {
                if (state.coldSettlementAssaultSceneCandidates().size() != 1) {
                    throw new IllegalStateException("development assault fixture has no exact COLD battlefield");
                }
                // The fixture stops at the COLD/HOT boundary. It deliberately does not let a
                // background COLD combat due action decide the battle before the native pilot
                // has naturally loaded its scene.
                return new SettlementAssaultFixture(state, new SimInstant(400L), List.of(), assault.id());
            }
            next = progress.stream().map(ProposedEvent::payload).filter(ScheduleEffect.Created.class::isInstance)
                    .map(ScheduleEffect.Created.class::cast).map(ScheduleEffect.Created::action)
                    .filter(action -> action.kind().equals("frontier.settlement_assault.progress")).findFirst()
                    .orElseThrow(() -> new IllegalStateException("development assault fixture approach stalled"));
        }
        throw new IllegalStateException("development assault fixture did not reach COLD combat");
    }

    /**
     * Full-bootstrap pressure ingress: two disjoint current owners meet at Northwatch, while
     * all twelve settlement authorities and both hive seed nests remain the production ones.
     * It injects neither lease nor body; one ordinary visit admits each eligible front.
     */
    static MultiFrontPressureFixture multiFrontPressureFixture(WorldId worldId, long seed) {
        SettlementAssaultFixture assault = settlementAssaultFixture(worldId, seed);
        SubjectId settlement = new SubjectId("settlement:1");
        // This boundary fixture requires a disjoint worker, not the bootstrap farmers
        // already serving as defenders. Declare the fixture roster before admission.
        ResidentProfile participant = SettlementWorkforce.candidates(assault.state(), settlement,
                ResidentProfession.AGRICULTURAL_WORKER).getFirst();
        FrontierWorldState ingress = HarvestFixtureOwners.withSingleParticipant(assault.state(), settlement, participant.id());
        FrontierResourceSiteHarvestFixture.Fixture harvest = FrontierResourceSiteHarvestFixture.create(ingress);
        SettlementAssault retained = harvest.state().strategicPlans().settlementAssaults().get(assault.assaultId());
        if (retained == null || retained.status() != SettlementAssaultStatus.COLD_COMBAT
                || harvest.state().coldSettlementAssaultSceneCandidates().size() != 1) {
            throw new IllegalStateException("multi-front fixture lost the retained COLD assault");
        }
        return new MultiFrontPressureFixture(harvest.state(), harvest.instant(), harvest.schedules(), assault.assaultId(), harvest.jobId());
    }

    /**
     * Keeps one genuine COLD combat action due after bootstrap.  Unlike the HOT-scene carrier,
     * this fixture does not suppress combat for a visitor: the ordinary duration driver owns
     * the exact bomber strike and its aftermath before any player loads the target chunk.
     */
    static SettlementAssaultFixture coldBomberAftermathFixture(WorldId worldId, long seed) {
        SettlementAssaultFixture boundary = settlementAssaultFixture(worldId, seed);
        FrontierWorldState state = boundary.state(); long dueAt = boundary.instant().ticks() + 20L;
        SubjectId hive = state.bootstrap().hive().id();
        for (int turn = 0; turn < 8; turn++) {
            SettlementAssault assault = state.strategicPlans().settlementAssaults().get(boundary.assaultId());
            if (assault == null || assault.status() != SettlementAssaultStatus.COLD_COMBAT) throw new IllegalStateException("cold bomber fixture lost its COLD assault");
            ScheduledAction combat = HiveSettlementAssaultProcess.combat(assault, dueAt);
            List<ProposedEvent> events = HiveSettlementAssaultProcess.planCombat(state, combat);
            DeferredAftermathPrepared prepared = events.stream().map(ProposedEvent::payload).filter(DeferredAftermathPrepared.class::isInstance)
                    .map(DeferredAftermathPrepared.class::cast).findFirst().orElse(null);
            if (prepared != null) {
                if (!prepared.aftermath().causeId().value().equals("cause:development-settlement-assault-epoch-4-attacker-bioform-west-19")) {
                    throw new IllegalStateException("cold bomber fixture selected a substituted bomber cause: " + prepared.aftermath().causeId());
                }
                return new SettlementAssaultFixture(state, new SimInstant(dueAt - 1L), List.of(combat), boundary.assaultId());
            }
            SettlementAssaultStrike strike = events.stream().map(ProposedEvent::payload).filter(SettlementAssaultStrike.class::isInstance)
                    .map(SettlementAssaultStrike.class::cast).findFirst().orElseThrow(() -> new IllegalStateException("cold bomber fixture stalled before due combat"));
            state = HiveSettlementAssaultProcess.reduceStrike(state, hive, strike);
            dueAt += state.bootstrap().ruleset().cadence().hiveSettlementAssaultCombatInterval();
        }
        throw new IllegalStateException("cold bomber fixture could not retain its exact ordinary due cause");
    }

    /**
     * Read-only equipment hand-off boundary. It retains an approaching assault and one exact
     * depot sword, but neither an intent nor an active surface/body: the native visit must make
     * the ordinary container, resident and issue schedulers produce the hand-off.
     */
    static SettlementAssaultFixture defenderEquipmentFixture(WorldId worldId, long seed) {
        SettlementAssaultFixture started = startedSettlementAssaultFixture(worldId, seed);
        SettlementAssault assault = started.state().strategicPlans().settlementAssaults().get(started.assaultId());
        SubjectId depot = FrontierWorldState.depotId(assault.settlementId());
        int slot = started.state().inventory().firstFreeSlot(depot).orElseThrow();
        ExactItemStack sword = new ExactItemStack(new SubjectId("item:development-defender-sword"), assault.settlementId(),
                "minecraft:iron_sword", 1, new InventoryCustody.ContainerSlot(depot, slot));
        return new SettlementAssaultFixture(started.state().withInventory(started.state().inventory().store(sword)),
                started.instant(), started.schedules(), started.assaultId());
    }

    /**
     * Read-only inverse boundary: a resolved assault retains the same exact defender and sword
     * in actor custody. A natural visit must hydrate that body, materialize its depot and run the
     * ordinary durable return request; the fixture never mutates the physical world itself.
     */
    static SettlementAssaultFixture defenderEquipmentReturnFixture(WorldId worldId, long seed) {
        SettlementAssaultFixture started = startedSettlementAssaultFixture(worldId, seed);
        SettlementAssault assault = started.state().strategicPlans().settlementAssaults().get(started.assaultId());
        SubjectId defender = assault.defenderIds().getFirst();
        SubjectId depot = FrontierWorldState.depotId(assault.settlementId()); int slot = started.state().inventory().firstFreeSlot(depot).orElseThrow();
        ExactItemStack sword = new ExactItemStack(new SubjectId("item:development-defender-return-sword"), assault.settlementId(),
                "minecraft:iron_sword", 1, new InventoryCustody.ContainerSlot(depot, slot));
        FrontierWorldState state = started.state().withInventory(started.state().inventory().store(sword)
                .moveObservedItem(sword.id(), sword.custody(), new InventoryCustody.Actor(defender)));
        state = HiveSettlementAssaultProcess.reduceResolved(state, state.bootstrap().hive().id(),
                HiveSettlementAssaultProcess.resolution(state, assault, SettlementAssaultOutcome.ABORTED));
        return new SettlementAssaultFixture(state, started.instant(), started.schedules(), started.assaultId());
    }

    /**
     * Read-only engineering issue boundary. It explicitly retains one already-admitted detour
     * project and its exact local crew, while four real depot pickaxes remain untouched. A
     * fixture is allowed to establish that canonical precondition, but it must not invent a
     * production cause: a PM-owned baseline loss is now exclusively in-place maintenance.
     * A native visit must materialize the depot and issue one existing tagged pickaxe; this
     * fixture neither starts work nor moves an actor or item.
     */
    static RouteConstructionFixture engineeringEquipmentFixture(WorldId worldId, long seed) {
        return FrontierEngineeringFixtures.engineeringEquipmentFixture(worldId, seed);
    }

    static RouteConstructionFixture engineeringWorksiteFixture(WorldId worldId, long seed) {
        return FrontierEngineeringFixtures.engineeringWorksiteFixture(worldId, seed);
    }

    static SettlementAssaultFixture startedSettlementAssaultFixture(WorldId worldId, long seed) {
        return startedSettlementAssaultFixture(FrontierBootstrapper.create(worldId, seed));
    }

    private static SettlementAssaultFixture startedSettlementAssaultFixture(FrontierBootstrap bootstrap) {
        FrontierWorldState state = FrontierWorldState.initial(bootstrap);
        Settlement settlement = state.bootstrap().settlements().getFirst();
        Bioform scout = state.bootstrap().hive().bioforms().stream().filter(Bioform::isScout).findFirst()
                .orElseThrow(() -> new IllegalStateException("development assault fixture needs one Scout"));
        state = deployFixtureBioform(state, scout.id(), settlement.anchor());
        // Establish an externally mobilised assault party. The production selector correctly
        // excludes cocoon-retained identities; this fixture explicitly owns only its test
        // precondition, rather than relying on an impossible direct COLD movement later.
        for (Bioform bioform : state.bootstrap().hive().bioforms()) {
            if (bioform.isExplosiveAssaulter() || bioform.isDefender() || bioform.isOverseer()) {
                BlockPosition canonicalBodyCell = state.actorLocations().get(bioform.id()).supportingSurface().support();
                // The bootstrap's dormant-hive positions are feet cells; the native graybox
                // exposes their authored floor one cell below.  This fixture establishes the
                // real mobilised precondition on that exact physical floor, rather than asking
                // an approach scene to invent a higher substitute surface at hand-off time.
                state = deployFixtureBioform(state, bioform.id(), canonicalBodyCell.offset(0, -1, 0));
            }
        }
        HiveSettlementKnowledge.Sighting sighting = new HiveSettlementKnowledge.Sighting(settlement.id(), scout.id(), settlement.anchor(), 100L);
        InfectionCell cell = InfectionCell.at(settlement.anchor());
        FixedRatio intensity = new FixedRatio(FixedScalar.ONE);
        StrategicPlanState plans = state.strategicPlans()
                .withHiveSettlementKnowledge(new HiveSettlementKnowledge(java.util.Map.of(settlement.id(), sighting)))
                .withHiveDoctrine(new HiveDoctrineState(HiveDoctrine.INTERDICT, 100L))
                .withHiveTerritoryKnowledge(new HiveTerritoryKnowledge(java.util.Map.of(cell,
                        new HiveTerritoryKnowledge.Belief(cell, intensity, scout.id(), settlement.anchor(), 100L))));
        state = state.withInfection(cell, intensity);
        SubjectId hive = state.bootstrap().hive().id();
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:development-settlement-assault"), hive,
                StrategicObjectiveKind.HIVE_ASSAULT_SETTLEMENT, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:development-settlement-assault"), objective.id(), hive,
                StrategicTaskKind.ASSAULT_SETTLEMENT, Optional.empty(), List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD,
                StrategicTaskRequirement.AVAILABLE_HIVE_BOMBER), List.of(), StrategicTaskStatus.PENDING);
        state = state.withStrategicPlans(plans.addObjective(objective).addTask(task));
        List<ProposedEvent> start = HiveSettlementAssaultProcess.planStart(state, HiveSettlementAssaultProcess.start(task, sighting, 200L));
        for (ProposedEvent event : start) {
            if (event.payload() instanceof StrategicTaskTransition transition) state = StrategicObjectiveProcess.reduceTaskTransition(state, hive, transition);
            if (event.payload() instanceof SettlementAssaultStarted started) state = HiveSettlementAssaultProcess.reduceStarted(state, hive, started);
        }
        List<ScheduledAction> schedules = start.stream().map(ProposedEvent::payload).filter(ScheduleEffect.Created.class::isInstance)
                .map(ScheduleEffect.Created.class::cast).map(ScheduleEffect.Created::action).toList();
        SettlementAssault assault = state.strategicPlans().settlementAssaults().values().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("development assault fixture did not retain its assault"));
        return new SettlementAssaultFixture(state, new SimInstant(200L), schedules, assault.id());
    }

    /**
     * A read-only bootstrap at the first ordinary Northwatch shipment.  Unlike the strike
     * fixture, this begins with the real route at its first hand-off but deliberately removes
     * its already-due COLD progress action.  A native client needs time to connect before it
     * can create the HOT scene; after that scene releases, the production release path creates
     * the normal next COLD action.  This is a test-clock admission detail, not a production
     * route rule.
     */
    static io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection>
            routeCustodyConfiguration(WorldId worldId, long seed) {
        return routeCustodyConfiguration(FrontierV3FixtureCatalog.uncontestedSupplyConfiguration(worldId, seed));
    }

    static io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection>
            routeCustodyConfiguration(io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration) {
        // This fixture isolates route custody/recovery, not competition with population
        // growth. With real production labor, Northwatch's birth review can now consume
        // the exact export surplus before cargo admission. Keep ordinary reserve rules and
        // production timing; exclude only this independent initial workload in the fixture.
        var schedulesWithoutBirth = configuration.initialSchedules().stream()
                .filter(action -> !action.subject().equals(new SubjectId("settlement:1"))
                        || !action.kind().equals("frontier.population.birth.review")).toList();
        configuration = new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(
                configuration.worldId(), configuration.initialState(), configuration.initialInstant(), configuration.commandPlanner(),
                configuration.scheduledPlanner(), configuration.reducer(), configuration.stateCodec(), configuration.projectionMapper(),
                configuration.limits(), schedulesWithoutBirth, configuration.transactionCommitter(), configuration.stateValidator(),
                configuration.executionMetrics(), configuration.kernelQuarantineReporter());
        return configuration;
    }

    static RouteSceneReturnFixture routeSceneReturnFixture(WorldId worldId, long seed) {
        return routeSceneReturnFixture(routeCustodyConfiguration(worldId, seed));
    }

    static RouteSceneReturnFixture routeSceneReturnFixture(
            io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration) {
        var engine = FrontierEngines.create(configuration);
        var codec = new FrontierWorldStateCodec(configuration.initialState().bootstrap());
        io.farfrontier.palemirror.frontier.v3.api.CheckpointImage checkpoint = null;
        FrontierWorldState state = null; RouteOperation operation = null;
        for (long tick = 1L; tick <= 12_000L; tick++) {
            engine.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));
            if (engine.status().kind() != io.farfrontier.palemirror.frontier.v3.api.EngineStatus.Kind.ACTIVE)
                throw new IllegalStateException("route-return fixture stopped at " + engine.checkpoint().instant() + ": "
                        + engine.status().failureDetail().orElse("no failure detail"));
            if (tick % 20L != 0L) continue;
            checkpoint = engine.checkpoint(); state = codec.decode(checkpoint.canonicalState());
            RouteOperation candidate = initialNorthwatchShipment(state).orElse(null);
            if (candidate != null && candidate.stage() == OperationStage.EN_ROUTE && candidate.routeIndex() == 0
                    && candidate.activeTravel().isPresent() && candidate.activeTravel().orElseThrow().cursor() == 0) {
                operation = candidate; break;
            }
        }
        BlockPosition anchor = configuration.initialState().bootstrap().settlements().stream()
                .filter(settlement -> settlement.id().equals(new SubjectId("settlement:1")))
                .findFirst().orElseThrow().anchor();
        BlockPosition start = new BlockPosition(anchor.x() - 6, anchor.y(), anchor.z());
        BlockPosition next = new BlockPosition(anchor.x() - 6, anchor.y(), anchor.z() + 36);
        if (checkpoint == null || state == null || operation == null || !operation.route().getFirst().equals(start) || !operation.route().get(1).equals(next)
                || !operation.participantIds().equals(List.of(new SubjectId("resident:1-30"), new SubjectId("resident:1-16"), new SubjectId("resident:1-28")))) {
            throw new IllegalStateException("development route-return fixture did not retain its exact assembled Northwatch shipment: instant="
                    + (checkpoint == null ? "none" : checkpoint.instant()) + ", operation="
                    + (operation == null ? "absent" : operation.id() + ", route=" + operation.route() + ", participants=" + operation.participantIds()));
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
     * Read-only native-precondition fixture for the two-maintenance fairness boundary.
     *
     * <p>The first project has one exact prepared pickup in the central maintenance chest, but
     * the pilot never loads that chunk. The second project has independently retained cargo and
     * a completed exact crew approach. It deliberately starts with canonical route-loss masks
     * before either target has been materialized: normal first visit must retain provenance for
     * the loss, then admit only the second HOT worksite. The fixture writes neither blocks nor
     * ledger entries and is excluded from the production artifact.</p>
     */
    static RouteMaintenanceFairnessFixture routeMaintenanceColdSourceFairnessFixture(WorldId worldId, long seed) {
        return RouteMaintenanceFairnessScenario.routeMaintenanceColdSourceFairnessFixture(worldId, seed);
    }

    /**
     * Disposable physical-perception fixture.  It changes no operation, cargo or lease: one
     * otherwise ordinary unleased Scout begins beside the exact first cargo anchor, so only a
     * player-loaded HOT caravan can produce the subsequent observation.
     */
    static RouteSceneReturnFixture hotScoutSightingFixture(WorldId worldId, long seed) {
        RouteSceneReturnFixture base = routeSceneReturnFixture(worldId, seed);
        RouteOperation operation = initialNorthwatchShipment(base.state()).orElseThrow();
        Bioform scout = base.state().bootstrap().hive().bioforms().stream().filter(value -> value.id().equals(new SubjectId("bioform:west-1")))
                .filter(Bioform::isScout).findFirst().orElseThrow();
        if (operation == null || operation.activeTravel().isEmpty()) throw new IllegalStateException("hot scout fixture has no active exact cargo route");
        FrontierWorldState state = deployFixtureBioform(base.state(), scout.id(), operation.activeTravel().orElseThrow().cargoAnchor().surface().support());
        // This isolated proof must demonstrate physical HOT perception only.  Retain every
        // ordinary route/actor schedule, but remove the one pre-existing COLD hive-review that
        // could derive knowledge before a player loads the scene.
        List<ScheduledAction> schedules = base.schedules().stream().filter(action -> !(action.kind().equals("frontier.objective.review")
                && action.subject().equals(state.bootstrap().hive().id()))).toList();
        return new RouteSceneReturnFixture(state, base.instant(), schedules);
    }

    /**
     * Disposable end-to-end perception fixture.  The real HOT Scout sighting still creates the
     * intercept; only the otherwise independent guard/bomber approach is shortened so a pilot
     * can observe the ensuing naturally loaded engagement before the disposable world expires.
     * It never pre-creates an objective, task, engagement, lease, knowledge fact or effect.
     */
    static RouteSceneReturnFixture hotScoutInterceptFixture(WorldId worldId, long seed) {
        RouteSceneReturnFixture base = hotScoutSightingFixture(worldId, seed);
        RouteOperation operation = initialNorthwatchShipment(base.state()).orElseThrow();
        if (operation == null || operation.activeTravel().isEmpty()) throw new IllegalStateException("hot scout intercept fixture has no active exact cargo route");
        BlockPosition intercept = operation.activeTravel().orElseThrow().cargoAnchor().surface().support();
        FrontierWorldState state = base.state();
        List<Bioform> attackers = java.util.stream.Stream.concat(
                        state.bootstrap().hive().bioforms().stream(), state.hiveColony().spawnedBioforms().values().stream())
                .filter(bioform -> bioform.isExplosiveAssaulter() || bioform.isDefender())
                .sorted(java.util.Comparator.comparing(Bioform::assignment).thenComparing(Bioform::id)).toList();
        Bioform bomber = attackers.stream().filter(Bioform::isExplosiveAssaulter).findFirst()
                .orElseThrow(() -> new IllegalStateException("hot scout intercept fixture has no bomber"));
        List<Bioform> guards = attackers.stream().filter(Bioform::isDefender).limit(2).toList();
        if (guards.size() != 2) throw new IllegalStateException("hot scout intercept fixture has fewer than two guards");
        // These three exact bodies get distinct nearby starts.  Co-locating every eligible hive
        // attacker would invoke vanilla entity cramming and turn a causal fixture into deaths.
        state = deployFixtureBioform(state, bomber.id(), intercept.offset(-1, 0, 0));
        state = deployFixtureBioform(state, guards.getFirst().id(), intercept.offset(1, 0, 0));
        state = deployFixtureBioform(state, guards.getLast().id(), intercept.offset(0, 0, -1));
        return new RouteSceneReturnFixture(state, base.instant(), base.schedules());
    }

    /**
     * Test-only recovery precondition for a persisted pre-cursor ambient Scout lease.  The
     * fixture owns no body and does not inject an event after startup: an ordinary visit must
     * materialize the exact prepared Scout, and the production observer must durably rebase the
     * obsolete same-floor target before normal patrol can resume.
     */
    static AmbientScoutPatrolFixture hotScoutPatrolRecoveryFixture(WorldId worldId, long seed) {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(worldId, seed));
        Bioform scout = state.bootstrap().hive().bioforms().stream()
                .filter(value -> value.id().equals(new SubjectId("bioform:west-1"))).findFirst()
                .orElseThrow(() -> new IllegalStateException("scout patrol recovery fixture requires west Scout"));
        BlockPosition current = state.actorLocations().get(scout.id()).supportingSurface().support();
        AmbientActorLease generated = AmbientActorProcess.nextLease(state, scout.id(), SimInstant.ZERO);
        AmbientActorLease legacyPrepared = new AmbientActorLease(scout.id(), generated.handoffBody(), generated.handoffInstant(),
                generated.revision(), AmbientLeaseStatus.PREPARED, AmbientGoalKind.SCOUT_PATROL, BodyPosition.above(new SurfaceAnchor(current)));
        state = AmbientLeaseStateProcess.prepare(state, legacyPrepared);
        return new AmbientScoutPatrolFixture(state, SimInstant.ZERO, List.of(), scout.id(), current,
                HiveScoutPatrolProcess.nextPosition(state, scout, current));
    }

    /**
     * Stops at the ordinary cargo-loaded assembly boundary before its first COLD step.  The
     * disposable native pilot must load the port and advance these exact people through normal
     * HOT movement; it cannot use the fixture to start travel or move a resident.
     */
    static OperationAssemblyFixture operationAssemblyFixture(WorldId worldId, long seed) {
        var configuration = routeCustodyConfiguration(worldId, seed);
        var engine = FrontierEngines.createCanonicalStateAccess(configuration);
        for (long tick = 1L; tick <= 12_000L; tick++) {
            engine.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));
            FrontierWorldState state = engine.canonicalState().state();
            RouteOperation operation = initialNorthwatchAssembly(state).orElse(null);
            if (operation == null) continue;
            var checkpoint = engine.checkpoint();
            var schedules = checkpoint.schedules().stream().filter(action -> !action.subject().equals(operation.id())
                    || !action.kind().equals("frontier.operation.assembly")).toList();
            if (schedules.size() == checkpoint.schedules().size()) throw new IllegalStateException("assembly fixture has no pending COLD assembly action");
            return new OperationAssemblyFixture(state, checkpoint.instant(), schedules, operation.id());
        }
        throw new IllegalStateException("development assembly fixture did not reach its exact cargo-loaded boundary by 12000 ticks");
    }

    static Optional<RouteOperation> initialNorthwatchAssembly(FrontierWorldState state) {
        var candidates = state.operations().values().stream()
                .filter(operation -> operation.settlementId().equals(new SubjectId("settlement:1")))
                .filter(operation -> operation.stage() == OperationStage.ASSEMBLING && operation.activeAssembly().isPresent()).toList();
        if (candidates.size() > 1) throw new IllegalStateException("assembly fixture contains competing Northwatch shipments");
        return candidates.stream().findFirst();
    }

    /**
     * Retains the real 12-settlement schedule through the first hive decision, stopping only
     * at the durable exact-biomass physical boundary.  The native pilot must still load the
     * east store and let its ordinary executor consume the real tagged stack.
     */
    static HiveGrowthFixture hiveGrowthFixture(WorldId worldId, long seed) {
        var base = FrontierV3FixtureCatalog.uncontestedSupplyConfiguration(worldId, seed);
        SubjectId store = new SubjectId("container:hive-east-store");
        // This is the durable boundary after a socket has been claimed but before its physical
        // chest write.  The native pilot must load the chunk and let the ordinary container
        // executor complete PREPARED -> ACTIVE before exact consumption can run.
        FrontierWorldState initial = ReferenceContainerCustodyFixtures.observedAndHeld(base.initialState().withInventory(base.initialState().inventory()
                .withSurfaceStatus(store, ContainerSurfaceStatus.PREPARED)), store);
        initial = withHeldFungibleBiomass(initial, store);
        var engine = FrontierEngines.create(new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(base.worldId(), initial,
                base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(),
                base.initialSchedules(), base.transactionCommitter()));
        var codec = new FrontierWorldStateCodec(base.initialState().bootstrap());
        for (long tick = 1L; tick <= 12_000L; tick++) {
            engine.advanceTo(new SimInstant(tick), new WorkBudget(32, 256));
            if (tick % 20L != 0L) continue;
            var checkpoint = engine.checkpoint();
            FrontierWorldState state = codec.decode(checkpoint.canonicalState());
            HiveGrowthJob job = state.hiveColony().growthJobs().get(new SubjectId("job:hive-growth-1"));
            if (job != null && state.physicalIntents().get(job.consumptionIntentId()) != null) {
                return new HiveGrowthFixture(state, checkpoint.instant(), checkpoint.schedules());
            }
        }
        throw new IllegalStateException("development hive-growth fixture did not reach its exact biomass boundary by 12000 ticks");
    }

    /**
     * One physical inter-nest transfer. Both durable STORE surfaces are prepared but absent from
     * Minecraft until the pilot visits them; the exact biomass must never jump between nests.
     */
    static HiveNutrientTransferFixture hiveNutrientTransferFixture(WorldId worldId, long seed) {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(worldId, seed));
        SubjectId hive = state.bootstrap().hive().id(); SubjectId eastStore = new SubjectId("container:hive-east-store"); SubjectId westStore = new SubjectId("container:hive-west-store");
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:development-hive-nutrient-transfer"), hive,
                StrategicObjectiveKind.HIVE_GROW_ORGANISM, Optional.empty(), 2, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:development-hive-nutrient-transfer"), objective.id(), hive,
                StrategicTaskKind.GROW_HIVE_ORGANISM, Optional.empty(), List.of(StrategicTaskRequirement.EXACT_HIVE_BIOMASS), List.of(), StrategicTaskStatus.PENDING);
        state = ReferenceContainerCustodyFixtures.observedAndHeld(state.withStrategicPlans(state.strategicPlans().addObjective(objective).addTask(task)).withInventory(state.inventory()
                .withSurfaceStatus(eastStore, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(westStore, ContainerSurfaceStatus.PREPARED)), eastStore);
        state = withHeldFungibleBiomass(state, eastStore);
        return new HiveNutrientTransferFixture(state, SimInstant.ZERO, List.of(HiveGrowthProcess.start(task, 1L)),
                new SubjectId("transfer:hive-nutrient-task-development-hive-nutrient-transfer"));
    }

    /**
     * Read-only starting condition for one real settlement assessment.  The fixture does not
     * pre-write a disease result: the ordinary objective review must still emit the exact
     * exposure and quarantine transition after the server starts.
     */
    static HealthQuarantineFixture healthQuarantineFixture(WorldId worldId, long seed) {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(worldId, seed);
        FrontierWorldState state = FrontierWorldState.initial(bootstrap);
        // This fixture isolates ordinary infection/quarantine causality; food production has its
        // own native profile and must not win the settlement's first review here.
        SubjectId wheatAccount = new SubjectId("custody:container-1-depot"); SubjectId wheatLot = new SubjectId("lot:bootstrap-1-wheat");
        state = state.withInventory(state.inventory().withFungibleResources(state.inventory().fungibleResources()
                .destroy(wheatAccount, Map.of(wheatLot, 64), Map.of())));
        Settlement settlement = bootstrap.settlements().getFirst();
        SettlementStructure infirmary = settlement.structures().stream().filter(value -> value.kind() == StructureKind.INFIRMARY)
                .findFirst().orElseThrow(() -> new IllegalStateException("health fixture needs one infirmary"));
        // Health contact is defined against the structure's semantic foundation, not its
        // decorative projection (signs and trim may extend beyond that foundation).
        InfectionCell contact = InfectionCell.at(infirmary.anchor());
        state = state.withInfection(contact, new FixedRatio(new FixedScalar(FixedScalar.SCALE)));
        return new HealthQuarantineFixture(state, SimInstant.ZERO, List.of(StrategicObjectiveProcess.review(settlement.id(), 1, 1L)), settlement.id(), contact);
    }

    private static FrontierWorldState withHeldFungibleBiomass(FrontierWorldState state, SubjectId store) {
        CustodyAccount account = FungibleResourceCustodySupport.accountAtContainer(state, store).orElseThrow();
        SubjectId biomass = new SubjectId("lot:bootstrap-hive-biomass");
        FungibleResourceLedger resources = state.inventory().fungibleResources();
        FungiblePhysicalObservation.Stack stack = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                new InventoryCustody.ContainerSlot(store, 0)), resources.lots().get(biomass).itemKind(), 64);
        resources = resources.rebind(account.id(), 1L, FungiblePhysicalObservation.bind(resources, account.id(), 1L, List.of(stack)));
        return state.withInventory(state.inventory().withFungibleResources(resources));
    }

    /**
     * Read-only HOT-treatment boundary. One already infected resident, one real local medic and
     * one exact honey bottle are retained canonically, but neither a medical operation, scene
     * lease nor physical chest/body is pre-created. An ordinary visit must first materialize
     * the depot; the retained future review can then admit care, assemble the same people and
     * produce the exact consumption/recovery receipt.
     */
    static MedicalTreatmentFixture medicalTreatmentFixture(WorldId worldId, long seed) {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(worldId, seed));
        Settlement settlement = state.bootstrap().settlements().getFirst(); SubjectId depot = FrontierWorldState.depotId(settlement.id());
        HumanPopulation population = state.humanPopulation();
        SubjectId patient = settlement.residents().stream().map(Resident::id).sorted()
                .filter(id -> population.resident(id).profession() != ResidentProfession.MEDICAL_WORKER).findFirst()
                .orElseThrow(() -> new IllegalStateException("medical fixture needs one non-medic patient"));
        SubjectId supply = new SubjectId("item:development-medical-remedy");
        ExactInventory inventory = state.inventory().store(new ExactItemStack(supply, settlement.id(), MedicalEvacuationStateSupport.FIRST_TREATMENT_SUPPLY, 1,
                        new InventoryCustody.ContainerSlot(depot, 1)));
        state = state.withInventory(inventory).withHumanPopulation(state.humanPopulation()
                .transitionHealth(patient, ResidentHealthStatus.EXPOSED, 1L)
                .transitionHealth(patient, ResidentHealthStatus.INFECTED, 2L));
        return new MedicalTreatmentFixture(state, SimInstant.ZERO, List.of(StrategicObjectiveProcess.review(settlement.id(), 1, 1_000L)));
    }

    /**
     * Disposable service-work fixture for the actual medic-held decontamination path. The
     * fixture intentionally does not make the depot surface active: an ordinary player visit is
     * the only way to materialize it, after which the retained normal scan admits the service.
     */
    static ServiceDecontaminationFixture serviceDecontaminationFixture(WorldId worldId, long seed) {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(worldId, seed);
        FrontierWorldState state = FrontierWorldState.initial(bootstrap);
        Settlement settlement = bootstrap.settlements().stream().filter(value -> value.id().value().equals("settlement:9"))
                .findFirst().orElseThrow(() -> new IllegalStateException("service fixture needs settlement:9"));
        SettlementStructure infirmary = settlement.structures().stream().filter(value -> value.kind() == StructureKind.INFIRMARY)
                .findFirst().orElseThrow(() -> new IllegalStateException("service fixture needs one infirmary"));
        InfectionCell cell = treatmentCell(bootstrap, infirmary);
        SubjectId reagent = new SubjectId("item:development-service-decontamination-reagent");
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        state = state.withInfection(cell, new FixedRatio(new FixedScalar(750_000L)))
                .withInventory(state.inventory().store(new ExactItemStack(reagent, settlement.id(), DecontaminationPolicy.REAGENT, 1,
                        new InventoryCustody.ContainerSlot(depot, 1))));
        String suffix = settlement.id().value().replace(':', '-');
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:development-" + suffix + "-decontamination"), settlement.id(),
                StrategicObjectiveKind.SETTLEMENT_CONTAIN_LOCAL_INFECTION, Optional.of(cell), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:development-" + suffix + "-decontamination"), objective.id(), settlement.id(),
                StrategicTaskKind.DECONTAMINATE_INFECTION_CELL, Optional.of(cell), List.of(StrategicTaskRequirement.ACTIVE_INFIRMARY,
                StrategicTaskRequirement.EXACT_DECONTAMINATION_REAGENT), List.of(), StrategicTaskStatus.PENDING);
        state = state.withStrategicPlans(state.strategicPlans().addObjective(objective).addTask(task));
        // The player has ample time to enter the ordinary settlement before the first scan. A
        // failed early scan would correctly block this exact task, which is not the condition
        // this fixture is intended to exercise.
        return new ServiceDecontaminationFixture(state, SimInstant.ZERO, List.of(SettlementServiceWorkProcess.scan(1, 1_000L)),
                settlement.id(), cell, reagent);
    }

    private static InfectionCell treatmentCell(FrontierBootstrap bootstrap, SettlementStructure infirmary) {
        for (int radius = 4; radius <= 32; radius += 4) for (int dx = -radius; dx <= radius; dx += 4) {
            for (int dz = -radius; dz <= radius; dz += 4) {
                if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                InfectionCell cell = InfectionCell.at(infirmary.anchor().offset(dx, 0, dz));
                try {
                    if (!InfectionTreatmentWorksite.candidates(bootstrap, cell).isEmpty()) return cell;
                } catch (IllegalArgumentException ignored) {
                    // The same bounded worksite compiler remains the authority for eligibility.
                }
            }
        }
        throw new IllegalStateException("service fixture has no treatment cell near its infirmary");
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

    /**
     * Disposable player-causality fixture: the exact Northwatch wheat is already committed to
     * one job whose input becomes materialized through the normal owned-container lifecycle,
     * but no transform intent exists.  The only valid way through the scenario is an ordinary
     * player withdrawal followed by the production cancellation boundary; the fixture itself
     * grants neither an item nor a canonical mutation API.
     */
    static MaterializedProductionFixture materializedProductionInputTheftFixture(WorldId worldId, long seed) {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(worldId, seed));
        state = withLegacyMaterializedWheat(state);
        SubjectId settlementId = new SubjectId("settlement:1");
        for (ProposedEvent event : OptionalCompanyEmploymentFixture.foundation(state, settlementId, 4_000L)) {
            if (event.payload() instanceof CompanyRegistered registered) state = CompanyFoundationProcess.reduce(state, settlementId, registered);
            if (event.payload() instanceof EmploymentContractOpened opened) state = SettlementEmploymentProcess.reduceEmployment(state, settlementId, opened);
        }
        Settlement settlement = state.bootstrap().settlements().getFirst();
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:development-production-input-theft"), settlementId,
                StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:development-production-input-theft"), objective.id(), settlementId,
                StrategicTaskKind.PRODUCE_BREAD, Optional.empty(), List.of(StrategicTaskRequirement.ACTIVE_WORKSHOP, StrategicTaskRequirement.EXACT_WHEAT_INPUT),
                List.of(), StrategicTaskStatus.ACTIVE);
        state = state.withStrategicPlans(state.strategicPlans().addObjective(objective).addTask(task));
        SubjectId worker = SettlementWorkPolicy.permissions(state, settlementId).workers(ResidentWorkKind.BAKING)
                .stream().sorted().findFirst().orElseThrow();
        SubjectId company = CompanyFoundationProcess.companyId(settlementId);
        ExactItemStack input = state.inventory().items().get(new SubjectId("item:bootstrap-1-wheat"));
        SubjectId jobId = new SubjectId("job:production-development-input-theft");
        SettlementStructure workshop = settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.WORKSHOP).findFirst().orElseThrow();
        ProductionJob job = new ProductionJob(jobId, task.id(), settlementId, workshop.id(), worker, input.id(), new ProductionInputHold.Materialized(input.id()),
                new SubjectId("item:development-production-input-theft-bread"), "minecraft:bread", input.count(), ProductionWorkProgress.notStarted(),
                ProductionWorkTraversal.compile(state.bootstrap(), workshop, state.actorLocations().get(worker), jobId), 0)
                .withRights(OptionalCompanyEmploymentFixture.publicCompanyService(state, settlementId));
        state = ProductionCommercialProcess.reserve(state.withProductionJob(job), job);
        EmploymentContract contract = ProductionCommercialProcess.contractFor(state, job).orElseThrow();
        FinancialReservation reservation = ProductionCommercialProcess.reservation(job, contract);
        MarketDemand demand = new MarketDemand(new SubjectId("demand:development-production-input-theft"), settlementId, task.id(), "minecraft:bread", input.count(),
                FixedScalar.whole(2L), 0L, 1_000L, MarketDemandStatus.OPEN);
        CompanyQuote quote = new CompanyQuote(new SubjectId("quote:development-production-input-theft"), demand.id(), company, input.count(),
                contract.invoicePerCompletedJob(), 0L, 1_000L);
        MarketWorkOrder order = new MarketWorkOrder(new SubjectId("order:development-production-input-theft"), demand.id(), quote.id(), company, task.id(), job.id(),
                reservation.id(), quote.totalPrice(), MarketWorkOrderStatus.ACCEPTED);
        state = state.withCompanies(state.companies().withMarket(MarketOrderBook.empty().open(demand).publish(quote, 0L).accept(order, 0L)));
        return new MaterializedProductionFixture(state, SimInstant.ZERO, List.of(), order.id());
    }

    /**
     * This fixture isolates the retained exact-materialization recovery branch. Production
     * bootstrap wheat itself is fungible; the fixture replaces only its local test input so
     * player-theft recovery remains exercised without reintroducing a production bootstrap
     * stack identity.
     */
    private static FrontierWorldState withLegacyMaterializedWheat(FrontierWorldState state) {
        SubjectId account = new SubjectId("custody:container-1-depot");
        SubjectId lot = new SubjectId("lot:bootstrap-1-wheat");
        SubjectId item = new SubjectId("item:bootstrap-1-wheat");
        SubjectId depot = FrontierWorldState.depotId(new SubjectId("settlement:1"));
        FungibleResourceLedger resources = state.inventory().fungibleResources().destroy(account, Map.of(lot, 64), Map.of());
        ExactItemStack wheat = new ExactItemStack(item, new SubjectId("settlement:1"), "minecraft:wheat", 64,
                new InventoryCustody.ContainerSlot(depot, 0));
        return state.withInventory(state.inventory().withFungibleResources(resources).store(wheat));
    }

    /**
     * Disposable player-combat fixture for the irreversible-worker boundary.  The exact worker
     * starts at the real workshop exterior port.  The pilot attacks only within its ordinary
     * melee range, so the fixture needs no fabricated distant worker position or second
     * movement/materialization authority.
     */
    static MaterializedProductionFixture materializedProductionWorkFixture(WorldId worldId, long seed) {
        MaterializedProductionFixture base = materializedProductionInputTheftFixture(worldId, seed);
        SubjectId jobId = new SubjectId("job:production-development-input-theft");
        ProductionJob job = base.state().productionJobs().get(jobId);
        SubjectId worker = job.workerId();
        SettlementStructure workshop = FrontierWorldStateSupport.settlement(base.state().bootstrap(), job.settlementId()).structures().stream()
                .filter(structure -> structure.id().equals(job.facilityId())).findFirst().orElseThrow();
        SurfaceAnchor exterior = SettlementWorkshopServicePort.forWorkshop(workshop).exteriorApproach();
        FrontierWorldState isolated = base.state().withActorBody(worker, BodyPosition.above(exterior));
        var jobs = new java.util.LinkedHashMap<>(isolated.productionJobs());
        jobs.put(jobId, job.withWorkTraversal(ProductionWorkTraversal.compile(isolated.bootstrap(), workshop, isolated.actorLocations().get(worker), jobId), 0));
        isolated = isolated.withChanges(FrontierWorldStateUpdate.begin().productionJobs(jobs));
        // This is the normal scheduled completion boundary.  The materialized executor may
        // defer it while the exact worker is still approaching/processing, but it must not rely
        // on a test-only direct transformation once OUTPUT_READY is observed.
        return new MaterializedProductionFixture(isolated, base.instant(),
                List.of(ProductionProcess.complete(jobs.get(jobId), 100L)), base.orderId());
    }

    /**
     * The production obstruction remains local to Northwatch. A second, unvisited settlement
     * retains an ordinary exact-resident need review at a later canonical instant, proving that the
     * obstruction neither stalls nor borrows capacity from an unrelated COLD process.
     */
    static MaterializedProductionFixture materializedProductionObstructionLivenessFixture(WorldId worldId, long seed) {
        MaterializedProductionFixture base = materializedProductionWorkFixture(worldId, seed);
        FrontierWorldState state = base.state(); Settlement settlement = state.bootstrap().settlements().get(1);
        SubjectId resident = settlement.residents().getFirst().id();
        HumanPopulation population = state.humanPopulation();
        Map<SubjectId, ResidentNutrition> nutrition = new LinkedHashMap<>(population.nutrition());
        long unit = Math.multiplyExact(state.bootstrap().ruleset().residentLife().satietyUnitTicks(), 1_000L);
        int rate = population.resident(resident).characteristics().effectiveMetabolismPermille(0L);
        long depletion = Math.multiplyExact(1_000L, rate);
        long units = Math.ceilDiv(depletion, unit);
        nutrition.put(resident, new ResidentNutrition(ResidentNutritionStatus.NOURISHED,
                Math.toIntExact(state.bootstrap().ruleset().residentLife().eatBelowUnits() + units - 1), 0L,
                Math.subtractExact(Math.multiplyExact(units, unit), depletion)));
        state = state.withHumanPopulation(new HumanPopulation(population.households(), population.residents(),
                population.birthJobs(), population.health(), population.quarantines(), population.migrations(),
                population.provisions(), nutrition, population.medicalOperations(), population.schedules(), population.meals(), population.mealResourceObligations()));
        List<ScheduledAction> schedules = new java.util.ArrayList<>();
        schedules.add(ResidentNeedProcess.review(resident, 1_000L));
        return new MaterializedProductionFixture(state, base.instant(), schedules, base.orderId());
    }

    /** Worker-death uses the normal production-work fixture; only the pilot action is fatal. */
    static MaterializedProductionFixture materializedProductionWorkerDeathFixture(WorldId worldId, long seed) {
        return materializedProductionWorkFixture(worldId, seed);
    }

    /**
     * Read-only native-pilot precondition for the cocoon-release boundary.  The fixture performs
     * only canonical task selection; a visiting ordinary player must still demand every real
     * block removal, exact ambient lease and visible body.
     */
    static HiveMobilizationFixture hiveMobilizationFixture(WorldId worldId, long seed) {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(worldId, seed));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        FrontierWorldState initial = state;
        Bioform scout = initial.bootstrap().hive().bioforms().stream().filter(Bioform::isScout)
                .filter(value -> initial.hiveColony().bioformLifecycles().get(value.id()).phase().permitsAmbientBody())
                .findFirst().orElseThrow();
        HiveSettlementKnowledge.Sighting sighting = new HiveSettlementKnowledge.Sighting(settlement.id(), scout.id(), settlement.anchor(), 100L);
        InfectionCell cell = InfectionCell.at(settlement.anchor());
        state = state.withInfection(cell, new FixedRatio(FixedScalar.ONE));
        SubjectId hive = state.bootstrap().hive().id();
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:development-hive-mobilization"), hive,
                StrategicObjectiveKind.HIVE_ASSAULT_SETTLEMENT, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:development-hive-mobilization"), objective.id(), hive,
                StrategicTaskKind.ASSAULT_SETTLEMENT, Optional.empty(), List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD,
                StrategicTaskRequirement.AVAILABLE_HIVE_BOMBER), List.of(), StrategicTaskStatus.PENDING);
        StrategicPlanState plans = state.strategicPlans()
                .withHiveSettlementKnowledge(new HiveSettlementKnowledge(Map.of(settlement.id(), sighting)))
                .withHiveTerritoryKnowledge(new HiveTerritoryKnowledge(Map.of(cell,
                        new HiveTerritoryKnowledge.Belief(cell, new FixedRatio(FixedScalar.ONE), scout.id(), settlement.anchor(), 100L))))
                .withHiveDoctrine(new HiveDoctrineState(HiveDoctrine.INTERDICT, 100L))
                .addObjective(objective).addTask(task);
        state = state.withStrategicPlans(plans);
        HiveMobilization mobilization = HiveMobilizationProcess.forSettlementAssault(state, task, sighting, 200L).orElseThrow();
        state = StrategicObjectiveProcess.reduceTaskTransition(state, hive, new StrategicTaskTransition(task.id(), StrategicTaskStatus.ACTIVE));
        state = HiveMobilizationProcess.reduceStarted(state, hive, new HiveMobilizationStarted(mobilization));
        return new HiveMobilizationFixture(state, new SimInstant(200L), List.of(), mobilization.id(), mobilization.memberIds());
    }

    /**
     * Exact post-contact precondition for the native survivor-return carrier.  It uses the same
     * reducers as a completed expedition: no fixture body, lease, movement, or return cursor is
     * fabricated.  The ordinary player must still load and observe the retained next edge.
     */
    static HiveMobilizationFixture hiveReturnFixture(WorldId worldId, long seed) {
        HiveMobilizationFixture base = hiveMobilizationFixture(worldId, seed);
        FrontierWorldState state = base.state(); SubjectId hive = state.bootstrap().hive().id();
        for (SubjectId member : base.memberIds()) {
            HiveMobilization current = state.hiveColony().mobilizations().get(base.mobilizationId());
            state = HiveMobilizationProcess.reduceReleaseStarted(state, hive, new HiveMobilizationReleaseStarted(current.id()));
            state = HiveMobilizationProcess.reduceCocoonReleased(state, hive, new HiveMobilizationCocoonReleased(current.id(), member, HiveAssemblyExecutionAuthority.admission(state, current.id(), member)));
        }
        SettlementAssault assault = null;
        for (int step = 0; step < 256; step++) {
            HiveMobilization current = state.hiveColony().mobilizations().get(base.mobilizationId());
            List<ProposedEvent> planned = HiveMobilizationProcess.planAssemblyProgress(state,
                    HiveMobilizationProcess.assemblyProgress(current.id(), 300L + step * 20L));
            HiveMobilizationAssemblyAdvanced advanced = (HiveMobilizationAssemblyAdvanced) planned.getFirst().payload();
            state = HiveMobilizationProcess.reduceAssemblyAdvanced(state, hive, advanced);
            for (ProposedEvent event : planned) if (event.payload() instanceof HiveMobilizationDeparted departed) {
                assault = departed.assault(); state = HiveMobilizationProcess.reduceDeparted(state, hive, departed); break;
            }
            if (assault != null) break;
        }
        if (assault == null) throw new IllegalStateException("development return fixture did not reach exact departure");
        state = HiveSettlementAssaultProcess.reduceResolved(state, hive, HiveSettlementAssaultProcess.resolution(state, assault, SettlementAssaultOutcome.ABORTED));
        HiveMobilization returning = state.hiveColony().mobilizations().get(base.mobilizationId());
        if (returning.status() != HiveMobilizationStatus.RETURNING) throw new IllegalStateException("development return fixture lacks retained survivors");
        return new HiveMobilizationFixture(state, new SimInstant(900L), List.of(), base.mobilizationId(), base.memberIds());
    }

    /**
     * Test fixtures may establish a real external-operation precondition, but they must make
     * the lifecycle transition explicit.  Directly moving a cocoon-retained identity would
     * create an impossible canonical state and conceal the production wake boundary.
     */
    private static FrontierWorldState deployFixtureBioform(FrontierWorldState state, SubjectId bioformId, BlockPosition surface) {
        BioformLifecycle lifecycle = state.hiveColony().bioformLifecycles().get(bioformId);
        if (lifecycle == null) throw new IllegalArgumentException("fixture deployment requires a canonical bioform: " + bioformId.value());
        if (lifecycle.phase().occupiesCocoon()) {
            Map<SubjectId, BioformLifecycle> lifecycles = new LinkedHashMap<>(state.hiveColony().bioformLifecycles());
            lifecycles.put(bioformId, lifecycle.waking().active());
            state = state.withChanges(FrontierWorldStateUpdate.begin().hiveColony(state.hiveColony().withBioformLifecycles(lifecycles)));
        }
        return state.withActorBody(bioformId, BodyPosition.above(new SurfaceAnchor(surface)));
    }

    record HiveGrowthFixture(FrontierWorldState state, SimInstant instant, List<ScheduledAction> schedules) {
        HiveGrowthFixture { schedules = List.copyOf(schedules); }
    }

    record HiveNutrientTransferFixture(FrontierWorldState state, SimInstant instant, List<ScheduledAction> schedules, SubjectId transferId) {
        HiveNutrientTransferFixture { schedules = List.copyOf(schedules); }
    }

    /** Test-only admission query; the selected operation retains its actual canonical identity. */
    static Optional<RouteOperation> initialNorthwatchShipment(FrontierWorldState state) {
        return state.operations().values().stream()
                .filter(operation -> operation.settlementId().equals(new SubjectId("settlement:1")))
                .filter(operation -> operation.stage() == OperationStage.EN_ROUTE && operation.routeIndex() == 0)
                .filter(operation -> operation.activeTravel().isPresent() && operation.activeTravel().orElseThrow().cursor() == 0)
                .reduce((left, right) -> { throw new IllegalStateException("route fixture has ambiguous initial Northwatch shipments"); });
    }

    record RouteSceneReturnFixture(FrontierWorldState state, SimInstant instant, List<ScheduledAction> schedules) {
        RouteSceneReturnFixture { schedules = List.copyOf(schedules); }
    }

    record RoutePatrolFixture(FrontierWorldState state, SimInstant instant, List<ScheduledAction> schedules,
                              SubjectId taskId, RoutePatrol patrol) {
        RoutePatrolFixture { schedules = List.copyOf(schedules); }
    }

    record RouteMaintenanceFairnessFixture(FrontierWorldState state, SimInstant instant, List<ScheduledAction> schedules,
                                           SubjectId coldSourceMaintenanceId, SubjectId loadedRepairMaintenanceId) {
        RouteMaintenanceFairnessFixture { schedules = List.copyOf(schedules); }
    }

    record AmbientScoutPatrolFixture(FrontierWorldState state, SimInstant instant, List<ScheduledAction> schedules,
                                     SubjectId scoutId, BlockPosition priorPosition, BlockPosition nextGoalPosition) {
        AmbientScoutPatrolFixture { schedules = List.copyOf(schedules); }
    }

    record SettlementAssaultFixture(FrontierWorldState state, SimInstant instant, List<ScheduledAction> schedules, SubjectId assaultId) {
        SettlementAssaultFixture { schedules = List.copyOf(schedules); }
    }
    record MultiFrontPressureFixture(FrontierWorldState state, SimInstant instant, List<ScheduledAction> schedules,
                                     SubjectId assaultId, SubjectId harvestJobId) {
        MultiFrontPressureFixture { schedules = List.copyOf(schedules); }
    }

    record RouteConstructionFixture(FrontierWorldState state, SimInstant instant, List<ScheduledAction> schedules, SubjectId projectId) {
        RouteConstructionFixture { schedules = List.copyOf(schedules); }
    }

    record OperationAssemblyFixture(FrontierWorldState state, SimInstant instant, List<ScheduledAction> schedules, SubjectId operationId) {
        OperationAssemblyFixture { schedules = List.copyOf(schedules); }
    }

    record HealthQuarantineFixture(FrontierWorldState state, SimInstant instant, List<ScheduledAction> schedules,
                                   SubjectId settlementId, InfectionCell contact) {
        HealthQuarantineFixture { schedules = List.copyOf(schedules); }
    }

    record MedicalTreatmentFixture(FrontierWorldState state, SimInstant instant, List<ScheduledAction> schedules) {
        MedicalTreatmentFixture { schedules = List.copyOf(schedules); }
    }

    record ServiceDecontaminationFixture(FrontierWorldState state, SimInstant instant, List<ScheduledAction> schedules,
                                         SubjectId settlementId, InfectionCell cell, SubjectId reagentId) {
        ServiceDecontaminationFixture { schedules = List.copyOf(schedules); }
    }

    record ResidentTransitFixture(FrontierWorldState state, SimInstant instant, List<ScheduledAction> schedules,
                                  ResidentMigrationJourney journey) {
        ResidentTransitFixture { schedules = List.copyOf(schedules); }
    }

    record MaterializedProductionFixture(FrontierWorldState state, SimInstant instant, List<ScheduledAction> schedules, SubjectId orderId) {
        MaterializedProductionFixture { schedules = List.copyOf(schedules); }
    }

    record HiveMobilizationFixture(FrontierWorldState state, SimInstant instant, List<ScheduledAction> schedules,
                                   SubjectId mobilizationId, List<SubjectId> memberIds) {
        HiveMobilizationFixture {
            schedules = List.copyOf(schedules); memberIds = List.copyOf(memberIds);
        }
    }
}
