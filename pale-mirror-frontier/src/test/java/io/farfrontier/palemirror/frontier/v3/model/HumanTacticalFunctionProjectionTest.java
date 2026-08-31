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
import io.farfrontier.palemirror.frontier.v3.process.StrategicObjectiveProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HumanTacticalFunctionProjectionTest {
    @Test
    void exactAssignmentUnitLeadershipAndActorWeaponDeriveReadableDefenceFunctions() {
        Fixture fixture = fixture();
        List<ProposedEvent> events = HiveSettlementAssaultProcess.planStart(fixture.state(),
                HiveSettlementAssaultProcess.start(fixture.task(), fixture.sighting(), 200L));
        FrontierWorldState state = StrategicObjectiveProcess.reduceTaskTransition(fixture.state(), fixture.hive(),
                assertInstanceOf(StrategicTaskTransition.class, events.getFirst().payload()));
        SettlementAssault assault = assertInstanceOf(SettlementAssaultStarted.class, events.get(1).payload()).assault();
        state = HiveSettlementAssaultProcess.reduceStarted(state, fixture.hive(), new SettlementAssaultStarted(assault));

        SubjectId leader = assault.defenderUnit().leaderId();
        assertEquals(HumanTacticalFunction.SQUAD_LEADER, HumanTacticalFunctionProjection.derive(state, leader));
        assertEquals("Northwatch SQUAD LEADER", FrontierSceneLabels.actor(state, leader, false));

        SubjectId depot = FrontierWorldState.depotId(assault.settlementId());
        int slot = state.inventory().firstFreeSlot(depot).orElseThrow();
        SubjectId sword = new SubjectId("item:tactical-function-sword");
        ExactInventory stored = state.inventory().withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE)
                .store(new ExactItemStack(sword, assault.settlementId(), "minecraft:iron_sword", 1,
                new InventoryCustody.ContainerSlot(depot, slot)));
        state = state.withInventory(stored);
        PhysicalIntent issue = DefenderEquipmentProcess.plan(state, DefenderEquipmentProcess.review(assault, 201L)).stream()
                .map(ProposedEvent::payload).filter(PhysicalIntentPrepared.class::isInstance).map(PhysicalIntentPrepared.class::cast)
                .map(PhysicalIntentPrepared::intent).findFirst().orElseThrow();
        SubjectId militia = issue.subjectIds().get(1);
        assertEquals(HumanTacticalFunction.MILITIA, HumanTacticalFunctionProjection.derive(state, militia));
        state = state.preparePhysicalIntent(issue).transitionPhysicalIntent(issue.id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        FrontierWorldState runningState = state;
        EquipmentIssueObservation forgedSource = new EquipmentIssueObservation(new PhysicalObservationId("observation:tactical-function-forged"), issue.id(),
                assault.id(), militia, sword, new InventoryCustody.ContainerSlot(depot, slot + 1));
        assertThrows(IllegalArgumentException.class, () -> runningState.transitionPhysicalIntent(issue.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(forgedSource)));
        EquipmentIssueObservation receipt = new EquipmentIssueObservation(new PhysicalObservationId("observation:tactical-function-issue"), issue.id(),
                assault.id(), militia, sword, new InventoryCustody.ContainerSlot(depot, slot));
        state = state.transitionPhysicalIntent(issue.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));

        assertEquals(HumanTacticalFunction.ARMED_DEFENDER, HumanTacticalFunctionProjection.derive(state, militia));
        assertEquals("Northwatch ARMED DEFENDER", FrontierSceneLabels.actor(state, militia, false));
        assertEquals(receipt, ((PhysicalIntentTransition) FrontierWorldRuntimeDefinition.payloadCodecs().decode(
                new PhysicalIntentTransition(issue.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt)).type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(new PhysicalIntentTransition(issue.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt)))))
                .observation().orElseThrow());
    }

    @Test
    void exactWeaponWithoutAnOwnedTacticalAssignmentDoesNotCreateACombatClass() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:tactical-idle"), 91L));
        ResidentProfile resident = state.humanPopulation().residents().values().stream()
                .filter(value -> value.profession() != ResidentProfession.SECURITY_WORKER).findFirst().orElseThrow();
        SubjectId depot = FrontierWorldState.depotId(resident.settlementId());
        int slot = state.inventory().firstFreeSlot(depot).orElseThrow();
        SubjectId sword = new SubjectId("item:tactical-idle-sword");
        ExactInventory stored = state.inventory().store(new ExactItemStack(sword, resident.settlementId(), "minecraft:iron_sword", 1,
                new InventoryCustody.ContainerSlot(depot, slot)));
        state = state.withInventory(stored.moveObservedItem(sword, new InventoryCustody.ContainerSlot(depot, slot), new InventoryCustody.Actor(resident.id())));

        assertEquals(HumanTacticalFunction.CIVILIAN, HumanTacticalFunctionProjection.derive(state, resident.id()));
    }

    @Test
    void resolvedAssaultReturnsTheSameExactWeaponOnlyToItsNamedEmptyDepotSlot() {
        Fixture fixture = fixture();
        List<ProposedEvent> start = HiveSettlementAssaultProcess.planStart(fixture.state(), HiveSettlementAssaultProcess.start(fixture.task(), fixture.sighting(), 200L));
        FrontierWorldState state = StrategicObjectiveProcess.reduceTaskTransition(fixture.state(), fixture.hive(),
                assertInstanceOf(StrategicTaskTransition.class, start.getFirst().payload()));
        SettlementAssault assault = assertInstanceOf(SettlementAssaultStarted.class, start.get(1).payload()).assault();
        state = HiveSettlementAssaultProcess.reduceStarted(state, fixture.hive(), new SettlementAssaultStarted(assault));
        SubjectId depot = FrontierWorldState.depotId(assault.settlementId()), resident = assault.defenderIds().getFirst();
        int sourceSlot = state.inventory().firstFreeSlot(depot).orElseThrow(); SubjectId sword = new SubjectId("item:tactical-return-sword");
        state = state.withInventory(state.inventory().withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE)
                .store(new ExactItemStack(sword, assault.settlementId(), "minecraft:iron_sword", 1, new InventoryCustody.ContainerSlot(depot, sourceSlot)))
                .moveObservedItem(sword, new InventoryCustody.ContainerSlot(depot, sourceSlot), new InventoryCustody.Actor(resident)));
        state = HiveSettlementAssaultProcess.reduceResolved(state, fixture.hive(), new SettlementAssaultResolved(assault.id(), SettlementAssaultOutcome.ABORTED));
        SettlementAssault resolved = state.strategicPlans().settlementAssaults().get(assault.id());

        PhysicalIntent returned = DefenderEquipmentReturnProcess.plan(state, DefenderEquipmentReturnProcess.review(resolved, 250L)).stream()
                .map(ProposedEvent::payload).filter(PhysicalIntentPrepared.class::isInstance).map(PhysicalIntentPrepared.class::cast)
                .map(PhysicalIntentPrepared::intent).findFirst().orElseThrow();
        assertEquals(PhysicalIntentKind.EQUIPMENT_RETURN, returned.kind());
        assertEquals(new io.farfrontier.palemirror.frontier.v3.api.PhysicalContainerSlot(depot, sourceSlot), returned.targetSlot().orElseThrow());
        state = state.preparePhysicalIntent(returned).transitionPhysicalIntent(returned.id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        FrontierWorldState running = state;
        EquipmentReturnObservation forged = new EquipmentReturnObservation(new PhysicalObservationId("observation:tactical-return-forged"), returned.id(),
                assault.id(), resident, sword, new InventoryCustody.ContainerSlot(depot, sourceSlot + 1));
        assertThrows(IllegalArgumentException.class, () -> running.transitionPhysicalIntent(returned.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(forged)));
        EquipmentReturnObservation receipt = new EquipmentReturnObservation(new PhysicalObservationId("observation:tactical-return"), returned.id(),
                assault.id(), resident, sword, new InventoryCustody.ContainerSlot(depot, sourceSlot));
        state = state.transitionPhysicalIntent(returned.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));

        assertEquals(new InventoryCustody.ContainerSlot(depot, sourceSlot), state.inventory().items().get(sword).custody());
        assertEquals(HumanTacticalFunction.CIVILIAN, HumanTacticalFunctionProjection.derive(state, resident));
        assertEquals(state, new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().decode(
                new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().encode(state)));
        assertEquals(receipt, ((PhysicalIntentTransition) FrontierWorldRuntimeDefinition.payloadCodecs().decode(
                new PhysicalIntentTransition(returned.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt)).type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(new PhysicalIntentTransition(returned.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt)))))
                .observation().orElseThrow());
    }

    private static Fixture fixture() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:tactical-function"), 91L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        Bioform scout = state.bootstrap().hive().bioforms().stream().filter(value -> value.role() == BioformRole.SCOUT).findFirst().orElseThrow();
        state = state.withActorLocation(scout.id(), settlement.anchor());
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

    private record Fixture(FrontierWorldState state, SubjectId hive, StrategicTask task, HiveSettlementKnowledge.Sighting sighting) { }
}
