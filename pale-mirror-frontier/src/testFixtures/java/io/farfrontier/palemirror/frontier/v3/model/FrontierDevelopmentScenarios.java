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
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.uncontestedSupplyConfiguration(worldId, seed));
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
        BlockPosition intercept = operation.activeTravel().orElseThrow().cargoAnchor().surface().support();
        for (Bioform bioform : state.bootstrap().hive().bioforms()) {
            if (bioform.isDefender() || bioform.isExplosiveAssaulter()) state = state.withActorBody(bioform.id(), BodyPosition.above(new SurfaceAnchor(intercept)));
        }
        SubjectId hive = state.bootstrap().hive().id();
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:development-hot-strike"), hive,
                StrategicObjectiveKind.HIVE_INTERCEPT_ROUTE_OPERATION, Optional.empty(), 99, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:development-hot-strike"), objective.id(), hive,
                StrategicTaskKind.INTERCEPT_ROUTE_OPERATION, Optional.empty(), Optional.of(operation.id()),
                Optional.empty(), List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD), List.of(), StrategicTaskStatus.PENDING, Optional.of(intercept));
        state = state.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task));
        Bioform scout = state.bootstrap().hive().bioforms().stream().filter(Bioform::isScout).findFirst().orElseThrow();
        state = state.withActorBody(scout.id(), BodyPosition.above(new SurfaceAnchor(intercept)));
        HiveOperationKnowledge.Sighting sighting = new HiveOperationKnowledge.Sighting(operation.id(), scout.id(), intercept, 2_600L);
        state = state.withStrategicPlans(state.strategicPlans().withHiveOperationKnowledge(state.strategicPlans().hiveOperationKnowledge().observe(sighting)));
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
     * Disposable-only real assault boundary. The fixture advances the normal bounded Scout
     * sighting and attacker approach process to COLD_COMBAT, but creates neither a scene lease
     * nor a Minecraft body: a naturally visiting player must admit the typed HOT scene.
     */
    static SettlementAssaultFixture settlementAssaultFixture(WorldId worldId, long seed) {
        SettlementAssaultFixture started = startedSettlementAssaultFixture(worldId, seed);
        FrontierWorldState state = started.state();
        ScheduledAction next = started.schedules().stream().filter(action -> action.kind().equals("frontier.settlement_assault.progress")).findFirst()
                .orElseThrow(() -> new IllegalStateException("development assault fixture did not schedule approach"));
        SubjectId hive = state.bootstrap().hive().id();
        for (int step = 0; step < 256; step++) {
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
                new SettlementAssaultResolved(assault.id(), SettlementAssaultOutcome.ABORTED));
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
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(worldId, seed));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        RouteConstruction project = fixtureDetourProject(state, settlement);
        state = RouteConstructionStateSupport.reduceStarted(state, FrontierRouteNetwork.OWNER, new RouteConstructionStarted(project));
        return new RouteConstructionFixture(state, new SimInstant(200L), List.of(RouteConstructionProcess.scan(1, 200L)), project.id());
    }

    private static RouteConstruction fixtureDetourProject(FrontierWorldState state, Settlement settlement) {
        List<BlockPosition> baseline = state.routeTopology().supplyWaypoints(state.bootstrap(), settlement.id());
        BlockPosition origin = baseline.getFirst(), destination = baseline.getLast();
        BlockPosition egress = origin.offset(-36, 0, 0), lane = egress.offset(0, 0, 60);
        List<BlockPosition> detour = List.of(origin, egress, lane, new BlockPosition(-300, lane.y(), lane.z()),
                new BlockPosition(-300, destination.y(), destination.z()), destination);
        // This identity is the fixture's stable subject referenced by checked-in scenario and
        // GameTest evidence. It no longer encodes a physical-loss cause.
        SubjectId projectId = new SubjectId("construction:route-reroute-" + settlement.id().value().replace(':', '-') + "--366-64--304");
        List<SubjectId> crew = state.humanPopulation().residents().values().stream()
                .filter(resident -> resident.settlementId().equals(settlement.id()))
                .filter(resident -> resident.capability(HumanCapability.ENGINEERING) > 0)
                .sorted(Comparator.comparing(ResidentProfile::id)).limit(EngineeringRecoveryTeam.MIN_MEMBERS).map(ResidentProfile::id).toList();
        if (crew.isEmpty()) throw new IllegalStateException("engineering fixture needs one exact engineer");
        List<BlockPosition> workCells = FrontierRouteNetwork.constructionCells(state.bootstrap(), state.routeTopology(), settlement.id(), detour);
        if (workCells.isEmpty()) throw new IllegalStateException("engineering fixture needs a non-empty immutable detour work plan");
        return new RouteConstruction(projectId, settlement.id(), detour, workCells, 0, RouteConstructionStatus.BUILDING,
                Optional.empty(), Optional.of(EngineeringRecoveryTeam.forWorkOrder(projectId, settlement.id(), crew)), Optional.empty());
    }

    /**
     * Test-only postcondition fixture for the physical work boundary. It retains the ordinary
     * project/team identities, moves only their already-issued exact tools and creates the same
     * one-unit cargo split used by the production receipt path. Minecraft remains untouched
     * until an ordinary player visit admits the HOT work-site scene.
     */
    static RouteConstructionFixture engineeringWorksiteFixture(WorldId worldId, long seed) {
        RouteConstructionFixture base = engineeringEquipmentFixture(worldId, seed);
        FrontierWorldState state = base.state();
        RouteConstruction project = state.routeConstructions().get(base.projectId());
        EngineeringRecoveryTeam team = project.team().orElseThrow();
        ExactInventory inventory = state.inventory();
        for (SubjectId member : team.memberIds()) {
            ExactItemStack tool = inventory.items().values().stream().filter(item -> item.economicOwnerId().equals(project.settlementId()))
                    .filter(item -> EngineeringToolCustody.isTool(item.itemKind())).filter(item -> item.custody() instanceof InventoryCustody.ContainerSlot)
                    .findFirst().orElseThrow(() -> new IllegalStateException("engineering worksite fixture needs one exact depot tool per member"));
            inventory = inventory.moveObservedItem(tool.id(), tool.custody(), new InventoryCustody.Actor(member));
        }
        SubjectId maintenance = FrontierRouteNetwork.MAINTENANCE_CONTAINER;
        SubjectId source = new SubjectId("item:fixture-engineering-worksite-concrete");
        inventory = inventory.withSurfaceStatus(maintenance, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(maintenance, ContainerSurfaceStatus.ACTIVE)
                .store(new ExactItemStack(source, FrontierRouteNetwork.OWNER, "minecraft:gray_concrete", 1,
                        new InventoryCustody.ContainerSlot(maintenance, inventory.firstFreeSlot(maintenance).orElseThrow())));
        CargoBatch cargo = new CargoBatch(project.plannedCargoId(), FrontierRouteNetwork.OWNER, List.of(project.plannedCargoItemId()));
        inventory = inventory.extractOneToCargo(source, cargo, project.plannedCargoItemId());
        EngineeringWorkAssembly compiled = EngineeringWorksite.compile(state, project);
        java.util.Map<SubjectId, EngineeringWorkAssembly.Member> completed = new java.util.LinkedHashMap<>();
        java.util.Map<SubjectId, ActorLocation> locations = new java.util.LinkedHashMap<>(state.actorLocations());
        compiled.members().forEach((member, approach) -> {
            EngineeringWorkAssembly.Member arrived = new EngineeringWorkAssembly.Member(approach.corridor(), approach.corridor().size() - 1);
            completed.put(member, arrived);
            locations.put(member, new ActorLocation(BodyPosition.above(new SurfaceAnchor(arrived.currentPosition())), locations.get(member).condition()));
        });
        RouteConstruction ready = new RouteConstruction(project.id(), project.settlementId(), project.waypoints(), project.workCells(), project.confirmedCells(),
                project.status(), java.util.Optional.of(cargo.id()), project.team(), java.util.Optional.of(new EngineeringWorkAssembly(EngineeringJourneyPurpose.WORKSITE, completed)));
        java.util.Map<SubjectId, RouteConstruction> projects = new java.util.LinkedHashMap<>(state.routeConstructions()); projects.put(ready.id(), ready);
        state = state.withChanges(FrontierWorldStateUpdate.begin().inventory(inventory).actorLocations(locations).routeConstructions(projects));
        return new RouteConstructionFixture(state, base.instant(), List.of(), ready.id());
    }

    private static SettlementAssaultFixture startedSettlementAssaultFixture(WorldId worldId, long seed) {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(worldId, seed));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        Bioform scout = state.bootstrap().hive().bioforms().stream().filter(Bioform::isScout).findFirst()
                .orElseThrow(() -> new IllegalStateException("development assault fixture needs one Scout"));
        state = state.withActorBody(scout.id(), BodyPosition.above(new SurfaceAnchor(settlement.anchor())));
        HiveSettlementKnowledge.Sighting sighting = new HiveSettlementKnowledge.Sighting(settlement.id(), scout.id(), settlement.anchor(), 100L);
        InfectionCell cell = InfectionCell.at(settlement.anchor());
        FixedRatio intensity = new FixedRatio(FixedScalar.ONE);
        StrategicPlanState plans = StrategicPlanState.empty()
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
    static RouteSceneReturnFixture routeSceneReturnFixture(WorldId worldId, long seed) {
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.uncontestedSupplyConfiguration(worldId, seed));
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
                || !operation.participantIds().equals(List.of(new SubjectId("resident:1-30"), new SubjectId("resident:1-16"), new SubjectId("resident:1-28")))) {
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
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(worldId, seed));
        BlockPosition firstLoss = new BlockPosition(-380, 64, -304);
        BlockPosition secondLoss = new BlockPosition(-140, 64, -304);
        state = state.recordPhysicalDelta(new PhysicalDelta(firstLoss, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                Optional.of(FrontierRouteNetwork.OWNER), Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "fixture:cold-source"));
        state = state.recordPhysicalDelta(new PhysicalDelta(secondLoss, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                Optional.of(FrontierRouteNetwork.OWNER), Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "fixture:loaded-repair"));

        RouteMaintenance first = maintenanceStarted(state, 1, 100L, firstLoss);
        state = RouteMaintenanceStateSupport.reduceStarted(state, FrontierRouteNetwork.OWNER, new RouteMaintenanceStarted(first));
        RouteMaintenance second = maintenanceStarted(state, 2, 200L, secondLoss);
        state = RouteMaintenanceStateSupport.reduceStarted(state, FrontierRouteNetwork.OWNER, new RouteMaintenanceStarted(second));

        ExactInventory inventory = state.inventory().withSurfaceStatus(FrontierRouteNetwork.MAINTENANCE_CONTAINER, ContainerSurfaceStatus.PREPARED)
                .withSurfaceStatus(FrontierRouteNetwork.MAINTENANCE_CONTAINER, ContainerSurfaceStatus.ACTIVE);
        SubjectId coldSource = new SubjectId("item:fixture-maintenance-cold-source");
        inventory = inventory.store(new ExactItemStack(coldSource, FrontierRouteNetwork.OWNER, "minecraft:gray_concrete", 1,
                new InventoryCustody.ContainerSlot(FrontierRouteNetwork.MAINTENANCE_CONTAINER,
                        inventory.firstFreeSlot(FrontierRouteNetwork.MAINTENANCE_CONTAINER).orElseThrow())));
        state = state.withInventory(inventory);
        state = RouteMaintenanceProcess.reducePrepared(state, FrontierRouteNetwork.OWNER,
                RouteMaintenanceProcess.materialLoadingIntent(state, first, state.inventory().items().get(coldSource)));

        // This cargo models an already-confirmed earlier physical pickup, not an alternate
        // source. It lets the player load only the later worksite while the first exact source
        // remains naturally COLD at the real shared maintenance chest.
        SubjectId priorSource = new SubjectId("item:fixture-maintenance-loaded-source");
        inventory = state.inventory().store(new ExactItemStack(priorSource, FrontierRouteNetwork.OWNER, "minecraft:gray_concrete", 1,
                new InventoryCustody.ContainerSlot(FrontierRouteNetwork.MAINTENANCE_CONTAINER,
                        state.inventory().firstFreeSlot(FrontierRouteNetwork.MAINTENANCE_CONTAINER).orElseThrow())));
        CargoBatch secondCargo = new CargoBatch(second.plannedCargoId(), FrontierRouteNetwork.OWNER, List.of(second.plannedCargoItemId()));
        inventory = inventory.extractOneToCargo(priorSource, secondCargo, second.plannedCargoItemId());
        for (SubjectId member : second.team().memberIds()) {
            ExactItemStack tool = inventory.items().values().stream().filter(item -> item.economicOwnerId().equals(second.settlementId()))
                    .filter(item -> item.custody() instanceof InventoryCustody.ContainerSlot)
                    .filter(item -> EngineeringToolCustody.isTool(item.itemKind())).findFirst()
                    .orElseThrow(() -> new IllegalStateException("fairness fixture needs one exact local engineering tool per member"));
            inventory = inventory.moveObservedItem(tool.id(), tool.custody(), new InventoryCustody.Actor(member));
        }
        state = state.withInventory(inventory);
        EngineeringWorkAssembly initialAssembly = EngineeringWorksite.compile(state, second);
        state = RouteMaintenanceStateSupport.reduceAssemblyStarted(state, FrontierRouteNetwork.OWNER,
                new RouteMaintenanceAssemblyStarted(second.id(), initialAssembly));
        while (!state.routeMaintenances().get(second.id()).assembly().orElseThrow().complete()) {
            EngineeringWorkAssembly current = state.routeMaintenances().get(second.id()).assembly().orElseThrow();
            SubjectId advancing = current.safeAdvances().getFirst();
            state = RouteMaintenanceStateSupport.reduceAssemblyAdvanced(state, FrontierRouteNetwork.OWNER,
                    new RouteMaintenanceAssemblyAdvanced(second.id(), current.advance(advancing)));
        }
        Map<SubjectId, RouteMaintenance> maintenances = new LinkedHashMap<>(state.routeMaintenances());
        maintenances.put(second.id(), maintenances.get(second.id()).withCargo(secondCargo.id()));
        state = state.withChanges(FrontierWorldStateUpdate.begin().routeMaintenances(maintenances));
        // Do not pre-prepare the physical repair. A work intent is authority granted only by
        // the actual HOT worksite lease; the normal loaded scene executor must request it after
        // the player visit. Retaining it here would let a direct physical executor race the
        // scene transition and would make this fixture prove an invalid lifecycle.
        return new RouteMaintenanceFairnessFixture(state, new SimInstant(300L), List.of(), first.id(), second.id());
    }

    private static RouteMaintenance maintenanceStarted(FrontierWorldState state, int scanOrdinal, long dueAt, BlockPosition expectedLoss) {
        return RouteMaintenanceProcess.plan(state, RouteMaintenanceProcess.scan(scanOrdinal, dueAt)).stream().map(ProposedEvent::payload)
                .filter(RouteMaintenanceStarted.class::isInstance).map(RouteMaintenanceStarted.class::cast)
                .map(RouteMaintenanceStarted::maintenance).filter(maintenance -> maintenance.repairCell().equals(expectedLoss)).findFirst()
                .orElseThrow(() -> new IllegalStateException("fairness fixture did not admit its exact retained loss " + expectedLoss));
    }

    /**
     * Disposable physical-perception fixture.  It changes no operation, cargo or lease: one
     * otherwise ordinary unleased Scout begins beside the exact first cargo anchor, so only a
     * player-loaded HOT caravan can produce the subsequent observation.
     */
    static RouteSceneReturnFixture hotScoutSightingFixture(WorldId worldId, long seed) {
        RouteSceneReturnFixture base = routeSceneReturnFixture(worldId, seed);
        RouteOperation operation = base.state().operations().get(new SubjectId("operation:supply-1-2"));
        Bioform scout = base.state().bootstrap().hive().bioforms().stream().filter(value -> value.id().equals(new SubjectId("bioform:west-1")))
                .filter(Bioform::isScout).findFirst().orElseThrow();
        if (operation == null || operation.activeTravel().isEmpty()) throw new IllegalStateException("hot scout fixture has no active exact cargo route");
        FrontierWorldState state = base.state().withActorBody(scout.id(), BodyPosition.above(new SurfaceAnchor(operation.activeTravel().orElseThrow().cargoAnchor().surface().support())));
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
        RouteOperation operation = base.state().operations().get(new SubjectId("operation:supply-1-2"));
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
        state = state.withActorBody(bomber.id(), BodyPosition.above(new SurfaceAnchor(intercept.offset(-1, 0, 0))));
        state = state.withActorBody(guards.getFirst().id(), BodyPosition.above(new SurfaceAnchor(intercept.offset(1, 0, 0))));
        state = state.withActorBody(guards.getLast().id(), BodyPosition.above(new SurfaceAnchor(intercept.offset(0, 0, -1))));
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
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.uncontestedSupplyConfiguration(worldId, seed));
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
        var base = FrontierV3FixtureCatalog.uncontestedSupplyConfiguration(worldId, seed);
        SubjectId store = new SubjectId("container:hive-east-store");
        // This is the durable boundary after a socket has been claimed but before its physical
        // chest write.  The native pilot must load the chunk and let the ordinary container
        // executor complete PREPARED -> ACTIVE before exact consumption can run.
        FrontierWorldState initial = base.initialState().withInventory(base.initialState().inventory()
                .withSurfaceStatus(store, ContainerSurfaceStatus.PREPARED));
        var engine = FrontierEngines.create(new io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration<>(base.worldId(), initial,
                base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(),
                base.initialSchedules(), base.transactionCommitter()));
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
        state = state.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task)).withInventory(state.inventory()
                .withSurfaceStatus(eastStore, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(westStore, ContainerSurfaceStatus.PREPARED));
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
        state = state.withInventory(state.inventory().withoutItem(new SubjectId("item:bootstrap-1-wheat")));
        Settlement settlement = bootstrap.settlements().getFirst();
        SettlementStructure infirmary = settlement.structures().stream().filter(value -> value.kind() == StructureKind.INFIRMARY)
                .findFirst().orElseThrow(() -> new IllegalStateException("health fixture needs one infirmary"));
        // Health contact is defined against the structure's semantic foundation, not its
        // decorative projection (signs and trim may extend beyond that foundation).
        InfectionCell contact = InfectionCell.at(infirmary.anchor());
        state = state.withInfection(contact, new FixedRatio(new FixedScalar(FixedScalar.SCALE)));
        return new HealthQuarantineFixture(state, SimInstant.ZERO, List.of(StrategicObjectiveProcess.review(settlement.id(), 1, 1L)), settlement.id(), contact);
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
        SubjectId settlementId = new SubjectId("settlement:1");
        for (ProposedEvent event : CompanyFoundationProcess.plan(state, CompanyFoundationProcess.review(settlementId, 1, 4_000L))) {
            if (event.payload() instanceof CompanyRegistered registered) state = CompanyFoundationProcess.reduce(state, settlementId, registered);
            if (event.payload() instanceof EmploymentContractOpened opened) state = CompanyFoundationProcess.reduceEmployment(state, settlementId, opened);
        }
        Settlement settlement = state.bootstrap().settlements().getFirst();
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:development-production-input-theft"), settlementId,
                StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:development-production-input-theft"), objective.id(), settlementId,
                StrategicTaskKind.PRODUCE_BREAD, Optional.empty(), List.of(StrategicTaskRequirement.ACTIVE_WORKSHOP, StrategicTaskRequirement.EXACT_WHEAT_INPUT),
                List.of(), StrategicTaskStatus.ACTIVE);
        state = state.withStrategicPlans(StrategicPlanState.empty().addObjective(objective).addTask(task));
        SubjectId company = CompanyFoundationProcess.companyId(settlementId);
        SubjectId worker = state.companies().companies().get(company).founderId();
        ExactItemStack input = state.inventory().items().get(new SubjectId("item:bootstrap-1-wheat"));
        ProductionJob job = new ProductionJob(new SubjectId("job:development-production-input-theft"), settlementId,
                settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.WORKSHOP).findFirst().orElseThrow().id(), worker,
                input.id(), new ProductionInputHold.Materialized(input.id()), new SubjectId("item:development-production-input-theft-bread"), "minecraft:bread", input.count());
        state = CompanyWorkPaymentProcess.reserve(state.withProductionJob(job), job);
        EmploymentContract contract = CompanyWorkPaymentProcess.contractFor(state, job).orElseThrow();
        FinancialReservation reservation = CompanyWorkPaymentProcess.reservation(job, contract);
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
     * Disposable player-combat fixture for the irreversible-worker boundary.  The exact worker
     * is deliberately the sole ambient actor inside the ordinary scene-demand radius; this is
     * test isolation only, not a second movement or materialization authority.
     */
    static MaterializedProductionFixture materializedProductionWorkerDeathFixture(WorldId worldId, long seed) {
        MaterializedProductionFixture base = materializedProductionInputTheftFixture(worldId, seed);
        SubjectId jobId = new SubjectId("job:development-production-input-theft");
        SubjectId worker = base.state().productionJobs().get(jobId).workerId();
        FrontierWorldState isolated = base.state().withActorBody(worker, BodyPosition.above(new SurfaceAnchor(new BlockPosition(-480, 64, -480))));
        return new MaterializedProductionFixture(isolated, base.instant(), base.schedules(), base.orderId());
    }

    record HiveGrowthFixture(FrontierWorldState state, SimInstant instant, List<ScheduledAction> schedules) {
        HiveGrowthFixture {
            schedules = List.copyOf(schedules);
        }
    }

    record HiveNutrientTransferFixture(FrontierWorldState state, SimInstant instant, List<ScheduledAction> schedules, SubjectId transferId) {
        HiveNutrientTransferFixture {
            schedules = List.copyOf(schedules);
        }
    }

    record RouteSceneReturnFixture(FrontierWorldState state, SimInstant instant, List<ScheduledAction> schedules) {
        RouteSceneReturnFixture {
            schedules = List.copyOf(schedules);
        }
    }

    record RouteMaintenanceFairnessFixture(FrontierWorldState state, SimInstant instant, List<ScheduledAction> schedules,
                                           SubjectId coldSourceMaintenanceId, SubjectId loadedRepairMaintenanceId) {
        RouteMaintenanceFairnessFixture {
            schedules = List.copyOf(schedules);
        }
    }

    record AmbientScoutPatrolFixture(FrontierWorldState state, SimInstant instant, List<ScheduledAction> schedules,
                                     SubjectId scoutId, BlockPosition priorPosition, BlockPosition nextGoalPosition) {
        AmbientScoutPatrolFixture {
            schedules = List.copyOf(schedules);
        }
    }

    record SettlementAssaultFixture(FrontierWorldState state, SimInstant instant, List<ScheduledAction> schedules, SubjectId assaultId) {
        SettlementAssaultFixture {
            schedules = List.copyOf(schedules);
        }
    }

    record RouteConstructionFixture(FrontierWorldState state, SimInstant instant, List<ScheduledAction> schedules, SubjectId projectId) {
        RouteConstructionFixture {
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

    record MedicalTreatmentFixture(FrontierWorldState state, SimInstant instant, List<ScheduledAction> schedules) {
        MedicalTreatmentFixture {
            schedules = List.copyOf(schedules);
        }
    }

    record ResidentTransitFixture(FrontierWorldState state, SimInstant instant, List<ScheduledAction> schedules,
                                  ResidentMigrationJourney journey) {
        ResidentTransitFixture {
            schedules = List.copyOf(schedules);
        }
    }

    record MaterializedProductionFixture(FrontierWorldState state, SimInstant instant, List<ScheduledAction> schedules, SubjectId orderId) {
        MaterializedProductionFixture {
            schedules = List.copyOf(schedules);
        }
    }
}
