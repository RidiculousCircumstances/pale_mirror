package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.model.group.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual registered policy, queue, movement and custody; no fabricated arrival or delivery. */
class UnitGroupTransportTest {
    @Test void formationDeliveryPaymentReturnAndRetirementSurviveAnInFlightCheckpoint() {
        var configuration = completeNeedClocks(FrontierV3FixtureCatalog.autonomousGoodsConfiguration(new WorldId("frontier:group-transport"), 41));
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
