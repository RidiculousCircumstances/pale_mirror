package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.group.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual registered policy, queue, movement and custody; no fabricated arrival or delivery. */
class UnitGroupTransportTest {
    @Test void confirmedLoadingAndCooperativeCompletionWakeTheRegisteredMissionWithoutPeriodicDelay() throws Exception {
        var configuration = completeNeedClocks(FrontierV3FixtureCatalog.autonomousGoodsConfiguration(new WorldId("frontier:group-readiness"), 41));
        var engine = FrontierEngines.createCanonicalStateAccess(configuration);
        UnitGroup ready = null;
        for (int boundary = 0; boundary < 500; boundary++) {
            var state = engine.canonicalState().state();
            ready = state.unitGroups().groups().values().stream().filter(group -> {
                var mission = state.shipments().missions().get(group.mission().id());
                return mission.stage() == TransportMission.Stage.LOADING && mission.shipmentIds().stream()
                        .allMatch(id -> state.shipments().shipments().get(id).status() == Shipment.Status.CARRYING);
            }).findFirst().orElse(null);
            if (ready != null) break;
            var next = engine.checkpoint().schedules().stream().filter(action -> !FrontierWorldRuntimeDefinition.scheduledHeld(state, action)).sorted().findFirst().orElseThrow();
            var result = engine.advanceTo(new SimInstant(Math.max(engine.checkpoint().instant().ticks(), next.dueAt().ticks())), new WorkBudget(1, 1024));
            assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse("active"));
        }
        assertNotNull(ready, "must stop at actual confirmed COLD pickup, before mission progress");
        var groupId = ready.id(); var missionId = ready.mission().id();
        long loadedAt = engine.checkpoint().instant().ticks();
        assertEquals(loadedAt + 1, engine.checkpoint().schedules().stream()
                .filter(action -> action.subject().equals(missionId)).findFirst().orElseThrow().dueAt().ticks());
        try (var planner = new CooperativePedestrianPlanner(); var binding = PedestrianRoutePlanning.bind(planner)) {
            var result = engine.advanceTo(new SimInstant(loadedAt + 2), new WorkBudget(128, 1024));
            assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse("active"));
            var state = engine.canonicalState().state(); var group = state.unitGroups().groups().get(groupId);
            assertEquals(TransportMission.Stage.OUTBOUND, state.shipments().missions().get(missionId).stage());
            assertEquals(UnitGroup.Phase.READY, group.phase());
            assertEquals("PLANNING", UnitGroupProcess.navigationReadiness(state, group).status());
            long work = planner.workUnits();
            for (int read = 0; read < 20; read++) assertEquals("PLANNING", UnitGroupProcess.navigationReadiness(state, group).status());
            assertEquals(work, planner.workUnits(), "diagnostics cannot advance or enqueue planning");
            long signal = planner.progressRevision();
            for (int slice = 0; slice < 2000 && planner.pendingCount() > 0; slice++)
                planner.advance(HierarchicalPedestrianSearch.MIN_SLICE_WORK * 2);
            assertEquals(0, planner.pendingCount()); assertTrue(planner.progressRevision() > signal);
            assertEquals("FOUND", UnitGroupProcess.navigationReadiness(state, group).status());
            var notification = new UnitGroupNavigationReady(group.id(), group.revision());
            var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
            assertEquals(notification, codecs.decode(notification.type(), codecs.encode(notification)));
            var checkpoint = engine.checkpoint(); var commandId = new CommandId("test:group-navigation-ready");
            assertInstanceOf(CommandResult.Accepted.class, engine.submit(new FrontierCommand(1, commandId,
                    checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                    CauseChain.root(commandId), notification)));
            assertEquals(checkpoint.instant().ticks() + 1, engine.checkpoint().schedules().stream()
                    .filter(action -> action.subject().equals(missionId)).findFirst().orElseThrow().dueAt().ticks());
            var advanced = engine.advanceTo(new SimInstant(checkpoint.instant().ticks() + 3), new WorkBudget(128, 1024));
            assertEquals(EngineStatus.Kind.ACTIVE, advanced.status().kind(), advanced.status().failureDetail().orElse("active"));
            assertEquals(UnitGroup.Phase.TRAVELLING, engine.canonicalState().state().unitGroups().groups().get(groupId).phase());
            var after = engine.checkpoint(); var staleId = new CommandId("test:group-navigation-stale");
            assertInstanceOf(CommandResult.Rejected.class, engine.submit(new FrontierCommand(1, staleId, after.worldId(),
                    after.revision(), after.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(staleId), notification)));
        }
    }
    @Test void formationDeliveryPaymentReturnAndRetirementSurviveAnInFlightCheckpoint() {
        var resumedKinds = EnumSet.noneOf(ActorActivityKind.class);
        var configuration = completeNeedClocks(FrontierV3FixtureCatalog.autonomousGoodsConfiguration(new WorldId("frontier:group-transport"), 41))
                .withTransactionCommitter((transaction, durability) -> {
                    for (var event : transaction.events()) if (event.payload() instanceof
                            io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionResumed resumed) {
                        var successor = resumed.successor();
                        if (successor.activityKind() != ActorActivityKind.GROUP_MEMBER
                                && successor.activityKind() != ActorActivityKind.COURIER) continue;
                        resumedKinds.add(successor.activityKind());
                        assertTrue(transaction.events().stream().map(FrontierEvent::payload)
                                .filter(ScheduleEffect.Rescheduled.class::isInstance).map(ScheduleEffect.Rescheduled.class::cast)
                                .anyMatch(wake -> wake.replacement().kind().equals(UnitGroupProcess.PROGRESS)
                                        && wake.replacement().dueAt().ticks() == resumed.atTick() + 1),
                                "actual post-meal resumption must wake its retained group in the same transaction");
                    }
                });
        var engine = FrontierEngines.createCanonicalStateAccess(configuration);
        UnitGroup admitted = null; boolean recovered = false, acceptedBeforeReturn = false, closed = false, nonflat = false;
        for (int boundary = 0; boundary < 4000; boundary++) {
            var state = engine.canonicalState().state();
            if (admitted == null && !state.unitGroups().groups().isEmpty()) {
                admitted = state.unitGroups().groups().values().iterator().next();
                assertEquals(2, admitted.members().size());
                assertEquals(List.of(UnitGroup.Role.CARRIER, UnitGroup.Role.GUIDE), admitted.members().stream().map(UnitGroup.Member::role).toList());
            }
            if (admitted != null) {
                var group = state.unitGroups().groups().get(admitted.id());
                var mission = state.shipments().missions().get(admitted.mission().id());
                if (group == null) { closed = true; break; }
                if (!recovered && group.phase() == UnitGroup.Phase.TRAVELLING && state.actorMovements().values().stream().anyMatch(m -> m.coldTravel().isPresent())) {
                    var home = state.shipments().missions().get(group.mission().id()).sender().settlementId();
                    var permissions = SettlementStaffingComposition.POLICY.propose(state, home);
                    for (var member : group.members()) {
                        assertTrue(permissions.permits(ResidentWorkKind.LOGISTICS, member.actorId()));
                        assertFalse(HumanAssignmentProjection.compile(state).idle(member.actorId()));
                    }
                    var checkpoint = engine.checkpoint();
                    var codec = new FrontierWorldStateCodec();
                    assertArrayEquals(checkpoint.canonicalState(), codec.encode(codec.decode(checkpoint.canonicalState())));
                    engine = FrontierEngines.recoverCanonicalStateAccess(configuration,
                            new RecoveryImage(configuration.worldId(), Optional.of(new SnapshotRecord(checkpoint, 0)), List.of()));
                    assertEquals(state.unitGroups(), engine.canonicalState().state().unitGroups());
                    recovered = true;
                }
                if (state.companies().goodsTrade().contracts().values().stream().anyMatch(GoodsTradeContract::fulfilled)
                        && group.phase() != UnitGroup.Phase.CLOSED) acceptedBeforeReturn = true;
                if (group.journey().isPresent()) {
                    var journey = group.journey().orElseThrow();
                    assertEquals(group.members().size(), journey.stations().values().stream().distinct().count());
                    nonflat |= journey.route().stream().map(SurfaceAnchor::y).distinct().count() > 1;
                }
                assertEquals(admitted.members(), group.members(), "checkpoint/return cannot discover a replacement roster");
                assertNotNull(mission);
            }
            var current = engine.canonicalState().state();
            var next = engine.checkpoint().schedules().stream().filter(action -> !FrontierWorldRuntimeDefinition.scheduledHeld(current, action))
                    .sorted().findFirst().orElseThrow();
            var result = engine.advanceTo(new SimInstant(Math.max(engine.checkpoint().instant().ticks(), next.dueAt().ticks())), new WorkBudget(128, 1024));
            assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse("active"));
            // Ordinary hosts compact after installing a snapshot. This long-lived fixture must do the same.
            if (boundary % 64 == 0) {
                var saved = FrontierPersistenceCodec.decodeSnapshot(FrontierPersistenceCodec.encodeSnapshot(new SnapshotRecord(engine.checkpoint(), 0)));
                engine.compact(saved.checkpoint().revision());
            }
        }
        assertNotNull(admitted); assertTrue(recovered); assertTrue(acceptedBeforeReturn); assertTrue(nonflat);
        assertTrue(closed, "mission must return and retire, not just deliver");
        var terminal = engine.canonicalState().state();
        assertTrue(terminal.shipments().missions().isEmpty()); assertTrue(terminal.unitGroups().groups().isEmpty());
        for (var member : admitted.members()) {
            assertTrue(HumanAssignmentProjection.compile(terminal).idle(member.actorId()));
            assertEquals(0, terminal.inventory().fungibleResources().accounts().values().stream()
                    .filter(account -> account.custody().equals(new ResourceCustody.Actor(member.actorId()))).count());
            assertTrue(terminal.actorExecutions().actors().get(member.actorId()).current().stream()
                    .noneMatch(execution -> execution.activityKind() == ActorActivityKind.GROUP_MEMBER));
            assertTrue(terminal.actorExecutions().actors().get(member.actorId()).suspended().isEmpty());
        }
        var completed = admitted;
        assertTrue(engine.checkpoint().schedules().stream().noneMatch(action -> action.subject().equals(completed.id())
                || action.subject().equals(completed.mission().id())));
    }
    private static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> completeNeedClocks(
            FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base) {
        // The original trade fixture ends at delivery. A return journey crosses hunger thresholds,
        // so retain the real need schedules that a confirmed meal must reschedule.
        var schedules = new ArrayList<>(base.initialSchedules()); var state = base.initialState();
        for (var resident : state.humanPopulation().residents().values()) {
            long due = state.humanPopulation().nutrition(resident.id()).nextThresholdTick(state.bootstrap().ruleset().residentLife(),
                    resident.characteristics().effectiveMetabolismPermille(base.initialInstant().ticks()));
            schedules.add(ResidentNeedProcess.review(resident.id(), due));
        }
        return new FrontierEngineConfiguration<>(base.worldId(), state, base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(),
                base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(), schedules, base.transactionCommitter(),
                base.stateValidator(), base.executionMetrics(), base.kernelQuarantineReporter());
    }

    @Test void actualCarrierAndGuideMealsResumeTheirRetainedGroupWithoutWaitingForPeriodicReview() {
        var base = completeNeedClocks(FrontierV3FixtureCatalog.autonomousGoodsConfiguration(new WorldId("frontier:group-meal-resume"), 41));
        var initialEngine = FrontierEngines.createCanonicalStateAccess(base);
        UnitGroup group = null;
        for (int turn = 0; turn < 256; turn++) {
            var state = initialEngine.canonicalState().state();
            group = state.unitGroups().groups().values().stream().filter(candidate ->
                    candidate.phase() == UnitGroup.Phase.TRAVELLING && candidate.members().stream()
                            .allMatch(member -> state.actorExecutions().actors().get(member.actorId()).current().isPresent()))
                    .findFirst().orElse(null);
            if (group != null) break;
            var due = initialEngine.checkpoint().schedules().stream()
                    .filter(action -> !FrontierWorldRuntimeDefinition.scheduledHeld(state, action)).sorted().findFirst().orElseThrow();
            var result = initialEngine.advanceTo(new SimInstant(Math.max(initialEngine.checkpoint().instant().ticks(), due.dueAt().ticks())),
                    new WorkBudget(1, 1024));
            assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse("active"));
        }
        assertNotNull(group);
        var selected = group; var state = initialEngine.canonicalState().state(); var population = state.humanPopulation();
        long now = initialEngine.checkpoint().instant().ticks();
        var nutrition = new LinkedHashMap<>(population.nutrition());
        for (var member : group.members()) nutrition.put(member.actorId(), new ResidentNutrition(ResidentNutritionStatus.HUNGRY,
                state.bootstrap().ruleset().residentLife().eatBelowUnits() - 1, now, 0));
        // Hungry travellers are the fixture precondition; all movement, consumption and resume outcomes remain registered.
        state = state.withHumanPopulation(new HumanPopulation(population.households(), population.residents(), population.birthJobs(),
                population.health(), population.quarantines(), population.migrations(), population.provisions(), nutrition,
                population.medicalOperations(), population.schedules(), population.meals(), population.mealResourceObligations()));
        // Outbound members eat actual fixture-held provisions, not a new solo route to the home depot.
        var resources = state.inventory().fungibleResources();
        for (var member : group.members()) {
            var lot = new SubjectId("lot:meal-resume-" + member.actorId().value().replace(':', '-'));
            var account = new SubjectId("custody:meal-resume-" + member.actorId().value().replace(':', '-'));
            var owner = state.humanPopulation().resident(member.actorId()).settlementId();
            resources = resources.issue(new ResourceLot(lot, owner, FoodCatalog.BREAD, 8, "fixture", List.of()),
                    new CustodyAccount(account, new ResourceCustody.Actor(member.actorId()), Map.of(lot, 8), Map.of()));
        }
        state = state.withInventory(state.inventory().withFungibleResources(resources));
        var schedules = new ArrayList<>(initialEngine.checkpoint().schedules());
        for (var member : group.members()) {
            var review = ResidentActivityProcess.review(member.actorId(), now + 1);
            schedules.removeIf(action -> action.id().equals(review.id())); schedules.add(review);
            schedules.removeIf(action -> action.subject().equals(member.actorId()) && action.kind().equals(ResidentNeedProcess.REVIEW));
            schedules.add(ResidentNeedProcess.review(member.actorId(), state.humanPopulation().nutrition(member.actorId())
                    .nextThresholdTick(state.bootstrap().ruleset().residentLife(),
                            state.humanPopulation().resident(member.actorId()).characteristics().effectiveMetabolismPermille(now))));
        }
        var resumed = EnumSet.noneOf(ActorActivityKind.class);
        var config = new FrontierEngineConfiguration<>(base.worldId(), state, new SimInstant(now), base.commandPlanner(),
                base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(), schedules,
                (TransactionCommitter) (transaction, durability) -> {
                    for (var event : transaction.events()) if (event.payload() instanceof
                            io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionResumed result
                            && selected.members().stream().anyMatch(member -> member.actorId().equals(result.successor().actorId()))) {
                        resumed.add(result.successor().activityKind());
                        assertTrue(transaction.events().stream().anyMatch(change -> change.payload() instanceof ScheduleEffect.Rescheduled wake
                                && wake.replacement().equals(UnitGroupProcess.progress(selected.id(), result.atTick() + 1))),
                                "meal completion must notify exact retained group in the resume transaction");
                    }
                }, base.stateValidator());
        var engine = FrontierEngines.createCanonicalStateAccess(config);
        for (int turn = 0; turn < 512 && resumed.size() < 2; turn++) {
            var current = engine.canonicalState().state();
            var due = engine.checkpoint().schedules().stream().filter(action -> !FrontierWorldRuntimeDefinition.scheduledHeld(current, action))
                    .sorted().findFirst().orElseThrow();
            var result = engine.advanceTo(new SimInstant(Math.max(engine.checkpoint().instant().ticks(), due.dueAt().ticks())), new WorkBudget(1, 1024));
            assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse("active"));
        }
        assertEquals(EnumSet.of(ActorActivityKind.COURIER, ActorActivityKind.GROUP_MEMBER), resumed);
    }

    @Test void forgedRoleDanglingMissionPrematureArrivalAndStaleEpochFailClosed() {
        var configuration = FrontierV3FixtureCatalog.autonomousGoodsConfiguration(new WorldId("frontier:group-references"), 41);
        var engine = FrontierEngines.createCanonicalStateAccess(configuration);
        for (int boundary = 0; boundary < 500 && engine.canonicalState().state().unitGroups().groups().values().stream()
                .noneMatch(group -> group.phase() == UnitGroup.Phase.TRAVELLING); boundary++) {
            var state = engine.canonicalState().state();
            var next = engine.checkpoint().schedules().stream().filter(action -> !FrontierWorldRuntimeDefinition.scheduledHeld(state, action)).sorted().findFirst().orElseThrow();
            var result = engine.advanceTo(new SimInstant(Math.max(engine.checkpoint().instant().ticks(), next.dueAt().ticks())), new WorkBudget(128, 1024));
            assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse("active"));
        }
        var state = engine.canonicalState().state(); var group = state.unitGroups().groups().values().iterator().next();
        assertThrows(IllegalArgumentException.class, () -> UnitGroupMissionPorts.registry(List.of()));
        var port = UnitGroupMissionPorts.require(group);
        assertThrows(IllegalArgumentException.class, () -> UnitGroupMissionPorts.registry(List.of(port, port)));
        var carrier = group.members().getFirst(); var members = new ArrayList<>(group.members());
        members.set(0, new UnitGroup.Member(carrier.actorId(), UnitGroup.Role.GUIDE, ActorActivityKind.GROUP_MEMBER, group.id()));
        var forged = new UnitGroup(group.id(), group.mission(), members, group.formation(), group.phase(), group.revision(), group.goalOrdinal(), group.journey());
        assertThrows(IllegalArgumentException.class, () -> FrontierReferenceClosure.validate(
                state.withChanges(FrontierWorldStateUpdate.begin().unitGroups(new UnitGroupState(Map.of(group.id(), forged)))), List.of()));
        assertThrows(IllegalArgumentException.class, () -> FrontierReferenceClosure.validate(state.withChanges(
                FrontierWorldStateUpdate.begin().shipments(new ShipmentState(state.shipments().shipments(), Map.of()))), List.of()));
        assertThrows(IllegalArgumentException.class, () -> UnitGroupProcess.reduce(state, group.id(),
                new UnitGroupAdvanced(group.id(), group.revision(), UnitGroupAdvanced.Change.ARRIVE, group.goalOrdinal(), Optional.empty(), Optional.empty())));
        assertThrows(IllegalArgumentException.class, () -> UnitGroupProcess.reduce(state, group.id(),
                new UnitGroupAdvanced(group.id(), group.revision() - 1, UnitGroupAdvanced.Change.ARRIVE, group.goalOrdinal(), Optional.empty(), Optional.empty())));
        assertFalse(ActorExecutionCoordinator.ordinaryWorkAdmission(state, carrier.actorId()).permitted());
        var wrongPurpose = new ArrayList<>(group.members());
        wrongPurpose.set(0, new UnitGroup.Member(carrier.actorId(), UnitGroup.Role.CARRIER, ActorActivityKind.GROUP_MEMBER, carrier.activityOwnerId()));
        var wrongOwner = new ArrayList<>(group.members());
        wrongOwner.set(0, new UnitGroup.Member(carrier.actorId(), UnitGroup.Role.CARRIER, carrier.activityKind(), new SubjectId("shipment:unknown-owner")));
        for (var roster : List.of(wrongPurpose, wrongOwner)) {
            var declaration = new UnitGroup(group.id(), group.mission(), roster, group.formation(), group.phase(), group.revision(), group.goalOrdinal(), group.journey());
            assertThrows(IllegalArgumentException.class, () -> TransportGroupMissionPort.validateDeclaration(state,
                    state.shipments().missions().get(group.mission().id()), declaration, state.shipments().shipments()));
        }
        var target = group.journey().orElseThrow().destination();
        var shortAssembly = GroupFormation.first(group, target, List.of(target), port.knowledge(state, group));
        assertEquals(group.members().size(), shortAssembly.stations().values().stream().distinct().count());
        assertTrue(port.knowledge(state, group).traversable(List.copyOf(shortAssembly.stations().values())));
        var damaged = state.recordPhysicalDelta(new PhysicalDelta(target.support(), PhysicalDeltaKind.UNKNOWN_SCAR,
                Optional.empty(), Optional.empty(), "test:assembly-obstructed"));
        assertFalse(UnitGroupMissionPorts.require(group).knowledge(damaged, group).traversable(List.of(target)));
    }
}
