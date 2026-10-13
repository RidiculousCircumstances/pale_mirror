package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.process.HiveSettlementAssaultProcess;
import io.farfrontier.palemirror.frontier.v3.process.DefenderEquipmentProcess;
import io.farfrontier.palemirror.frontier.v3.process.DefenderEquipmentReturnProcess;
import io.farfrontier.palemirror.frontier.v3.process.PhysicalIntentLifecycleFixture;
import io.farfrontier.palemirror.frontier.v3.process.StrategicObjectiveProcess;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class HumanTacticalFunctionProjectionTest {
    @Test
    void exactAssignmentUnitLeadershipAndActorWeaponDeriveReadableDefenceFunctions() {
        Fixture fixture = fixture();
        List<ProposedEvent> events = HiveSettlementAssaultProcess.planStart(fixture.state(),
                HiveSettlementAssaultProcess.start(fixture.task(), fixture.sighting(), 200L));
        FrontierWorldState state = StrategicObjectiveProcess.reduceTaskTransition(fixture.state(), fixture.hive(),
                assertInstanceOf(StrategicTaskTransition.class, events.getFirst().payload()));
        SettlementAssault assault = assertInstanceOf(SettlementAssaultStarted.class, events.get(1).payload()).assault();
        state = HiveSettlementAssaultProcess.reduceStarted(state, fixture.hive(), new SettlementAssaultStarted(assault, io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultExecutionAuthority.admission(state, assault)));

        SubjectId leader = assault.defenderUnit().leaderId();
        assertEquals(HumanTacticalFunction.SQUAD_LEADER, HumanTacticalFunctionProjection.derive(state, leader));
        assertEquals(state.humanPopulation().resident(leader).name() + "\nUNIT IMPROVISED", FrontierSceneLabels.actor(state, leader, false));

        SubjectId depot = FrontierWorldState.depotId(assault.settlementId());
        int slot = state.inventory().firstFreeSlot(depot).orElseThrow();
        SubjectId sword = new SubjectId("item:tactical-function-sword");
        ExactInventory stored = state.inventory().withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE)
                .store(new ExactItemStack(sword, assault.settlementId(), "minecraft:iron_sword", 1,
                new InventoryCustody.ContainerSlot(depot, slot)));
        state = state.withInventory(stored);
        FrontierWorldState remote = state;
        assertTrue(DefenderEquipmentProcess.plan(remote, DefenderEquipmentProcess.review(assault, 201L)).stream()
                .noneMatch(event -> event.payload() instanceof PhysicalIntentPrepared), "unfinished defence cannot reserve remote equipment");
        var contactActor = assault.defenderIds().stream().sorted().filter(id -> !id.equals(leader)).findFirst().orElse(leader);
        state = state.withActorBody(contactActor, ResourceStorageAccess.at(state, depot).stations().getFirst().standingBody());
        PhysicalIntent issue = DefenderEquipmentProcess.plan(state, DefenderEquipmentProcess.review(assault, 201L)).stream()
                .map(ProposedEvent::payload).filter(PhysicalIntentPrepared.class::isInstance).map(PhysicalIntentPrepared.class::cast)
                .map(PhysicalIntentPrepared::intent).findFirst().orElseThrow();
        SubjectId militia = issue.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.ASSAULT_DEFENDER);
        assertEquals(HumanTacticalFunction.MILITIA, HumanTacticalFunctionProjection.derive(state, militia));
        assertEquals(depot, EquipmentPhysicalAuthority.container(issue));
        assertThrows(IllegalArgumentException.class, () -> EquipmentServiceBoundary.requireMayBegin(remote, issue),
                "canonical stock ownership is not evidence of a physical visit");
        state = atStorageStation(state, militia, depot);
        state = PhysicalIntentLifecycleFixture.prepare(state, assault.settlementId(), issue);
        EquipmentServiceBoundary.requireMayBegin(state, issue);
        state = PhysicalIntentLifecycleFixture.transition(state,
                assault.settlementId(), issue, PhysicalIntentStatus.RUNNING, Optional.empty());
        org.junit.jupiter.api.Assertions.assertTrue(ContainerPhysicalAuthorityComposition.pending(state, depot));
        FrontierWorldState unresolved = PhysicalIntentLifecycleFixture.transition(state, assault.settlementId(), issue,
                PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty());
        assertEquals(unresolved, new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().decode(
                new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().encode(unresolved)));
        org.junit.jupiter.api.Assertions.assertTrue(ContainerPhysicalAuthorityComposition.pending(unresolved, depot));
        FrontierWorldState runningState = state;
        EquipmentIssueObservation forgedSource = new EquipmentIssueObservation(new PhysicalObservationId("observation:tactical-function-forged"), issue.id(),
                assault.id(), militia, sword, new InventoryCustody.ContainerSlot(depot, slot + 1));
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleFixture.transition(runningState, assault.settlementId(), issue,
                PhysicalIntentStatus.CONFIRMED, Optional.of(forgedSource)));
        EquipmentIssueObservation receipt = new EquipmentIssueObservation(new PhysicalObservationId("observation:tactical-function-issue"), issue.id(),
                assault.id(), militia, sword, new InventoryCustody.ContainerSlot(depot, slot));
        state = PhysicalIntentLifecycleFixture.transition(state, assault.settlementId(), issue, PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
        org.junit.jupiter.api.Assertions.assertFalse(EquipmentPhysicalAuthority.pendingForContainer(state, depot));

        assertEquals(HumanTacticalFunction.ARMED_DEFENDER, HumanTacticalFunctionProjection.derive(state, militia));
        assertEquals(state.humanPopulation().resident(militia).name() + "\nUNIT READY", FrontierSceneLabels.actor(state, militia, false));
        assertEquals(receipt, ((PhysicalIntentTransition) FrontierWorldRuntimeDefinition.payloadCodecs().decode(
                new PhysicalIntentTransition(issue.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt)).type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(new PhysicalIntentTransition(issue.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt)))))
                .observation().orElseThrow());

        FrontierWorldState afterObservedLoss = state.withInventory(state.inventory().destroyObservedItem(sword, new InventoryCustody.Actor(militia)));
        assertEquals(afterObservedLoss, new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().decode(
                new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().encode(afterObservedLoss)));
        EquipmentIssueObservation foreignContainer = new EquipmentIssueObservation(new PhysicalObservationId("observation:foreign-equipment-storage"),
                issue.id(), assault.id(), militia, sword, new InventoryCustody.ContainerSlot(new SubjectId("container:foreign"), slot));
        assertThrows(IllegalArgumentException.class, () -> EquipmentIssueStateSupport.validateReceiptForRecovery(
                afterObservedLoss.inventory(), issue, foreignContainer));
    }

    @Test
    void exactWeaponWithoutAnOwnedTacticalAssignmentDoesNotCreateACombatClass() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:tactical-idle"), 91L));
        ResidentProfile resident = state.humanPopulation().residents().values().stream()
                .filter(value -> value.capability(HumanCapability.SECURITY) < 50).findFirst().orElseThrow();
        SubjectId depot = FrontierWorldState.depotId(resident.settlementId());
        int slot = state.inventory().firstFreeSlot(depot).orElseThrow();
        SubjectId sword = new SubjectId("item:tactical-idle-sword");
        ExactInventory stored = state.inventory().store(new ExactItemStack(sword, resident.settlementId(), "minecraft:iron_sword", 1,
                new InventoryCustody.ContainerSlot(depot, slot)));
        state = state.withInventory(stored.moveObservedItem(sword, new InventoryCustody.ContainerSlot(depot, slot), new InventoryCustody.Actor(resident.id())));

        assertEquals(HumanTacticalFunction.CIVILIAN, HumanTacticalFunctionProjection.derive(state, resident.id()));
    }

    @Test
    void exactEquipmentAndLeaderLossDeriveReadinessAndChangeTheSameSurvivorsColdCapability() {
        Fixture fixture = fixture();
        List<ProposedEvent> start = HiveSettlementAssaultProcess.planStart(fixture.state(),
                HiveSettlementAssaultProcess.start(fixture.task(), fixture.sighting(), 200L));
        FrontierWorldState state = StrategicObjectiveProcess.reduceTaskTransition(fixture.state(), fixture.hive(),
                assertInstanceOf(StrategicTaskTransition.class, start.getFirst().payload()));
        SettlementAssault assault = assertInstanceOf(SettlementAssaultStarted.class, start.get(1).payload()).assault();
        state = HiveSettlementAssaultProcess.reduceStarted(state, fixture.hive(), new SettlementAssaultStarted(assault, io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultExecutionAuthority.admission(state, assault)));
        SubjectId leader = assault.defenderUnit().leaderId();
        SubjectId fighter = assault.defenderIds().stream().filter(id -> !id.equals(leader)).findFirst().orElseThrow();
        SubjectId support = assault.defenderIds().stream().filter(id -> !id.equals(leader) && !id.equals(fighter)).findFirst().orElseThrow();

        assertEquals(SettlementDefenderReadinessStatus.IMPROVISED,
                SettlementDefenderReadinessProjection.derive(state, assault).status());
        assertEquals(FixedScalar.ONE, FrontierCombatRules.damage(state, fighter));
        state = arm(state, assault, leader, "leader");
        state = arm(state, assault, fighter, "fighter");
        state = arm(state, assault, support, "support");
        assertEquals(SettlementDefenderReadinessStatus.READY, SettlementDefenderReadinessProjection.derive(state, assault).status());
        assertEquals(FixedScalar.whole(3), FrontierCombatRules.damage(state, fighter));

        SubjectId fighterSword = state.inventory().actorItems(fighter).getFirst().id();
        state = state.withInventory(state.inventory().destroyObservedItem(fighterSword, new InventoryCustody.Actor(fighter)));
        assertEquals(SettlementDefenderReadinessStatus.READY, SettlementDefenderReadinessProjection.derive(state, assault).status());
        assertEquals(FixedScalar.ONE, FrontierCombatRules.damage(state, fighter), "weapon loss changes the same exact defender");

        SubjectId leaderSword = state.inventory().actorItems(leader).getFirst().id();
        state = state.withInventory(state.inventory().destroyObservedItem(leaderSword, new InventoryCustody.Actor(leader)));
        ActorLocation leaderLocation = state.actorLocations().get(leader);
        java.util.Map<SubjectId, ActorLocation> actors = new java.util.LinkedHashMap<>(state.actorLocations());
        actors.put(leader, new ActorLocation(leaderLocation.body(), ActorCondition.dead(), leaderLocation.kind(), leaderLocation.navigationProfile()));
        state = state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors));
        assertEquals(SettlementDefenderReadinessStatus.DEGRADED, SettlementDefenderReadinessProjection.derive(state, assault).status());
        assertEquals(FixedScalar.ONE, FrontierCombatRules.damage(state, fighter), "leader loss degrades survivors without replacing them");
        assertEquals(state.humanPopulation().resident(fighter).name() + "\nUNIT DEGRADED", FrontierSceneLabels.actor(state, fighter, false));
        assertEquals(state, new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().decode(
                new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().encode(state)));
    }

    @Test
    void resolvedAssaultReturnsTheSameExactWeaponOnlyToItsNamedEmptyDepotSlot() {
        Fixture fixture = fixture();
        List<ProposedEvent> start = HiveSettlementAssaultProcess.planStart(fixture.state(), HiveSettlementAssaultProcess.start(fixture.task(), fixture.sighting(), 200L));
        FrontierWorldState state = StrategicObjectiveProcess.reduceTaskTransition(fixture.state(), fixture.hive(),
                assertInstanceOf(StrategicTaskTransition.class, start.getFirst().payload()));
        SettlementAssault assault = assertInstanceOf(SettlementAssaultStarted.class, start.get(1).payload()).assault();
        state = HiveSettlementAssaultProcess.reduceStarted(state, fixture.hive(), new SettlementAssaultStarted(assault, io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultExecutionAuthority.admission(state, assault)));
        SubjectId depot = FrontierWorldState.depotId(assault.settlementId()), resident = assault.defenderIds().getFirst();
        int sourceSlot = state.inventory().firstFreeSlot(depot).orElseThrow(); SubjectId sword = new SubjectId("item:tactical-return-sword");
        state = state.withInventory(state.inventory().withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE)
                .store(new ExactItemStack(sword, assault.settlementId(), "minecraft:iron_sword", 1, new InventoryCustody.ContainerSlot(depot, sourceSlot)))
                .moveObservedItem(sword, new InventoryCustody.ContainerSlot(depot, sourceSlot), new InventoryCustody.Actor(resident)));
        state = HiveSettlementAssaultProcess.reduceResolved(state, fixture.hive(), HiveSettlementAssaultProcess.resolution(state, assault, SettlementAssaultOutcome.ABORTED));
        SettlementAssault resolved = state.strategicPlans().settlementAssaults().get(assault.id());
        int returnSlot = state.firstFreeContainerSlot(depot).orElseThrow();

        state = selectReturnVisit(state, resolved, 250L);
        assertFalse(EquipmentPhysicalAuthority.actorCommitted(state, resident), "selected travel must not start a remote physical hand transfer");
        assertTrue(DefenderEquipmentReturnProcess.plan(state, DefenderEquipmentReturnProcess.review(resolved, 251L)).stream()
                .noneMatch(event -> event.payload() instanceof PhysicalIntentPrepared), "remote selected visitor has not arrived");
        var selectedCodec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        state = selectedCodec.decode(selectedCodec.encode(state));
        state = atReturnService(state, resolved, 252L);
        PhysicalIntent returned = DefenderEquipmentReturnProcess.plan(state, DefenderEquipmentReturnProcess.review(resolved, 254L)).stream()
                .map(ProposedEvent::payload).filter(PhysicalIntentPrepared.class::isInstance).map(PhysicalIntentPrepared.class::cast)
                .map(PhysicalIntentPrepared::intent).findFirst().orElseThrow();
        assertEquals(PhysicalIntentKind.EQUIPMENT_RETURN, returned.kind());
        assertEquals(new io.farfrontier.palemirror.frontier.v3.api.PhysicalContainerSlot(depot, returnSlot), returned.targetSlot().orElseThrow());
        state = atStorageStation(state, resident, depot);
        state = PhysicalIntentLifecycleFixture.transition(PhysicalIntentLifecycleFixture.prepare(state, assault.settlementId(), returned),
                assault.settlementId(), returned, PhysicalIntentStatus.RUNNING, Optional.empty());
        FrontierWorldState running = state;
        EquipmentReturnObservation forged = new EquipmentReturnObservation(new PhysicalObservationId("observation:tactical-return-forged"), returned.id(),
                assault.id(), resident, sword, new InventoryCustody.ContainerSlot(depot, returnSlot + 1));
        assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleFixture.transition(running, assault.settlementId(), returned,
                PhysicalIntentStatus.CONFIRMED, Optional.of(forged)));
        EquipmentReturnObservation receipt = new EquipmentReturnObservation(new PhysicalObservationId("observation:tactical-return"), returned.id(),
                assault.id(), resident, sword, new InventoryCustody.ContainerSlot(depot, returnSlot));
        state = PhysicalIntentLifecycleFixture.transition(state, assault.settlementId(), returned, PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));

        assertEquals(new InventoryCustody.ContainerSlot(depot, returnSlot), state.inventory().items().get(sword).custody());
        assertEquals(HumanTacticalFunction.CIVILIAN, HumanTacticalFunctionProjection.derive(state, resident));
        assertEquals(state, new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().decode(
                new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().encode(state)));
        assertEquals(receipt, ((PhysicalIntentTransition) FrontierWorldRuntimeDefinition.payloadCodecs().decode(
                new PhysicalIntentTransition(returned.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt)).type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(new PhysicalIntentTransition(returned.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt)))))
                .observation().orElseThrow());

        FrontierWorldState afterPlayerPickup = state.withInventory(state.inventory().moveObservedItem(sword,
                new InventoryCustody.ContainerSlot(depot, returnSlot), new InventoryCustody.Player(UUID.fromString("00000000-0000-0000-0000-000000000043"))));
        assertEquals(afterPlayerPickup, new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().decode(
                new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().encode(afterPlayerPickup)));
    }

    @Test
    void resolvedOwnerAndDepartedStationDoNotRejectAnAlreadyAttemptedExactIssue() {
        for (boolean recovered : List.of(false, true)) {
            Fixture fixture = fixture();
            var start = HiveSettlementAssaultProcess.planStart(fixture.state(),
                    HiveSettlementAssaultProcess.start(fixture.task(), fixture.sighting(), 200L));
            var state = StrategicObjectiveProcess.reduceTaskTransition(fixture.state(), fixture.hive(),
                    assertInstanceOf(StrategicTaskTransition.class, start.getFirst().payload()));
            var assault = assertInstanceOf(SettlementAssaultStarted.class, start.get(1).payload()).assault();
            state = HiveSettlementAssaultProcess.reduceStarted(state, fixture.hive(),
                    new SettlementAssaultStarted(assault, SettlementAssaultExecutionAuthority.admission(state, assault)));
            var depot = FrontierWorldState.depotId(assault.settlementId());
            int slot = state.inventory().firstFreeSlot(depot).orElseThrow();
            var sword = new SubjectId("item:late-exact-weapon");
            state = state.withInventory(state.inventory().withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED)
                    .withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE).store(new ExactItemStack(
                            sword, assault.settlementId(), "minecraft:iron_sword", 1, new InventoryCustody.ContainerSlot(depot, slot))));
            var intended = assault.defenderIds().stream().sorted().filter(id -> !id.equals(assault.defenderUnit().leaderId())).findFirst().orElseThrow();
            state = atStorageStation(state, intended, depot);
            var issue = DefenderEquipmentProcess.plan(state, DefenderEquipmentProcess.review(assault, 201L)).stream()
                    .map(ProposedEvent::payload).filter(PhysicalIntentPrepared.class::isInstance).map(PhysicalIntentPrepared.class::cast)
                    .map(PhysicalIntentPrepared::intent).findFirst().orElseThrow();
            var actor = EquipmentServiceBoundary.capability(issue).actorId(issue);
            state = atStorageStation(state, actor, depot);
            state = PhysicalIntentLifecycleFixture.prepare(state, assault.settlementId(), issue);
            var beforeAttempt = state;
            assertThrows(IllegalArgumentException.class, () -> EquipmentServiceBoundary.validateInspection(beforeAttempt, issue));
            state = PhysicalIntentLifecycleFixture.transition(state, assault.settlementId(), issue, PhysicalIntentStatus.RUNNING, Optional.empty());
            state = HiveSettlementAssaultProcess.reduceResolved(state, fixture.hive(),
                    HiveSettlementAssaultProcess.resolution(state, assault, SettlementAssaultOutcome.ABORTED));
            var resolved = state.strategicPlans().settlementAssaults().get(assault.id());
            org.junit.jupiter.api.Assertions.assertTrue(DefenderEquipmentReturnProcess.plan(state,
                    DefenderEquipmentReturnProcess.review(resolved, 250L)).stream().anyMatch(event ->
                            event.payload() instanceof io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled),
                    "return review must not disappear before the unresolved issue can confirm");
            // Pose may change after an observed hand-off; it cannot renew or invalidate that effect.
            var locations = new java.util.LinkedHashMap<>(state.actorLocations());
            locations.put(actor, locations.get(actor).withBody(
                    FrontierTestPositions.bodyAboveSupport(locations.get(actor).supportingSurface().support().offset(10, 0, 0))));
            state = state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(locations));
            var terminalOwner = state;
            assertThrows(IllegalArgumentException.class, () -> EquipmentIssueStateSupport.validateIntent(terminalOwner, issue));
            if (recovered) state = PhysicalIntentLifecycleFixture.transition(state, assault.settlementId(), issue,
                    PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty());
            var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
            state = codec.decode(codec.encode(state));
            var current = state.physicalIntents().get(issue.id());
            EquipmentServiceBoundary.validateInspection(state, current);
            var inspectable = state;
            var stale = current.withStatus(PhysicalIntentStatus.PREPARED, Optional.empty());
            assertThrows(IllegalArgumentException.class, () -> EquipmentServiceBoundary.validateContinuation(inspectable, stale));
            var receipt = new EquipmentIssueObservation(new PhysicalObservationId("observation:late-exact-weapon"), issue.id(),
                    assault.id(), actor, sword, new InventoryCustody.ContainerSlot(depot, slot));
            var forged = new EquipmentIssueObservation(new PhysicalObservationId("observation:late-forged-weapon"), issue.id(),
                    assault.id(), actor, sword, new InventoryCustody.ContainerSlot(depot, slot + 1));
            assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleFixture.transition(inspectable,
                    assault.settlementId(), issue, PhysicalIntentStatus.CONFIRMED, Optional.of(forged)));
            state = PhysicalIntentLifecycleFixture.transition(state, assault.settlementId(), issue, PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
            assertEquals(new InventoryCustody.Actor(actor), state.inventory().items().get(sword).custody());
            assertEquals(SettlementAssaultStatus.RESOLVED, state.strategicPlans().settlementAssaults().get(assault.id()).status());
            org.junit.jupiter.api.Assertions.assertFalse(EquipmentPhysicalAuthority.pendingForContainer(state, depot));
            org.junit.jupiter.api.Assertions.assertTrue(DefenderEquipmentReturnProcess.plan(state,
                    DefenderEquipmentReturnProcess.review(resolved, 251L)).stream().anyMatch(event ->
                            event.payload() instanceof DefenderEquipmentReturnVisitChanged selected
                                    && selected.selected().isPresent()),
                    "late exact custody must become returnable, not revive a resolved defence assignment");
            assertEquals(state, codec.decode(codec.encode(state)));
            var confirmed = state;
            assertThrows(IllegalArgumentException.class, () -> PhysicalIntentLifecycleFixture.transition(confirmed,
                    assault.settlementId(), issue, PhysicalIntentStatus.CONFIRMED, Optional.of(receipt)));
        }
    }

    @Test
    void equipmentChoosesPermittedWarehouseAndReturnsToAnotherAfterSourceFills() {
        for (boolean wrongTier : List.of(false, true)) {
            Fixture fixture = fixture();
            var start = HiveSettlementAssaultProcess.planStart(fixture.state(),
                    HiveSettlementAssaultProcess.start(fixture.task(), fixture.sighting(), 200L));
            var state = StrategicObjectiveProcess.reduceTaskTransition(fixture.state(), fixture.hive(),
                    assertInstanceOf(StrategicTaskTransition.class, start.getFirst().payload()));
            var assault = assertInstanceOf(SettlementAssaultStarted.class, start.get(1).payload()).assault();
            state = HiveSettlementAssaultProcess.reduceStarted(state, fixture.hive(),
                    new SettlementAssaultStarted(assault, SettlementAssaultExecutionAuthority.admission(state, assault)));
            var rejectedStore = new SubjectId("container:equipment-forbidden");
            var source = new SubjectId("container:equipment-source");
            var receiver = new SubjectId("container:equipment-receiver");
            var filter = wrongTier ? ResourceStorageFilter.all()
                    : new ResourceStorageFilter(false, java.util.Set.of("minecraft:cobblestone"), Map.of());
            state = equipmentStorage(state, assault.settlementId(), rejectedStore, 18, filter,
                    wrongTier ? ResourceStorageRole.LOCAL_BUFFER : ResourceStorageRole.WAREHOUSE, 100);
            state = equipmentStorage(state, assault.settlementId(), source, 20, ResourceStorageFilter.all(),
                    ResourceStorageRole.WAREHOUSE, 0);
            var sword = new SubjectId("item:equipment-permitted");
            state = state.withInventory(state.inventory()
                    .store(new ExactItemStack(new SubjectId("item:aaa-equipment-forbidden"), assault.settlementId(),
                            "minecraft:iron_sword", 1, new InventoryCustody.ContainerSlot(rejectedStore, 0)))
                    .store(new ExactItemStack(sword, assault.settlementId(),
                            "minecraft:iron_sword", 1, new InventoryCustody.ContainerSlot(source, 0))));
            var remote = state;
            var intended = assault.defenderIds().stream().sorted().filter(id -> !id.equals(assault.defenderUnit().leaderId())).findFirst().orElseThrow();
            state = atStorageStation(state, intended, source);
            var issue = DefenderEquipmentProcess.plan(state, DefenderEquipmentProcess.review(assault, 201L)).stream()
                    .map(ProposedEvent::payload).filter(PhysicalIntentPrepared.class::isInstance).map(PhysicalIntentPrepared.class::cast)
                    .map(PhysicalIntentPrepared::intent).findFirst().orElseThrow();
            assertEquals(source, EquipmentPhysicalAuthority.container(issue));
            assertEquals(sword, issue.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.EQUIPMENT));
            var actor = EquipmentServiceBoundary.capability(issue).actorId(issue);
            assertThrows(IllegalArgumentException.class, () -> EquipmentServiceBoundary.requireMayBegin(remote, issue),
                    "universal stock discovery is not physical arrival");
            state = atStorageStation(state, actor, source);
            state = PhysicalIntentLifecycleFixture.prepare(state, assault.settlementId(), issue);
            assertEquals(state, new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().decode(
                    new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().encode(state)));
            state = PhysicalIntentLifecycleFixture.transition(state, assault.settlementId(), issue, PhysicalIntentStatus.RUNNING, Optional.empty());
            state = PhysicalIntentLifecycleFixture.transition(state, assault.settlementId(), issue, PhysicalIntentStatus.CONFIRMED,
                    Optional.of(new EquipmentIssueObservation(new PhysicalObservationId("observation:equipment-permitted"), issue.id(),
                            assault.id(), actor, sword, new InventoryCustody.ContainerSlot(source, 0))));
            state = HiveSettlementAssaultProcess.reduceResolved(state, fixture.hive(),
                    HiveSettlementAssaultProcess.resolution(state, assault, SettlementAssaultOutcome.ABORTED));
            // Fill the original source's real slots; the retained weapon must not disappear or wait for that exact chest.
            for (int slot = 0; slot < state.inventory().containers().get(source).slotCount(); slot++)
                state = state.withInventory(state.inventory().store(new ExactItemStack(new SubjectId("item:equipment-fill-" + slot),
                        assault.settlementId(), "minecraft:iron_sword", 1, new InventoryCustody.ContainerSlot(source, slot))));
            state = equipmentStorage(state, assault.settlementId(), receiver, 24, ResourceStorageFilter.all(),
                    ResourceStorageRole.WAREHOUSE, 0);
            var resolved = state.strategicPlans().settlementAssaults().get(assault.id());
            state = selectReturnVisit(state, resolved, 251L);
            state = atReturnService(state, resolved, 252L);
            var returned = DefenderEquipmentReturnProcess.plan(state, DefenderEquipmentReturnProcess.review(resolved, 254L)).stream()
                    .map(ProposedEvent::payload).filter(PhysicalIntentPrepared.class::isInstance).map(PhysicalIntentPrepared.class::cast)
                    .map(PhysicalIntentPrepared::intent).findFirst().orElseThrow();
            assertEquals(receiver, EquipmentPhysicalAuthority.container(returned));
            state = atStorageStation(state, actor, receiver);
            state = PhysicalIntentLifecycleFixture.prepare(state, assault.settlementId(), returned);
            state = PhysicalIntentLifecycleFixture.transition(state, assault.settlementId(), returned, PhysicalIntentStatus.RUNNING, Optional.empty());
            state = PhysicalIntentLifecycleFixture.transition(state, assault.settlementId(), returned, PhysicalIntentStatus.CONFIRMED,
                    Optional.of(new EquipmentReturnObservation(new PhysicalObservationId("observation:equipment-alternative-return"), returned.id(),
                            assault.id(), actor, sword, EquipmentServiceBoundary.targetSlot(returned))));
            assertEquals(new InventoryCustody.ContainerSlot(receiver, 0), state.inventory().items().get(sword).custody());
            assertEquals(state, new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().decode(
                    new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().encode(state)));
        }
    }

    private static FrontierWorldState equipmentStorage(FrontierWorldState state, SubjectId owner, SubjectId id, int offset,
            ResourceStorageFilter filter, ResourceStorageRole role, int priority) {
        var settlement = state.bootstrap().settlements().stream().filter(value -> value.id().equals(owner)).findFirst().orElseThrow();
        var station = KnownPedestrianGround.forFrontier(state).at(settlement.anchor().x() + offset, settlement.anchor().z() + 20);
        var endpoint = new ShipmentEndpoint.Storage(owner, id, station, Optional.empty(), station);
        var record = new ContainerRecord(id, owner, 27, ContainerPurpose.RESOURCE_STORAGE, Optional.empty(),
                Optional.of(new ResourceStoragePolicy(endpoint, filter, priority, role)));
        state = new ResourceStorageDeclared(record, station.support().offset(1, 1, 0)).apply(state, owner);
        return state.withInventory(state.inventory().withSurfaceStatus(id, ContainerSurfaceStatus.PREPARED)
                .withSurfaceStatus(id, ContainerSurfaceStatus.ACTIVE));
    }

    private static Fixture fixture() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:tactical-function"), 91L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        Bioform scout = state.bootstrap().hive().bioforms().stream().filter(Bioform::isScout).findFirst().orElseThrow();
        state = FrontierTestPositions.deployBioform(state, scout.id(), FrontierTestPositions.bodyAboveSupport(settlement.anchor()));
        for (Bioform bioform : state.bootstrap().hive().bioforms().stream()
                .filter(value -> value.isDefender() || value.isExplosiveAssaulter() || value.isOverseer()).toList()) {
            state = FrontierTestPositions.deployBioform(state, bioform.id(),
                    state.actorLocations().get(bioform.id()).body().offset(0, -1, 0));
        }
        HiveSettlementKnowledge.Sighting sighting = new HiveSettlementKnowledge.Sighting(settlement.id(), scout.id(), settlement.anchor(), 100L);
        InfectionCell cell = InfectionCell.at(settlement.anchor());
        FixedRatio intensity = new FixedRatio(FixedScalar.ONE);
        state = state.withInfection(cell, intensity);
        HiveTerritoryKnowledge territory = new HiveTerritoryKnowledge(Map.of(cell,
                new HiveTerritoryKnowledge.Belief(cell, intensity, scout.id(), settlement.anchor(), 100L)));
        SubjectId hive = state.bootstrap().hive().id();
        StrategicObjective objective = new StrategicObjective(new SubjectId("objective:tactical-function"), hive,
                StrategicObjectiveKind.HIVE_ASSAULT_SETTLEMENT, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        StrategicTask task = new StrategicTask(new SubjectId("task:tactical-function"), objective.id(), hive,
                StrategicTaskKind.ASSAULT_SETTLEMENT, Optional.empty(), List.of(StrategicTaskRequirement.AVAILABLE_HIVE_GUARD,
                StrategicTaskRequirement.AVAILABLE_HIVE_BOMBER), List.of(), StrategicTaskStatus.PENDING);
        StrategicPlanState plans = StrategicPlanState.empty().withHiveSettlementKnowledge(new HiveSettlementKnowledge(Map.of(settlement.id(), sighting)))
                .withHiveTerritoryKnowledge(territory).withHiveDoctrine(new HiveDoctrineState(HiveDoctrine.INTERDICT, 100L))
                .addObjective(objective).addTask(task);
        return new Fixture(state.withStrategicPlans(plans), hive, task, sighting);
    }

    @Test void selectedOrdinaryWarehouseEquipmentSurvivesSnapshotAndSelectedReceiptWithoutSearch() throws Exception {
        Fixture fixture = fixture();
        var start = HiveSettlementAssaultProcess.planStart(fixture.state(), HiveSettlementAssaultProcess.start(fixture.task(), fixture.sighting(), 200L));
        var state = StrategicObjectiveProcess.reduceTaskTransition(fixture.state(), fixture.hive(),
                assertInstanceOf(StrategicTaskTransition.class, start.getFirst().payload()));
        var assault = assertInstanceOf(SettlementAssaultStarted.class, start.get(1).payload()).assault();
        state = HiveSettlementAssaultProcess.reduceStarted(state, fixture.hive(), new SettlementAssaultStarted(assault, SettlementAssaultExecutionAuthority.admission(state, assault)));
        var actor = assault.defenderIds().getFirst();
        var home = FrontierWorldStateSupport.settlement(state.bootstrap(), assault.settlementId());
        var station = KnownPedestrianGround.forFrontier(state).at(home.anchor().x() + 20, home.anchor().z() + 20);
        var warehouse = new SubjectId("container:equipment-warehouse");
        var endpoint = new ShipmentEndpoint.Storage(home.id(), warehouse, station, Optional.empty(), station);
        var record = new ContainerRecord(warehouse, home.id(), 27, ContainerPurpose.RESOURCE_STORAGE, Optional.empty(),
                Optional.of(new ResourceStoragePolicy(endpoint, ResourceStorageFilter.all(), 0, ResourceStorageRole.WAREHOUSE)));
        state = new ResourceStorageDeclared(record, station.support().offset(0, 1, 0)).apply(state, home.id());
        var weapon = new SubjectId("item:equipment-warehouse-sword");
        var slot = new InventoryCustody.ContainerSlot(warehouse, 0);
        state = state.withInventory(state.inventory().withSurfaceStatus(warehouse, ContainerSurfaceStatus.PREPARED)
                .withSurfaceStatus(warehouse, ContainerSurfaceStatus.ACTIVE).store(new ExactItemStack(weapon, home.id(), "minecraft:iron_sword", 1, slot)));
        state = atStorageStation(state, actor, warehouse);
        var roles = io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.assaultEquipmentIssue(assault.id(), actor, weapon, warehouse);
        var issue = new PhysicalIntent(new PhysicalIntentId("intent:ordinary-warehouse-issue"), PhysicalIntentKind.EQUIPMENT_ISSUE,
                PhysicalIntentStatus.PREPARED, home.id(), roles, new FixedPosition(FixedScalar.ZERO, FixedScalar.ZERO, FixedScalar.ZERO),
                0, PhysicalPostcondition.EQUIPMENT_ISSUED_OBSERVED,
                new EquipmentServiceRequest(new ServiceAccessDemand.Identity(ServiceAccessDemand.Kind.DEFENDER_EQUIPMENT, assault.id(), actor, warehouse), 100L),
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT);
        var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        try (var ignored = io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning.bind((geometry, origin, target) -> {
            throw new AssertionError("selected equipment receipt/recovery must not rediscover a path");
        })) {
            state = PhysicalIntentLifecycleFixture.prepare(state, home.id(), issue);
            var duplicate = new PhysicalIntent(new PhysicalIntentId("intent:ordinary-warehouse-duplicate"), issue.kind(), issue.status(),
                    issue.causeSubjectId(), issue.roles(), issue.origin(), 0, issue.postcondition(), issue.equipmentServiceRequest().orElseThrow(), issue.lifecycleOwner());
            var committed = state;
            assertThrows(IllegalArgumentException.class, () -> EquipmentIssueStateSupport.validateIntent(committed, duplicate),
                    "two PREPARED transfers cannot reserve the same exact item or resident hand");
            state = PhysicalIntentLifecycleFixture.transition(state, home.id(), issue, PhysicalIntentStatus.RUNNING, Optional.empty());
            var recovered = codec.decode(codec.encode(state));
            assertEquals(state, recovered);
            var receipt = new EquipmentIssueObservation(new PhysicalObservationId("observation:ordinary-warehouse-issue"),
                    issue.id(), assault.id(), actor, weapon, slot);
            var live = PhysicalIntentLifecycleFixture.transition(state, home.id(), issue, PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
            state = PhysicalIntentLifecycleFixture.transition(recovered, home.id(), issue, PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
            assertEquals(live, state);
            state = HiveSettlementAssaultProcess.reduceResolved(state, fixture.hive(), HiveSettlementAssaultProcess.resolution(state, assault, SettlementAssaultOutcome.ABORTED));
            var returned = new PhysicalIntent(new PhysicalIntentId("intent:ordinary-warehouse-return"), PhysicalIntentKind.EQUIPMENT_RETURN,
                    PhysicalIntentStatus.PREPARED, home.id(), io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding
                    .assaultEquipmentReturn(assault.id(), actor, weapon, warehouse), issue.origin(), 0,
                    PhysicalPostcondition.EQUIPMENT_RETURNED_OBSERVED, new io.farfrontier.palemirror.frontier.v3.api.PhysicalContainerSlot(warehouse, 0),
                    new EquipmentServiceRequest(new ServiceAccessDemand.Identity(ServiceAccessDemand.Kind.DEFENDER_EQUIPMENT, assault.id(), actor, warehouse), 200L), issue.lifecycleOwner());
            state = PhysicalIntentLifecycleFixture.prepare(state, home.id(), returned);
            state = PhysicalIntentLifecycleFixture.transition(state, home.id(), returned, PhysicalIntentStatus.RUNNING, Optional.empty());
            state = PhysicalIntentLifecycleFixture.transition(codec.decode(codec.encode(state)), home.id(), returned, PhysicalIntentStatus.CONFIRMED,
                    Optional.of(new EquipmentReturnObservation(new PhysicalObservationId("observation:ordinary-warehouse-return"), returned.id(), assault.id(), actor, weapon, slot)));
            assertEquals(slot, state.inventory().items().get(weapon).custody());
            assertEquals(state, codec.decode(codec.encode(state)));
        }
    }

    @Test void equipmentTransfersUseTheSharedTurnWithoutRemoteReservationsAndRetainInspectionAfterDeparture() {
        Fixture fixture = fixture();
        var start = HiveSettlementAssaultProcess.planStart(fixture.state(), HiveSettlementAssaultProcess.start(fixture.task(), fixture.sighting(), 200L));
        var state = StrategicObjectiveProcess.reduceTaskTransition(fixture.state(), fixture.hive(),
                assertInstanceOf(StrategicTaskTransition.class, start.getFirst().payload()));
        var assault = assertInstanceOf(SettlementAssaultStarted.class, start.get(1).payload()).assault();
        state = HiveSettlementAssaultProcess.reduceStarted(state, fixture.hive(),
                new SettlementAssaultStarted(assault, SettlementAssaultExecutionAuthority.admission(state, assault)));
        var store = FrontierWorldState.depotId(assault.settlementId());
        state = state.withInventory(state.inventory().withSurfaceStatus(store, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(store, ContainerSurfaceStatus.ACTIVE));
        for (int n = 0; n < 2; n++) state = state.withInventory(state.inventory().store(new ExactItemStack(new SubjectId("item:access-weapon-" + n),
                assault.settlementId(), "minecraft:iron_sword", 1, new InventoryCustody.ContainerSlot(store, state.inventory().firstFreeSlot(store).orElseThrow()))));
        var intended = assault.defenderIds().stream().sorted().filter(id -> !id.equals(assault.defenderUnit().leaderId())).findFirst().orElseThrow();
        state = atStorageStation(state, intended, store);
        var first = plannedIssue(state, assault, 201L);
        var before = state;
        state = PhysicalIntentLifecycleFixture.prepare(state, assault.settlementId(), first);
        assertTrue(ServiceAccessCapabilities.wakePoints(before, state).contains(store));
        var nextActor = assault.defenderIds().stream().sorted().filter(id -> !id.equals(intended)).findFirst().orElseThrow();
        state = atStorageStation(state, nextActor, store);
        var second = plannedIssue(state, assault, 202L);
        state = PhysicalIntentLifecycleFixture.prepare(state, assault.settlementId(), second);
        state = state.withActorBody(intended, state.actorLocations().get(intended).body().offset(20, 0, 20));
        state = state.withActorBody(nextActor, state.actorLocations().get(nextActor).body().offset(20, 0, 20));
        assertTrue(ServiceAccessCapabilities.current(state, store).isEmpty(), "stock intent without a real approach/contact cannot block the warehouse");
        assertFalse(EquipmentServiceBoundary.serviceAvailable(state, first));
        var firstActor = EquipmentServiceBoundary.capability(first).actorId(first);
        var secondActor = EquipmentServiceBoundary.capability(second).actorId(second);
        assertNotEquals(firstActor, secondActor);
        state = atStorageStation(atStorageStation(state, firstActor, store), secondActor, store);
        assertTrue(EquipmentServiceBoundary.serviceAvailable(state, first));
        assertFalse(EquipmentServiceBoundary.serviceAvailable(state, second));
        var occupied = state;
        assertThrows(IllegalArgumentException.class, () -> EquipmentServiceBoundary.requireMayBegin(occupied, second), "arrival alone cannot bypass the current turn");
        var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        state = codec.decode(codec.encode(state));
        assertTrue(EquipmentServiceBoundary.serviceAvailable(state, first));
        assertFalse(EquipmentServiceBoundary.serviceAvailable(state, second));
        state = PhysicalIntentLifecycleFixture.transition(state, assault.settlementId(), first, PhysicalIntentStatus.RUNNING, Optional.empty());
        state = state.withActorBody(firstActor, state.actorLocations().get(firstActor).body().offset(20, 0, 20));
        var running = state.physicalIntents().get(first.id());
        EquipmentServiceBoundary.validateInspection(state, running);
        var pending = state;
        assertThrows(IllegalArgumentException.class, () -> EquipmentServiceBoundary.requireMayBegin(pending, second), "departure cannot drop the unresolved physical fence");
        state = PhysicalIntentLifecycleFixture.transition(state, assault.settlementId(), first, PhysicalIntentStatus.CONFIRMED,
                Optional.of(new EquipmentIssueObservation(new PhysicalObservationId("observation:access-weapon"), first.id(), assault.id(), firstActor,
                        first.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.EQUIPMENT),
                        (InventoryCustody.ContainerSlot) state.inventory().items().get(first.roles().require(
                                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.EQUIPMENT)).custody())));
        assertTrue(EquipmentServiceBoundary.serviceAvailable(state, second));
        EquipmentServiceBoundary.requireMayBegin(state, second);
    }

    @Test
    void selectedReturnSourceDepartureRetiresOnlyAnUnbegunVisitBeforeObservedStockMutation() throws Exception {
        for (boolean prepared : List.of(false, true)) for (boolean destroyed : List.of(false, true)) {
            var state = selectedReturnFixture();
            var parent = state.strategicPlans().settlementAssaults().values().stream()
                    .filter(candidate -> candidate.equipmentReturnVisit().isPresent()).findFirst().orElseThrow();
            var visit = parent.equipmentReturnVisit().orElseThrow();
            if (prepared) {
                state = atReturnService(state, parent, 251L);
                var intent = DefenderEquipmentReturnProcess.plan(state, DefenderEquipmentReturnProcess.review(parent, 253L)).stream()
                        .map(ProposedEvent::payload).filter(PhysicalIntentPrepared.class::isInstance).map(PhysicalIntentPrepared.class::cast)
                        .map(PhysicalIntentPrepared::intent).findFirst().orElseThrow();
                state = PhysicalIntentLifecycleFixture.prepare(state, parent.settlementId(), intent);
            }
            var item = state.inventory().items().get(visit.itemId());
            io.farfrontier.palemirror.frontier.v3.api.FrontierPayload departure = destroyed
                    ? new ExactItemDestroyed(item.id(), item.custody(), "test:selected-return-loss")
                    : new ExactItemCustodyChanged(item.id(), item.custody(),
                            new InventoryCustody.ContainerSlot(visit.targetSlot().containerId(), visit.targetSlot().slot()));
            var definition = FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), 41L);
            var accepted = assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan.Accepted.class,
                    definition.commandPlanner().plan(state, equipmentCommand(state, departure)));
            var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
            var restored = codec.decode(codec.encode(state));
            var before = restored;
            assertThrows(IllegalArgumentException.class, () -> replayEquipmentEvent(before, accepted.events().getLast(), 500L));
            try (var ignored = PedestrianRoutePlanning.bind((geometry, origin, target) -> { throw new AssertionError("stock departure replay searched"); })) {
                for (var event : accepted.events()) {
                    if (!(event.payload() instanceof io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect))
                        restored = replayEquipmentEvent(restored, event, 500L);
                }
            }
            assertTrue(restored.strategicPlans().settlementAssaults().get(parent.id()).equipmentReturnVisit().isEmpty());
            assertFalse(restored.physicalIntents().containsKey(visit.transferId()));
            assertTrue(restored.inventory().actorItems(visit.actorId()).stream().noneMatch(held -> held.id().equals(item.id())));
            assertEquals(restored, codec.decode(codec.encode(restored)));
        }
    }

    @Test
    void selectedReturnCannotRetireAnUninspectedAttemptAndKeepsItsReceiptUntilAccessClears() {
        for (boolean recovered : List.of(false, true)) {
            var state = selectedReturnFixture();
            var parent = state.strategicPlans().settlementAssaults().values().stream()
                    .filter(candidate -> candidate.equipmentReturnVisit().isPresent()).findFirst().orElseThrow();
            var visit = parent.equipmentReturnVisit().orElseThrow();
            state = atReturnService(state, parent, 251L);
            visit = state.strategicPlans().settlementAssaults().get(parent.id()).equipmentReturnVisit().orElseThrow();
            var intent = DefenderEquipmentReturnProcess.plan(state, DefenderEquipmentReturnProcess.review(parent, 253L)).stream()
                    .map(ProposedEvent::payload).filter(PhysicalIntentPrepared.class::isInstance).map(PhysicalIntentPrepared.class::cast)
                    .map(PhysicalIntentPrepared::intent).findFirst().orElseThrow();
            state = PhysicalIntentLifecycleFixture.transition(PhysicalIntentLifecycleFixture.prepare(state, parent.settlementId(), intent),
                    parent.settlementId(), intent, PhysicalIntentStatus.RUNNING, Optional.empty());
            if (recovered) state = PhysicalIntentLifecycleFixture.transition(state, parent.settlementId(), intent,
                    PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty());
            var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
            state = codec.decode(codec.encode(state));
            var attempted = state;
            var retirement = new ProposedEvent(parent.id(), new DefenderEquipmentReturnVisitChanged(parent.id(), Optional.of(visit), Optional.empty()));
            assertThrows(IllegalArgumentException.class, () -> replayEquipmentEvent(attempted, retirement, 500L));
            var item = state.inventory().items().get(visit.itemId());
            var departure = new ExactItemDestroyed(item.id(), item.custody(), "test:attempted-return-loss");
            var definition = FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), 41L);
            assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan.Rejected.class,
                    definition.commandPlanner().plan(attempted, equipmentCommand(attempted, departure)));
            state = PhysicalIntentLifecycleFixture.transition(state, parent.settlementId(), intent, PhysicalIntentStatus.CONFIRMED,
                    Optional.of(new EquipmentReturnObservation(new PhysicalObservationId("observation:selected-return-complete"), intent.id(),
                            parent.id(), visit.actorId(), visit.itemId(), new InventoryCustody.ContainerSlot(visit.targetSlot().containerId(), visit.targetSlot().slot()))));
            var confirmed = state;
            assertFalse(DefenderEquipmentReturnProcess.mayCompact(state, state.physicalIntents().get(intent.id())));
            assertThrows(IllegalArgumentException.class, () -> replayEquipmentEvent(confirmed, retirement, 501L));
            state = state.withActorBody(visit.actorId(), state.actorLocations().get(visit.actorId()).body().offset(20, 0, 20));
            state = replayEquipmentEvent(state, retirement, 502L);
            assertTrue(DefenderEquipmentReturnProcess.mayCompact(state, state.physicalIntents().get(intent.id())));
            var cleared = state;
            assertThrows(IllegalArgumentException.class, () -> replayEquipmentEvent(cleared, retirement, 502L));
            assertEquals(state, codec.decode(codec.encode(state)));
        }
    }

    private static FrontierWorldState selectedReturnFixture() {
        var fixture = fixture();
        var start = HiveSettlementAssaultProcess.planStart(fixture.state(), HiveSettlementAssaultProcess.start(fixture.task(), fixture.sighting(), 200L));
        var state = StrategicObjectiveProcess.reduceTaskTransition(fixture.state(), fixture.hive(),
                assertInstanceOf(StrategicTaskTransition.class, start.getFirst().payload()));
        var parent = assertInstanceOf(SettlementAssaultStarted.class, start.get(1).payload()).assault();
        state = HiveSettlementAssaultProcess.reduceStarted(state, fixture.hive(), new SettlementAssaultStarted(parent, SettlementAssaultExecutionAuthority.admission(state, parent)));
        state = arm(state, parent, parent.defenderIds().getFirst(), "selected-return");
        state = HiveSettlementAssaultProcess.reduceResolved(state, fixture.hive(), HiveSettlementAssaultProcess.resolution(state, parent, SettlementAssaultOutcome.ABORTED));
        return selectReturnVisit(state, state.strategicPlans().settlementAssaults().get(parent.id()), 250L);
    }

    @Test
    void resolvedDefenderUsesCommonColdJourneyTransferAndClearanceWithoutRevivingCombat() throws Exception {
        for (boolean recovered : List.of(false, true)) {
            var state = selectedReturnFixture();
            var parent = state.strategicPlans().settlementAssaults().values().stream()
                    .filter(candidate -> candidate.equipmentReturnVisit().isPresent()).findFirst().orElseThrow();
            var selected = parent.equipmentReturnVisit().orElseThrow();
            state = applyReturnReview(state, parent, 251L);
            assertTrue(state.actorExecutions().owns(selected.actorId(), ActorActivityKind.EQUIPMENT_SERVICE, parent.id()));
            assertFalse(state.actorExecutions().owns(selected.actorId(), ActorActivityKind.SETTLEMENT_ASSAULT, parent.id()));
            assertEquals(HumanAssignmentKind.EQUIPMENT_SERVICE, HumanAssignmentProjection.compile(state).assignment(selected.actorId()).kind());
            state = applyReturnReview(state, parent, 252L);
            assertEquals(ActorMovementContext.Provider.DEFENDER_EQUIPMENT_RETURN, state.actorMovements().get(selected.actorId()).context().provider());
            assertTrue(ServiceAccessCapabilities.current(state, selected.access().containerId()).stream()
                    .anyMatch(demand -> demand.identity().equals(selected.request().identity())));
            var arrived = finishCommonMovement(state, selected.actorId(), 253L, recovered);
            state = applyReturnReview(arrived.state(), parent, arrived.tick() + 1L);
            state = applyReturnReview(state, parent, arrived.tick() + 2L);
            var intent = state.physicalIntents().get(selected.transferId());
            assertEquals(PhysicalIntentStatus.PREPARED, intent.status());
            state = PhysicalIntentLifecycleFixture.transition(state, parent.settlementId(), intent, PhysicalIntentStatus.RUNNING, Optional.empty());
            state = PhysicalIntentLifecycleFixture.transition(state, parent.settlementId(), intent, PhysicalIntentStatus.CONFIRMED,
                    Optional.of(new EquipmentReturnObservation(new PhysicalObservationId("observation:journey-return"), intent.id(), parent.id(),
                            selected.actorId(), selected.itemId(), new InventoryCustody.ContainerSlot(selected.targetSlot().containerId(), selected.targetSlot().slot()))));
            long clearanceAt = Math.max(501L, arrived.tick() + 3L);
            state = applyReturnReview(state, parent, clearanceAt);
            assertEquals(DefenderEquipmentReturnVisit.Phase.CLEARANCE, state.strategicPlans().settlementAssaults().get(parent.id()).equipmentReturnVisit().orElseThrow().phase());
            state = applyReturnReview(state, parent, clearanceAt + 1L);
            var cleared = finishCommonMovement(state, selected.actorId(), clearanceAt + 2L, recovered);
            state = applyReturnReview(cleared.state(), parent, cleared.tick() + 1L);
            assertTrue(state.strategicPlans().settlementAssaults().get(parent.id()).equipmentReturnVisit().isEmpty());
            assertFalse(state.actorExecutions().owns(selected.actorId(), ActorActivityKind.EQUIPMENT_SERVICE, parent.id()));
            assertEquals(SettlementAssaultStatus.RESOLVED, state.strategicPlans().settlementAssaults().get(parent.id()).status());
            assertEquals(new InventoryCustody.ContainerSlot(selected.targetSlot().containerId(), selected.targetSlot().slot()), state.inventory().items().get(selected.itemId()).custody());
            assertEquals(DefenderEquipmentReturnProcess.review(parent, 251L).id(), DefenderEquipmentReturnProcess.review(parent, cleared.tick() + 1L).id());
        }
    }

    private record MovementResult(FrontierWorldState state, long tick) { }

    @Test
    void registeredReturnJourneySurvivesCheckpointAndRetiresItsActualKernelSchedules() throws Exception {
        var state = selectedReturnFixture();
        var parent = state.strategicPlans().settlementAssaults().values().stream()
                .filter(candidate -> candidate.equipmentReturnVisit().isPresent()).findFirst().orElseThrow();
        var visit = parent.equipmentReturnVisit().orElseThrow();
        var base = FrontierWorldRuntimeDefinition.configuration(state.bootstrap());
        var configuration = new FrontierEngineConfiguration<>(base.worldId(), state,
                new io.farfrontier.palemirror.frontier.v3.api.SimInstant(250), base.commandPlanner(), base.scheduledPlanner(),
                base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(),
                List.of(DefenderEquipmentReturnProcess.review(parent, 251L), DefenderEquipmentProcess.review(parent, 251L)),
                base.transactionCommitter(), base.stateValidator());
        var engine = FrontierEngines.createCanonicalStateAccess(configuration);
        int maximumSteps = Math.toIntExact(64L + 2L * TimedKnownRoute.MAX_SURFACES
                * state.bootstrap().ruleset().resourceHarvestColdTravelTicksPerEdge()
                / state.bootstrap().ruleset().cadence().hiveSettlementAssaultStepInterval());
        boolean restarted = false;
        for (int step = 0; step < maximumSteps && !engine.canonicalState().state().physicalIntents().containsKey(visit.transferId()); step++) {
            engine.advanceTo(engine.nextExecutionBoundary().orElseThrow(), new WorkBudget(1, 128));
            assertEquals(io.farfrontier.palemirror.frontier.v3.api.EngineStatus.Kind.ACTIVE, engine.status().kind(), engine.status().detail());
            if (!restarted && Optional.ofNullable(engine.canonicalState().state().actorMovements().get(visit.actorId()))
                    .flatMap(ActorMovement::coldTravel).isPresent()) {
                var image = new io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage(base.worldId(),
                        Optional.of(new io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord(engine.checkpoint(), 0)), List.of());
                engine = FrontierEngines.recoverCanonicalStateAccess(configuration, image);
                restarted = true;
            }
        }
        assertTrue(restarted, "restart must split the real movement rather than a manually placed arrival");
        var intent = engine.canonicalState().state().physicalIntents().get(visit.transferId());
        assertTrue(intent != null, "return must reach its physical boundary; current=" + engine.canonicalState().instant()
                + "; pending=" + engine.checkpoint().schedules());
        assertEquals(PhysicalIntentStatus.PREPARED, intent.status());
        for (var status : List.of(PhysicalIntentStatus.RUNNING, PhysicalIntentStatus.CONFIRMED)) {
            var current = engine.canonicalState();
            var id = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:kernel-return-" + status.name().toLowerCase(java.util.Locale.ROOT));
            Optional<PhysicalEffectObservation> receipt = status == PhysicalIntentStatus.CONFIRMED
                    ? Optional.of(new EquipmentReturnObservation(new PhysicalObservationId("observation:kernel-return"), intent.id(),
                            parent.id(), visit.actorId(), visit.itemId(), new InventoryCustody.ContainerSlot(visit.targetSlot().containerId(), visit.targetSlot().slot())))
                    : Optional.empty();
            var command = new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(
                    io.farfrontier.palemirror.frontier.v3.api.FrontierCommand.SCHEMA_VERSION, id, current.worldId(), current.revision(), current.instant(),
                    FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(id),
                    new PhysicalIntentTransition(intent.id(), status, receipt));
            assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class, engine.submit(command));
        }
        for (int step = 0; step < maximumSteps && engine.nextExecutionBoundary().isPresent(); step++) {
            engine.advanceTo(engine.nextExecutionBoundary().orElseThrow(), new WorkBudget(1, 128));
            assertEquals(io.farfrontier.palemirror.frontier.v3.api.EngineStatus.Kind.ACTIVE, engine.status().kind(), engine.status().detail());
        }
        var completed = engine.canonicalState().state();
        assertTrue(engine.checkpoint().schedules().isEmpty(), "both movement and owner review must have a terminal queue disposition");
        assertTrue(completed.strategicPlans().settlementAssaults().get(parent.id()).equipmentReturnVisit().isEmpty());
        assertFalse(completed.actorMovements().containsKey(visit.actorId()));
        assertFalse(completed.actorExecutions().owns(visit.actorId(), ActorActivityKind.EQUIPMENT_SERVICE, parent.id()));
        assertTrue(visit.access().accessBoundary().cleared(completed.actorLocations().get(visit.actorId()).body()));
        assertEquals(new InventoryCustody.ContainerSlot(visit.targetSlot().containerId(), visit.targetSlot().slot()), completed.inventory().items().get(visit.itemId()).custody());
    }

    @Test
    void equipmentApproachCannotStealAPreparedOrDrainingBodyEvenThroughReplay() {
        var state = selectedReturnFixture();
        var parent = state.strategicPlans().settlementAssaults().values().stream()
                .filter(candidate -> candidate.equipmentReturnVisit().isPresent()).findFirst().orElseThrow();
        var visit = parent.equipmentReturnVisit().orElseThrow();
        var selected = DefenderEquipmentReturnProcess.plan(state, DefenderEquipmentReturnProcess.review(parent, 251L)).stream()
                .filter(event -> event.payload() instanceof DefenderEquipmentReturnVisitChanged).findFirst().orElseThrow();
        for (var status : List.of(AmbientLeaseStatus.PREPARED, AmbientLeaseStatus.DRAINING, AmbientLeaseStatus.UNKNOWN_AFTER_RESTART)) {
            var body = state.actorLocations().get(visit.actorId()).body();
            var leased = state.withChanges(FrontierWorldStateUpdate.begin().ambientLeases(Map.of(visit.actorId(),
                    new AmbientActorLease(visit.actorId(), body, new io.farfrontier.palemirror.frontier.v3.api.SimInstant(250),
                            1, status, AmbientGoalKind.PATROL, body))));
            assertTrue(DefenderEquipmentReturnProcess.plan(leased, DefenderEquipmentReturnProcess.review(parent, 251L)).stream()
                    .noneMatch(event -> event.payload() instanceof DefenderEquipmentReturnVisitChanged));
            assertThrows(IllegalArgumentException.class, () -> replayEquipmentEvent(leased, selected, 251L));
        }
    }

    @Test
    void sourceLossRetiresOnlyItsExactJourneyAndCheckpointsCurrentColdPosition() throws Exception {
        var state = selectedReturnFixture();
        var parent = state.strategicPlans().settlementAssaults().values().stream()
                .filter(candidate -> candidate.equipmentReturnVisit().isPresent()).findFirst().orElseThrow();
        var visit = parent.equipmentReturnVisit().orElseThrow();
        state = applyReturnReview(state, parent, 251L);
        state = applyReturnReview(state, parent, 252L);
        var movement = state.actorMovements().get(visit.actorId());
        for (var event : ActorMovementProcess.plan(state, ActorMovementProcess.progress(movement, 253L), 253L))
            if (!(event.payload() instanceof io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect)) state = replayEquipmentEvent(state, event, 253L);
        movement = state.actorMovements().get(visit.actorId());
        long tick = movement.coldTravel().orElseThrow().departedAtTick() + movement.coldTravel().orElseThrow().ticksPerEdge();
        var position = ActorMovementProcess.bodyAt(state, visit.actorId(), tick);
        var item = state.inventory().items().get(visit.itemId());
        var id = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:journey-source-loss");
        var command = new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(
                io.farfrontier.palemirror.frontier.v3.api.FrontierCommand.SCHEMA_VERSION, id, state.bootstrap().worldId(),
                io.farfrontier.palemirror.frontier.v3.api.Revision.ZERO, new io.farfrontier.palemirror.frontier.v3.api.SimInstant(tick),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(id),
                new ExactItemDestroyed(item.id(), item.custody(), "test:journey-source-loss"));
        var accepted = assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan.Accepted.class,
                FrontierWorldRuntimeDefinition.planCommand(state, command));
        var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        state = codec.decode(codec.encode(state));
        try (var ignored = PedestrianRoutePlanning.bind((geometry, origin, target) -> { throw new AssertionError("source retirement replay searched"); })) {
            for (var event : accepted.events()) if (!(event.payload() instanceof io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect))
                state = replayEquipmentEvent(state, event, tick);
        }
        assertTrue(state.strategicPlans().settlementAssaults().get(parent.id()).equipmentReturnVisit().isEmpty());
        assertFalse(state.actorMovements().containsKey(visit.actorId()));
        assertFalse(state.actorExecutions().owns(visit.actorId(), ActorActivityKind.EQUIPMENT_SERVICE, parent.id()));
        assertEquals(position, state.actorLocations().get(visit.actorId()).body());
        assertEquals(state, codec.decode(codec.encode(state)));
    }

    @Test
    void safeActivityPauseResumesTheSameSelectedGoalUnderANewExactGeneration() throws Exception {
        var state = selectedReturnFixture();
        var parent = state.strategicPlans().settlementAssaults().values().stream()
                .filter(candidate -> candidate.equipmentReturnVisit().isPresent()).findFirst().orElseThrow();
        state = applyReturnReview(state, parent, 251L);
        var visit = state.strategicPlans().settlementAssaults().get(parent.id()).equipmentReturnVisit().orElseThrow();
        var original = visit.execution().orElseThrow();
        var passive = state.actorExecutions().next(visit.actorId(), ActorActivityKind.PRESENCE, visit.actorId());
        state = ActorExecutionComposition.LIFECYCLE.prepareBegin(state, passive, 252L).commit(state, FrontierWorldStateUpdate.begin());
        assertTrue(state.actorExecutions().suspended().contains(original));
        assertTrue(DefenderEquipmentReturnProcess.plan(state, DefenderEquipmentReturnProcess.review(parent, 253L)).stream()
                .noneMatch(event -> event.payload() instanceof ActorMovementStarted));
        var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        state = codec.decode(codec.encode(state));
        var successor = state.actorExecutions().next(visit.actorId(), ActorActivityKind.EQUIPMENT_SERVICE, parent.id());
        var resumed = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionResumed(original, successor, Optional.of(passive), 254L);
        try (var ignored = PedestrianRoutePlanning.bind((geometry, origin, target) -> { throw new AssertionError("resumption replay searched"); })) {
            state = replayEquipmentEvent(state, new ProposedEvent(visit.actorId(), resumed), 254L);
        }
        var retained = state.strategicPlans().settlementAssaults().get(parent.id()).equipmentReturnVisit().orElseThrow();
        assertEquals(visit.order(), retained.order());
        assertEquals(visit.transferId(), retained.transferId());
        assertEquals(Optional.of(successor), retained.execution());
        var beforeDuplicate = state;
        assertThrows(IllegalArgumentException.class, () -> replayEquipmentEvent(beforeDuplicate, new ProposedEvent(visit.actorId(), resumed), 254L));
        state = applyReturnReview(state, parent, 255L);
        assertEquals(successor, state.actorMovements().get(visit.actorId()).executionId());
        assertEquals(state, codec.decode(codec.encode(state)));
    }
    private static MovementResult finishCommonMovement(FrontierWorldState state, SubjectId actor, long tick, boolean recovered) throws Exception {
        for (int segment = 0; segment < 8 && state.actorMovements().containsKey(actor); segment++) {
            var movement = state.actorMovements().get(actor);
            tick = movement.coldTravel().map(TimedKnownRoute::arrivalTick).orElse(Math.max(tick, movement.issuedAtTick() + 1L));
            var events = ActorMovementProcess.plan(state, ActorMovementProcess.progress(movement, tick), tick);
            var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
            if (recovered) state = codec.decode(codec.encode(state));
            try (var ignored = PedestrianRoutePlanning.bind((geometry, origin, target) -> { throw new AssertionError("movement replay searched"); })) {
                for (var event : events) if (!(event.payload() instanceof io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect))
                    state = replayEquipmentEvent(state, event, tick);
            }
            assertEquals(state, codec.decode(codec.encode(state)));
        }
        assertFalse(state.actorMovements().containsKey(actor), "the selected common journey must actually arrive");
        return new MovementResult(state, tick);
    }

    private static FrontierWorldState applyReturnReview(FrontierWorldState state, SettlementAssault parent, long tick) {
        for (var event : DefenderEquipmentReturnProcess.plan(state, DefenderEquipmentReturnProcess.review(parent, tick)))
            if (!(event.payload() instanceof io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect)) state = replayEquipmentEvent(state, event, tick);
        return state;
    }

    @Test
    void selectedReturnReplayRejectsDuplicateForeignAndSubstitutedDeclarationsWithoutSearching() throws Exception {
        var state = selectedReturnFixture();
        var parent = state.strategicPlans().settlementAssaults().values().stream()
                .filter(candidate -> candidate.equipmentReturnVisit().isPresent()).findFirst().orElseThrow();
        var selected = parent.equipmentReturnVisit().orElseThrow();
        var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        var before = codec.decode(codec.encode(state));
        var duplicate = new ProposedEvent(parent.id(), new DefenderEquipmentReturnVisitChanged(parent.id(), Optional.empty(), Optional.of(selected)));
        var foreign = new ProposedEvent(parent.settlementId(), new DefenderEquipmentReturnVisitChanged(parent.id(), Optional.of(selected), Optional.empty()));
        var wrongItem = new SubjectId("item:substituted-return");
        var substituted = new DefenderEquipmentReturnVisit(selected.transferId(),
                io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleBinding.assaultEquipmentReturn(
                        parent.id(), selected.actorId(), wrongItem, selected.access().containerId()), selected.access(), selected.targetSlot(), selected.request());
        var empty = before.withChanges(FrontierWorldStateUpdate.begin().strategicPlans(
                before.strategicPlans().replaceSettlementAssault(parent.withEquipmentReturnVisit(Optional.empty()))));
        try (var ignored = PedestrianRoutePlanning.bind((geometry, origin, target) -> { throw new AssertionError("visit replay searched"); })) {
            assertThrows(IllegalArgumentException.class, () -> replayEquipmentEvent(before, duplicate, 500L));
            assertThrows(IllegalArgumentException.class, () -> replayEquipmentEvent(before, foreign, 500L));
            assertThrows(IllegalArgumentException.class, () -> replayEquipmentEvent(empty, new ProposedEvent(parent.id(),
                    new DefenderEquipmentReturnVisitChanged(parent.id(), Optional.empty(), Optional.of(substituted))), 500L));
            var admitted = replayEquipmentEvent(empty, duplicate, 500L);
            assertEquals(before, admitted);
        }
    }

    private static io.farfrontier.palemirror.frontier.v3.api.FrontierCommand equipmentCommand(FrontierWorldState state,
            io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        var id = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:selected-return-source");
        return new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(io.farfrontier.palemirror.frontier.v3.api.FrontierCommand.SCHEMA_VERSION, id, state.bootstrap().worldId(),
                io.farfrontier.palemirror.frontier.v3.api.Revision.ZERO, new io.farfrontier.palemirror.frontier.v3.api.SimInstant(500),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(id), payload);
    }

    private static PhysicalIntent plannedIssue(FrontierWorldState state, SettlementAssault assault, long tick) {
        return DefenderEquipmentProcess.plan(state, DefenderEquipmentProcess.review(assault, tick)).stream().map(ProposedEvent::payload)
                .filter(PhysicalIntentPrepared.class::isInstance).map(PhysicalIntentPrepared.class::cast).map(PhysicalIntentPrepared::intent).findFirst().orElseThrow();
    }

    private static FrontierWorldState selectReturnVisit(FrontierWorldState state, SettlementAssault parent, long tick) {
        var selected = DefenderEquipmentReturnProcess.plan(state, DefenderEquipmentReturnProcess.review(parent, tick)).stream()
                .filter(event -> event.payload() instanceof DefenderEquipmentReturnVisitChanged).findFirst().orElseThrow();
        return replayEquipmentEvent(state, selected, tick);
    }

    /** Physical-transfer fixtures declare arrival; the full journey fixture below uses common movement instead. */
    private static FrontierWorldState atReturnService(FrontierWorldState state, SettlementAssault parent, long tick) {
        var visit = state.strategicPlans().settlementAssaults().get(parent.id()).equipmentReturnVisit().orElseThrow();
        state = atStorageStation(state, visit.actorId(), visit.access().containerId());
        for (int step = 0; step < 2; step++) {
            var changed = DefenderEquipmentReturnProcess.plan(state, DefenderEquipmentReturnProcess.review(parent, tick + step)).stream()
                    .filter(event -> event.payload() instanceof DefenderEquipmentReturnVisitChanged).findFirst().orElseThrow();
            state = replayEquipmentEvent(state, changed, tick + step);
        }
        return state;
    }

    private static FrontierWorldState replayEquipmentEvent(FrontierWorldState state, ProposedEvent proposed, long tick) {
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        var payload = codecs.decode(proposed.payload().type(), codecs.encode(proposed.payload()));
        var id = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:selected-return-replay");
        var event = new io.farfrontier.palemirror.frontier.v3.api.FrontierEvent(1,
                new io.farfrontier.palemirror.frontier.v3.api.EventId("event:selected-return-replay"),
                new io.farfrontier.palemirror.frontier.v3.api.TransactionId("transaction:selected-return-replay"), state.bootstrap().worldId(),
                new io.farfrontier.palemirror.frontier.v3.api.Revision(1), new io.farfrontier.palemirror.frontier.v3.api.SimInstant(tick),
                proposed.subject(), io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(id), payload);
        return FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), 41L).reducer().apply(state, event);
    }

    private static FrontierWorldState atStorageStation(FrontierWorldState state, SubjectId resident, SubjectId container) {
        var locations = new java.util.LinkedHashMap<>(state.actorLocations());
        var station = ResourceStorageAccess.at(state, container).stations().getFirst();
        locations.put(resident, locations.get(resident).withBody(station.standingBody()));
        return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(locations));
    }

    private static FrontierWorldState arm(FrontierWorldState state, SettlementAssault assault, SubjectId resident, String suffix) {
        SubjectId depot = FrontierWorldState.depotId(assault.settlementId());
        int slot = state.inventory().firstFreeSlot(depot).orElseThrow();
        SubjectId item = new SubjectId("item:tactical-readiness-" + suffix);
        ExactInventory source = state.inventory();
        if (source.surfaces().get(depot).status() == ContainerSurfaceStatus.UNMATERIALIZED) {
            source = source.withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE);
        }
        ExactInventory inventory = source
                .store(new ExactItemStack(item, assault.settlementId(), "minecraft:iron_sword", 1, new InventoryCustody.ContainerSlot(depot, slot)))
                .moveObservedItem(item, new InventoryCustody.ContainerSlot(depot, slot), new InventoryCustody.Actor(resident));
        return state.withInventory(inventory);
    }

    private record Fixture(FrontierWorldState state, SubjectId hive, StrategicTask task, HiveSettlementKnowledge.Sighting sighting) { }
}
