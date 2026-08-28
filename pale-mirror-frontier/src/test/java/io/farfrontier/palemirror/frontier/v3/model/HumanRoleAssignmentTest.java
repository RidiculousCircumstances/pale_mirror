package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** Proves role consumers use the mutable exact-person register, never bootstrap residents. */
class HumanRoleAssignmentTest {
    @Test
    void roleSelectionUsesTheRelevantExactSkillBeforeStableIdentity() {
        FrontierWorldState state = initial("frontier:human-skills");
        Settlement settlement = state.bootstrap().settlements().getFirst();
        ResidentProfile lower = born(state, "resident:1-guard-skilled-low", ResidentRole.GUARD, ResidentSkill.SECURITY, 90);
        state = state.recordResidentBirth(new ResidentBorn(lower, settlement.anchor()));
        ResidentProfile higher = born(state, "resident:1-guard-skilled-high", ResidentRole.GUARD, ResidentSkill.SECURITY, 100);
        state = state.recordResidentBirth(new ResidentBorn(higher, settlement.anchor()));

        assertEquals(higher, FrontierWorldStateSupport.availableRouteResident(state, settlement.id(), ResidentRole.GUARD).orElseThrow());
    }

    @Test
    void bornCrafterReplacesDeadBootstrapCrafterForProduction() {
        FrontierWorldState state = initial("frontier:human-crafter");
        Settlement settlement = state.bootstrap().settlements().getFirst();
        state = killRole(state, ResidentRole.CRAFTER);
        ResidentProfile born = born(state, "resident:1-crafter-born", ResidentRole.CRAFTER);
        state = state.recordResidentBirth(new ResidentBorn(born, settlement.anchor()));
        StrategicTask task = productionTask(settlement.id());
        state = state.withStrategicPlans(StrategicPlanState.empty().addObjective(objective(task, StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD))
                .addTask(task));

        List<ProposedEvent> planned = ProductionProcess.planStart(state, ProductionProcess.start(task, 100L));

        ProductionStarted started = assertInstanceOf(ProductionStarted.class, planned.get(1).payload());
        assertEquals(born.id(), started.job().workerId());
    }

    @Test
    void bornHaulerAndGuardReplaceDeadBootstrapRolesForAnExactCargoOperation() {
        FrontierWorldState state = initial("frontier:human-route");
        Settlement settlement = state.bootstrap().settlements().getFirst();
        state = killRole(state, ResidentRole.GUARD);
        state = killRole(state, ResidentRole.HAULER);
        ResidentProfile guard = born(state, "resident:1-guard-born", ResidentRole.GUARD);
        state = state.recordResidentBirth(new ResidentBorn(guard, settlement.anchor()));
        ResidentProfile hauler = born(state, "resident:1-hauler-born", ResidentRole.HAULER);
        state = state.recordResidentBirth(new ResidentBorn(hauler, settlement.anchor()));
        assertEquals(FixedScalar.whole(3), RouteEngagementCombatRules.damage(state, guard.id()));
        state = state.withInventory(withBread(state.inventory(), settlement.id()));

        StrategicTask preparation = preparationTask(settlement.id());
        StrategicTask delivery = deliveryTask(settlement.id(), preparation.id());
        StrategicPlanState plans = StrategicPlanState.empty().addObjective(objective(preparation, StrategicObjectiveKind.SETTLEMENT_DELIVER_BREAD_TO_HIVE))
                .addTask(preparation).addTask(delivery);
        state = state.withStrategicPlans(plans).createSupplyContract(new SupplyContract(new SubjectId("contract:supply-1-1"), settlement.id(),
                state.bootstrap().hive().id(), new SubjectId("cargo:supply-1-1"), "minecraft:bread", 64, ContractStatus.ORDERED));

        List<ProposedEvent> planned = SupplyOperationProcess.planCargoLoad(state, cargoLoad(new SubjectId("contract:supply-1-1")), false);

        OperationCreated created = planned.stream().map(ProposedEvent::payload).filter(OperationCreated.class::isInstance)
                .map(OperationCreated.class::cast).findFirst().orElseThrow();
        assertEquals(List.of(hauler.id(), guard.id()), created.operation().participantIds());
    }

