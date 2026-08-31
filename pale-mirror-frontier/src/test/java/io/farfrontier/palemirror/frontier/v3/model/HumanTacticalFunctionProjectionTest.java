package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.process.HiveSettlementAssaultProcess;
import io.farfrontier.palemirror.frontier.v3.process.StrategicObjectiveProcess;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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
        SubjectId militia = assault.defenderIds().stream().filter(id -> !id.equals(leader)).findFirst().orElseThrow();
        assertEquals(HumanTacticalFunction.SQUAD_LEADER, HumanTacticalFunctionProjection.derive(state, leader));
        assertEquals(HumanTacticalFunction.MILITIA, HumanTacticalFunctionProjection.derive(state, militia));
        assertEquals("Northwatch SQUAD LEADER", FrontierSceneLabels.actor(state, leader, false));

        SubjectId depot = FrontierWorldState.depotId(assault.settlementId());
        int slot = state.inventory().firstFreeSlot(depot).orElseThrow();
        SubjectId sword = new SubjectId("item:tactical-function-sword");
        ExactInventory stored = state.inventory().store(new ExactItemStack(sword, assault.settlementId(), "minecraft:iron_sword", 1,
                new InventoryCustody.ContainerSlot(depot, slot)));
        state = state.withInventory(stored.moveObservedItem(sword, new InventoryCustody.ContainerSlot(depot, slot), new InventoryCustody.Actor(militia)));

        assertEquals(HumanTacticalFunction.ARMED_DEFENDER, HumanTacticalFunctionProjection.derive(state, militia));
        assertEquals("Northwatch ARMED DEFENDER", FrontierSceneLabels.actor(state, militia, false));
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
