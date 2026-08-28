package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;

/** Fresh-world assembly kept outside the mutable-state aggregate. */
final class FrontierWorldInitialState {
    private FrontierWorldInitialState() { }

    static FrontierWorldState create(FrontierBootstrap bootstrap) {
        Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(); HumanPopulation population = HumanPopulation.bootstrap(bootstrap);
        bootstrap.settlements().forEach(settlement -> settlement.residents().forEach(resident -> actors.put(resident.id(), new ActorLocation(resident.home()))));
        bootstrap.hive().bioforms().forEach(bioform -> actors.put(bioform.id(), new ActorLocation(bioform.position())));
        Map<SubjectId, StructureCondition> structures = new LinkedHashMap<>();
        bootstrap.settlements().forEach(settlement -> settlement.structures().forEach(structure -> structures.put(structure.id(), StructureCondition.INTACT)));
        Map<SubjectId, ContainerRecord> containers = new LinkedHashMap<>();
        bootstrap.settlements().forEach(settlement -> { SubjectId id = FrontierWorldState.depotId(settlement.id()); containers.put(id, new ContainerRecord(id, settlement.id(), 27)); });
        bootstrap.hive().organs().forEach(organ -> organ.containerId().ifPresent(container -> containers.put(container, new ContainerRecord(container, bootstrap.hive().id(), 27))));
        containers.put(FrontierRouteNetwork.MAINTENANCE_CONTAINER, new ContainerRecord(FrontierRouteNetwork.MAINTENANCE_CONTAINER, FrontierRouteNetwork.OWNER, 27));
        SubjectId firstDepot = FrontierWorldState.depotId(bootstrap.settlements().getFirst().id()); Map<SubjectId, ExactItemStack> items = new LinkedHashMap<>();
        SubjectId wheat = new SubjectId("item:bootstrap-1-wheat"); items.put(wheat, new ExactItemStack(wheat, bootstrap.settlements().getFirst().id(), "minecraft:wheat", 64, new InventoryCustody.ContainerSlot(firstDepot, 0)));
        SubjectId biomass = new SubjectId("item:bootstrap-hive-biomass");
        items.put(biomass, new ExactItemStack(biomass, bootstrap.hive().id(), "minecraft:rotten_flesh", 64,
                new InventoryCustody.ContainerSlot(new SubjectId("container:hive-east-store"), 0)));
        Map<InfectionCell, FixedRatio> infection = new LinkedHashMap<>();
        bootstrap.hive().seedNests().forEach(nest -> infection.put(InfectionCell.at(nest.anchor()), new FixedRatio(new FixedScalar(500_000L))));
        return new FrontierWorldState(bootstrap, actors, structures, infection, new ExactInventory(containers, items, Map.of(), Map.of(), Map.of(), Map.of(), ContainerSurfaceManifest.initial(bootstrap)),
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), HiveColony.empty(), Map.of(), Map.of(), Map.of(), Map.of(), RouteTopology.initial(), StrategicPlanState.empty(), population);
    }
}