    private static FrontierWorldState initial(String world) {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId(world), 91L));
    }

    private static FrontierWorldState kill(FrontierWorldState state, SubjectId actorId) {
        return AmbientActorProcess.reduce(state, FrontierWorldStateSupport.actorOwner(state, actorId),
                new AmbientActorDied(actorId, state.actorLocations().get(actorId).position(), "test:replacement"));
    }

    private static FrontierWorldState killRole(FrontierWorldState state, ResidentRole role) {
        for (ResidentProfile resident : state.humanPopulation().residents().values().stream()
                .filter(value -> value.settlementId().equals(new SubjectId("settlement:1")) && value.role() == role).toList()) {
            state = kill(state, resident.id());
        }
        return state;
    }

    private static ResidentProfile born(FrontierWorldState state, String id, ResidentRole role) {
        ResidentProfile parent = state.humanPopulation().resident(new SubjectId("resident:1-1"));
        return new ResidentProfile(new SubjectId(id), parent.householdId(), parent.settlementId(), role, 0L, parent.skills());
    }

    private static ResidentProfile born(FrontierWorldState state, String id, ResidentRole role, ResidentSkill skill, int value) {
        ResidentProfile parent = state.humanPopulation().resident(new SubjectId("resident:1-1"));
        Map<ResidentSkill, Integer> skills = new EnumMap<>(parent.skills());
        skills.put(skill, value);
        return new ResidentProfile(new SubjectId(id), parent.householdId(), parent.settlementId(), role, 0L, skills);
    }

    private static ExactInventory withBread(ExactInventory inventory, SubjectId settlement) {
        SubjectId depot = FrontierWorldState.depotId(settlement);
        Map<SubjectId, ExactItemStack> items = new LinkedHashMap<>(inventory.items());
        SubjectId bread = new SubjectId("item:test-human-role-bread");
        items.put(bread, new ExactItemStack(bread, settlement, "minecraft:bread", 64, new InventoryCustody.ContainerSlot(depot, 1)));
        return new ExactInventory(inventory.containers(), items, inventory.cargo(), inventory.playerItems(), inventory.worldCarrierItems(), inventory.conflicts(), inventory.surfaces());
    }

    private static StrategicObjective objective(StrategicTask task, StrategicObjectiveKind kind) {
        return new StrategicObjective(task.objectiveId(), task.ownerId(), kind, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
    }

    private static StrategicTask productionTask(SubjectId settlement) {
        return new StrategicTask(new SubjectId("task:human-crafter"), new SubjectId("objective:human-crafter"), settlement,
                StrategicTaskKind.PRODUCE_BREAD, Optional.empty(), List.of(StrategicTaskRequirement.ACTIVE_WORKSHOP,
                StrategicTaskRequirement.EXACT_WHEAT_INPUT, StrategicTaskRequirement.FREE_DEPOT_SLOT), List.of(), StrategicTaskStatus.PENDING);
    }

    private static StrategicTask preparationTask(SubjectId settlement) {
        return new StrategicTask(new SubjectId("task:human-route-prepare"), new SubjectId("objective:human-route"), settlement,
                StrategicTaskKind.PREPARE_BREAD_CARGO, Optional.empty(), List.of(StrategicTaskRequirement.EXACT_BREAD_CARGO), List.of(), StrategicTaskStatus.ACTIVE);
    }

    private static StrategicTask deliveryTask(SubjectId settlement, SubjectId preparation) {
        return new StrategicTask(new SubjectId("task:human-route-deliver"), new SubjectId("objective:human-route"), settlement,
                StrategicTaskKind.DELIVER_BREAD_TO_HIVE, Optional.empty(), List.of(StrategicTaskRequirement.PASSABLE_SUPPLY_ROUTE,
                StrategicTaskRequirement.AVAILABLE_HAULER, StrategicTaskRequirement.AVAILABLE_GUARD), List.of(preparation), StrategicTaskStatus.PENDING);
    }

    private static ScheduledAction cargoLoad(SubjectId contract) {
        return new ScheduledAction(new ScheduleId("schedule:human-role-cargo-load"), new SimInstant(100L), 0, contract, "frontier.supply.cargo.load", 1);
    }
}
