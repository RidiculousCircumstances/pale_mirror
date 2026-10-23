package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.process.ProductionProcess;
import io.farfrontier.palemirror.frontier.v3.process.StationWorkOrders;
import java.util.*;

/** Unfinished equipment order with explicit inputs; no processing, output or delivery is injected. */
public final class EquipmentWorkFixture {
    private EquipmentWorkFixture() { }
    public record Fixture(FrontierWorldState state, ProductionJob job, StrategicTask task, Settlement home,
                   SubjectId material, SubjectId target) { }
    public static Fixture create(WorldId world, long seed, boolean repair) {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(
                world, seed, FrontierRulesets.toolGraybox()));
        var home = state.bootstrap().settlements().getFirst();
        var depot = FrontierWorldState.depotId(home.id());
        var station = state.inventory().containers().values().stream()
                .filter(c -> c.ownerId().equals(home.id())).flatMap(c -> c.productionStation().stream())
                .filter(s -> s.capability() == ProductionStationSpec.Capability.FABRICATION).findFirst().orElseThrow();
        var facility = home.structures().stream().filter(s -> s.id().equals(station.facilityId())).findFirst().orElseThrow();
        var objective = new StrategicObjective(new SubjectId("objective:tool-test"), home.id(),
                StrategicObjectiveKind.SETTLEMENT_MAINTAIN_EQUIPMENT, Optional.empty(), 1, StrategicObjectiveStatus.ACTIVE);
        var task = new StrategicTask(new SubjectId("task:tool-test"), objective.id(), home.id(), StrategicTaskKind.PRODUCE_AT_STATION,
                Optional.empty(), List.of(StrategicTaskRequirement.ACTIVE_PRODUCTION_FACILITY,
                    StrategicTaskRequirement.AVAILABLE_PRODUCTION_INPUT), List.of(), StrategicTaskStatus.ACTIVE);
        state = state.withStrategicPlans(StrategicPlanState.initial(state.bootstrap()).addObjective(objective).addTask(task));
        var material = new SubjectId("lot:tool-test-stone");
        var source = FungibleResourceCustodySupport.accountAtContainer(state, depot).orElseThrow().id();
        var staging = new SubjectId("custody:tool-test-staging");
        state = state.withInventory(state.inventory().withFungibleResources(state.inventory().fungibleResources().issue(
                new ResourceLot(material, home.id(), "minecraft:cobblestone", 3, "fixture:material", List.of()),
                new CustodyAccount(staging, new ResourceCustody.Container(depot), Map.of(material, 3), Map.of()))
                .transfer(staging, source, Map.of(material, 3), Map.of())));
        var target = new SubjectId("item:tool-test-target");
        if (repair) state = state.withInventory(state.inventory().store(new ExactItemStack(target, home.id(), "minecraft:stone_pickaxe", 1,
                new InventoryCustody.ContainerSlot(depot, state.inventory().firstFreeSlot(depot).orElseThrow()), new ItemCondition(131, 100))));
        var permissions = SettlementWorkPolicy.permissions(state, home.id());
        var worker = state.humanPopulation().residents().values().stream()
                .filter(r -> r.settlementId().equals(home.id()) && permissions.permits(ResidentWorkKind.PRODUCTION, r.id()))
                .sorted(Comparator.comparing(ResidentProfile::id)).findFirst().orElseThrow();
        var recipe = state.bootstrap().ruleset().production().require(new ProductionRecipe.Id(
                repair ? "pale_mirror:repair_stone_pickaxe" : "pale_mirror:make_stone_pickaxe"));
        var job = StationWorkOrders.propose(state, task, facility, worker, ResidentWorkKind.PRODUCTION, recipe,
                new FungibleResourceCustodySupport.LotSelection(source, Map.of(material, recipe.ingredient().quantity())),
                repair ? Optional.of(new StationWorkState.MaintenanceTarget(target, depot, "minecraft:stone_pickaxe")) : Optional.empty(),
                ProductionRights.publicService(home.id()));
        state = ProductionProcess.reduceStarted(state, home.id(), new ProductionStarted(job, material,
                Optional.of(StationWorkAdmissionNavigation.propose(state, job))));
        return new Fixture(state, job, task, home, material, target);
    }
}
