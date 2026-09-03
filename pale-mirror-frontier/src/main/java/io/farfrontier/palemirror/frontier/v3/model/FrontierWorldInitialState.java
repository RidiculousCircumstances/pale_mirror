package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fresh-world assembly kept outside the mutable-state aggregate. */
final class FrontierWorldInitialState {
    private FrontierWorldInitialState() { }

    static FrontierWorldState create(FrontierBootstrap bootstrap) {
        Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(); HumanPopulation population = HumanPopulation.bootstrap(bootstrap);
        bootstrap.settlements().forEach(settlement -> settlement.residents().forEach(resident -> actors.put(resident.id(), ActorLocation.standingOn(new SurfaceAnchor(resident.home())))));
        HiveColony colony = HiveColony.empty().withBioformLifecycles(initialBioformLifecycles(bootstrap));
        bootstrap.hive().bioforms().forEach(bioform -> {
            BioformLifecycle lifecycle = colony.bioformLifecycles().get(bioform.id());
            if (lifecycle.phase().occupiesCocoon()) {
                HiveCocoonSlot slot = lifecycle.homeSlot().orElseThrow();
                actors.put(bioform.id(), ActorLocation.standingOn(new SurfaceAnchor(HiveCocoonPlan.cocoonCell(organ(bootstrap, slot.hibernaculumId()), slot))));
            } else {
                actors.put(bioform.id(), ActorLocation.standingOn(new SurfaceAnchor(bioform.position())));
            }
        });
        Map<SubjectId, StructureCondition> structures = new LinkedHashMap<>();
        bootstrap.settlements().forEach(settlement -> settlement.structures().forEach(structure -> structures.put(structure.id(), StructureCondition.INTACT)));
        Map<SubjectId, ContainerRecord> containers = new LinkedHashMap<>();
        bootstrap.settlements().forEach(settlement -> { SubjectId id = FrontierWorldState.depotId(settlement.id()); containers.put(id, new ContainerRecord(id, settlement.id(), 27)); });
        bootstrap.hive().organs().forEach(organ -> organ.containerId().ifPresent(container -> containers.put(container, new ContainerRecord(container, bootstrap.hive().id(), 27))));
        containers.put(FrontierRouteNetwork.MAINTENANCE_CONTAINER, new ContainerRecord(FrontierRouteNetwork.MAINTENANCE_CONTAINER, FrontierRouteNetwork.OWNER, 27));
        SubjectId firstDepot = FrontierWorldState.depotId(bootstrap.settlements().getFirst().id()); Map<SubjectId, ExactItemStack> items = new LinkedHashMap<>();
        SubjectId wheat = new SubjectId("item:bootstrap-1-wheat"); items.put(wheat, new ExactItemStack(wheat, bootstrap.settlements().getFirst().id(), "minecraft:wheat", 64, new InventoryCustody.ContainerSlot(firstDepot, 0)));
        bootstrap.settlements().forEach(settlement -> {
            SubjectId depot = FrontierWorldState.depotId(settlement.id());
            for (int index = 0; index < EngineeringRecoveryTeam.MAX_MEMBERS; index++) {
                SubjectId tool = new SubjectId("item:bootstrap-" + settlement.id().value().substring("settlement:".length()) + "-engineering-tool-" + (index + 1));
                items.put(tool, new ExactItemStack(tool, settlement.id(), EngineeringToolCustody.FIRST_GRAYBOX_TOOL, 1,
                        new InventoryCustody.ContainerSlot(depot, 20 + index)));
            }
        });
        SubjectId biomass = new SubjectId("item:bootstrap-hive-biomass");
        items.put(biomass, new ExactItemStack(biomass, bootstrap.hive().id(), "minecraft:rotten_flesh", 64,
                new InventoryCustody.ContainerSlot(new SubjectId("container:hive-east-store"), 0)));
        Map<InfectionCell, FixedRatio> infection = new LinkedHashMap<>();
        bootstrap.hive().seedNests().forEach(nest -> seedInfection(infection, InfectionCell.at(nest.anchor())));
        return new FrontierWorldState(bootstrap, actors, structures, infection, new ExactInventory(containers, items, Map.of(), Map.of(), Map.of(), Map.of(),
                ContainerSurfaceManifest.initial(bootstrap), EconomicLedger.bootstrap(bootstrap)),
                Map.of(), Map.of(), Map.of(), LogisticsHistory.empty(), Map.of(), Map.of(), Map.of(), colony,
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), RouteTopology.initial(), StrategicPlanState.empty(), population, CompanyRegistry.empty(),
                ResourceSiteState.initial(bootstrap));
    }

    private static Map<SubjectId, BioformLifecycle> initialBioformLifecycles(FrontierBootstrap bootstrap) {
        Map<SubjectId, BioformLifecycle> lifecycles = new LinkedHashMap<>();
        for (HiveNest nest : bootstrap.hive().seedNests()) {
            List<HiveOrgan> hibernacula = bootstrap.hive().organs().stream()
                    .filter(organ -> organ.nestId().equals(nest.id()) && organ.kind() == HiveOrganKind.HIBERNACULUM)
                    .sorted(java.util.Comparator.comparing(HiveOrgan::id)).toList();
            int slotOrdinal = 0;
            for (Bioform bioform : bootstrap.hive().bioforms().stream().filter(value -> value.nestId().equals(nest.id())).sorted(java.util.Comparator.comparing(Bioform::id)).toList()) {
                HiveCocoonSlot slot = new HiveCocoonSlot(hibernacula.get(slotOrdinal / HiveCocoonSlot.MAX_SLOTS_PER_HIBERNACULUM).id(),
                        slotOrdinal % HiveCocoonSlot.MAX_SLOTS_PER_HIBERNACULUM);
                lifecycles.put(bioform.id(), HivePhysiologySupport.initiallyDeployed(bootstrap.hive(), bioform)
                        ? BioformLifecycle.active(slot) : BioformLifecycle.dormant(slot));
                slotOrdinal++;
            }
        }
        return Map.copyOf(lifecycles);
    }

    private static HiveOrgan organ(FrontierBootstrap bootstrap, SubjectId organId) {
        return bootstrap.hive().organs().stream().filter(organ -> organ.id().equals(organId)).findFirst()
                .orElseThrow(() -> new IllegalStateException("bootstrap cocoon references an absent HIBERNACULUM"));
    }

    /**
     * A seed nest begins as an actual local infection territory, not as one roof-sized marker.
     *
     * <p>Every member is still one exact four-by-four canonical cell. The nine-cell cluster
     * gives a player a readable twelve-by-twelve alien surface from the first natural visit,
     * while retaining meaningful growth: the hive's ordinary expansion task can only advance
     * from the cluster's outer cells. The centre/pulse/palisade values are simulation state,
     * not presentation-only colour choices.</p>
     */
    private static void seedInfection(Map<InfectionCell, FixedRatio> infection, InfectionCell centre) {
        for (int localX = -1; localX <= 1; localX++) for (int localZ = -1; localZ <= 1; localZ++) {
            long intensity = localX == 0 && localZ == 0 ? 750_000L
                    : localX == 0 || localZ == 0 ? 500_000L : 250_000L;
            InfectionCell cell = new InfectionCell(Math.addExact(centre.x(), localX), Math.addExact(centre.z(), localZ));
            if (infection.put(cell, new FixedRatio(new FixedScalar(intensity))) != null) {
                throw new IllegalArgumentException("overlapping hive seed infection cells");
            }
        }
    }
}
