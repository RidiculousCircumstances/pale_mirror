package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.process.*;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Proves role consumers use the mutable exact-person register, never bootstrap residents. */
class HumanRoleAssignmentTest {
    @Test
    void roleSelectionUsesTheRelevantExactSkillBeforeStableIdentity() {
        FrontierWorldState state = initial("frontier:human-skills");
        Settlement settlement = state.bootstrap().settlements().getFirst();
        ResidentProfile lower = born(state, "resident:1-guard-skilled-low", ResidentRole.GUARD, ResidentSkill.SECURITY, 90);
        state = HumanPopulationTestFixtures.withResident(state, lower, settlement.anchor());
        ResidentProfile higher = born(state, "resident:1-guard-skilled-high", ResidentRole.GUARD, ResidentSkill.SECURITY, 100);
        state = HumanPopulationTestFixtures.withResident(state, higher, settlement.anchor());

        assertEquals(higher, FrontierWorldStateSupport.availableRouteResident(state, settlement.id(), HumanCapability.SECURITY).orElseThrow());
    }

    @Test
    void capabilitiesDriveWorkSelectionWithoutReplacingThePersonOrName() {
        FrontierWorldState state = initial("frontier:human-profession");
        Settlement settlement = state.bootstrap().settlements().getFirst();
        ResidentProfile original = state.humanPopulation().residents().values().stream()
                .filter(value -> value.settlementId().equals(settlement.id()) && value.role() == ResidentRole.FARMER).findFirst().orElseThrow();
        Map<HumanCapability, Integer> capabilities = new EnumMap<>(original.capabilities());
        capabilities.put(HumanCapability.SECURITY, 100);
        ResidentProfile retrained = new ResidentProfile(original.id(), original.householdId(), original.settlementId(), original.role(),
                original.name(), original.birthTick(), original.skills(), capabilities);
        state = state.withHumanPopulation(state.humanPopulation().withProfile(retrained));

        assertEquals(original.id(), state.humanPopulation().resident(original.id()).id());
        assertEquals(original.name(), retrained.name());
        assertEquals(retrained, FrontierWorldStateSupport.availableRouteResident(state, settlement.id(), HumanCapability.SECURITY).orElseThrow());
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }

    @Test
    void genesisSkillsProduceCapabilitiesAndProfileReplacementCannotMoveAResident() {
        FrontierWorldState state = initial("frontier:human-profile-migration");
        ResidentProfile original = state.humanPopulation().resident(new SubjectId("resident:1-1"));
        ResidentProfile legacy = new ResidentProfile(original.id(), original.householdId(), original.settlementId(), ResidentRole.CRAFTER,
                original.name(), original.birthTick(), original.skills());

        assertEquals(original.name(), legacy.name());
        assertEquals(legacy.skill(ResidentSkill.CRAFTING), legacy.capability(HumanCapability.INDUSTRY));
        assertThrows(IllegalArgumentException.class, () -> state.humanPopulation().withProfile(new ResidentProfile(original.id(),
                new SubjectId("household:foreign"), original.settlementId(), original.role(), original.name(), original.birthTick(),
                original.skills(), original.capabilities())));
    }

    @Test
    void bornCrafterReplacesDeadBootstrapCrafterForProduction() {
        FrontierWorldState state = initial("frontier:human-crafter");
        Settlement settlement = state.bootstrap().settlements().getFirst();
        ResidentProfile departed = state.humanPopulation().residents().values().stream()
                .filter(value -> value.settlementId().equals(settlement.id()) && value.role() == ResidentRole.CRAFTER).findFirst().orElseThrow();
        BlockPosition replacementSurface = state.actorLocations().get(departed.id()).supportingSurface().support();
        state = killRole(state, ResidentRole.CRAFTER);
        ResidentProfile born = born(state, "resident:1-crafter-born", ResidentRole.CRAFTER);
        state = HumanPopulationTestFixtures.withResident(state, born, replacementSurface);
        StrategicTask task = productionTask(settlement.id());
        state = state.withStrategicPlans(state.strategicPlans().addObjective(objective(task, StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD))
                .addTask(task));
        var permissionKinds = new java.util.EnumMap<ResidentWorkKind, java.util.Set<SubjectId>>(ResidentWorkKind.class);
        permissionKinds.putAll(SettlementWorkPolicy.permissions(state, settlement.id()).workers());
        permissionKinds.put(ResidentWorkKind.BAKING, java.util.Set.of(born.id()));
        state = state.withStrategicPlans(state.strategicPlans().withWorkPermissions(settlement.id(),
                new ResidentWorkPermissions(permissionKinds)));

        List<ProposedEvent> planned = ProductionProcess.planStart(state, ProductionProcess.start(task, 100L));

        ProductionStarted started = assertInstanceOf(ProductionStarted.class, planned.get(1).payload());
        assertEquals(born.id(), started.job().workerId());
    }


    @Test
    void exactActorHeldWeaponChangesTheSameCivilianCombatCapability() {
        FrontierWorldState state = initial("frontier:human-equipped-worker");
        ResidentProfile worker = state.humanPopulation().residents().values().stream()
                .filter(value -> value.capability(HumanCapability.SECURITY) < 50).findFirst().orElseThrow();
        assertEquals(FixedScalar.whole(1), FrontierCombatRules.damage(state, worker.id()));
        SubjectId depot = FrontierWorldState.depotId(worker.settlementId());
        int slot = state.inventory().firstFreeSlot(depot).orElseThrow();
        SubjectId sword = new SubjectId("item:human-equipped-worker-sword");
        ExactInventory stored = state.inventory().store(new ExactItemStack(sword, worker.settlementId(), "minecraft:iron_sword", 1,
                new InventoryCustody.ContainerSlot(depot, slot)));
        state = state.withInventory(stored.moveObservedItem(sword, new InventoryCustody.ContainerSlot(depot, slot), new InventoryCustody.Actor(worker.id())));
        assertEquals(FixedScalar.whole(3), FrontierCombatRules.damage(state, worker.id()));
    }

    private static FrontierWorldState initial(String world) {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId(world), 91L));
    }

    private static FrontierWorldState kill(FrontierWorldState state, SubjectId actorId) {
        var present = ModeledActorBodyFacts.present(state, actorId);
        return ModeledActorBodyFacts.died(present, actorId, present.actorLocations().get(actorId).body(), "test:replacement", 0L);
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
        return new ResidentProfile(new SubjectId(id), parent.householdId(), parent.settlementId(), role, "Test Resident", 0L, parent.skills());
    }

    private static ResidentProfile born(FrontierWorldState state, String id, ResidentRole role, ResidentSkill skill, int value) {
        ResidentProfile parent = state.humanPopulation().resident(new SubjectId("resident:1-1"));
        Map<ResidentSkill, Integer> skills = new EnumMap<>(parent.skills());
        skills.put(skill, value);
        return new ResidentProfile(new SubjectId(id), parent.householdId(), parent.settlementId(), role, "Test Resident", 0L, skills);
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
                StrategicTaskRequirement.EXACT_WHEAT_INPUT), List.of(), StrategicTaskStatus.PENDING);
    }


}
