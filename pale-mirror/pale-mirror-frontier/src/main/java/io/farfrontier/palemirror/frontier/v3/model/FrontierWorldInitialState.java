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
        bootstrap.settlements().forEach(settlement -> settlement.residents().forEach(resident -> actors.put(resident.id(), ActorLocation.standingOn(new SurfaceAnchor(resident.home()), ActorKind.RESIDENT))));
        HiveColony colony = HiveColony.empty().withBioformLifecycles(initialBioformLifecycles(bootstrap));
        bootstrap.hive().bioforms().forEach(bioform -> {
            BioformLifecycle lifecycle = colony.bioformLifecycles().get(bioform.id());
            if (lifecycle.phase().occupiesCocoon()) {
                HiveCocoonSlot slot = lifecycle.homeSlot().orElseThrow();
                actors.put(bioform.id(), ActorLocation.standingOn(new SurfaceAnchor(HiveCocoonPlan.cocoonCell(organ(bootstrap, slot.hibernaculumId()), slot)), ActorKind.BIOFORM));
            } else {
                actors.put(bioform.id(), ActorLocation.standingOn(new SurfaceAnchor(bioform.position()), ActorKind.BIOFORM));
            }
        });
        var fleet = TransportFleetBootstrap.initial(bootstrap);
        fleet.assets().values().forEach(asset -> actors.put(asset.actorId(),
                ActorLocation.standingOn(asset.homeStation(), ActorKind.PACK_ANIMAL)));
        Map<SubjectId, StructureCondition> structures = new LinkedHashMap<>();
        bootstrap.settlements().forEach(settlement -> settlement.structures().forEach(structure -> structures.put(structure.id(), StructureCondition.INTACT)));
        Map<SubjectId, ContainerRecord> containers = new LinkedHashMap<>();
        bootstrap.settlements().forEach(settlement -> { SubjectId id = FrontierWorldState.depotId(settlement.id()); containers.put(id, new ContainerRecord(id, settlement.id(), 27)); });
        bootstrap.settlements().forEach(settlement -> settlement.structures().stream()
                .filter(structure -> structure.kind() == StructureKind.WORKSHOP).findFirst().ifPresent(workshop -> {
                    ProductionStationSpec station = ProductionStationSpec.grayboxBakery(workshop);
                    containers.put(station.containerId(), new ContainerRecord(station.containerId(), settlement.id(), 27,
                            java.util.Optional.of(station)));
                }));
        bootstrap.hive().organs().forEach(organ -> organ.containerId().ifPresent(container -> containers.put(container, new ContainerRecord(container, bootstrap.hive().id(), 27))));
        containers.put(FrontierRouteNetwork.MAINTENANCE_CONTAINER, new ContainerRecord(FrontierRouteNetwork.MAINTENANCE_CONTAINER, FrontierRouteNetwork.OWNER, 27));
        fleet.assets().values().forEach(asset -> containers.put(asset.containerId(),
                new ContainerRecord(asset.containerId(), asset.homeSettlementId(), asset.stackSlots())));
        var surfaces = new LinkedHashMap<>(ContainerSurfaceManifest.initial(bootstrap));
        fleet.assets().values().forEach(asset -> surfaces.put(asset.containerId(),
                new ContainerSurface(asset.containerId(), new ContainerLocation.Mobile(asset.actorId()),
                        ContainerSurfaceStatus.UNMATERIALIZED)));
        SubjectId firstDepot = FrontierWorldState.depotId(bootstrap.settlements().getFirst().id()); Map<SubjectId, ExactItemStack> items = new LinkedHashMap<>();
        SubjectId wheat = new SubjectId("lot:bootstrap-1-wheat");
        FungibleResourceLedger resources = FungibleResourceLedger.empty().issue(new ResourceLot(wheat, bootstrap.settlements().getFirst().id(), "minecraft:wheat", 64,
                "bootstrap", List.of()), new CustodyAccount(new SubjectId("custody:container-1-depot"), new ResourceCustody.Container(firstDepot), Map.of(wheat, 64), Map.of()));
        // Settlement one demonstrates the wheat-to-bread production vertical. Every other
        // graybox settlement needs an explicit, finite starter food lot: its first field
        // matures during FREE, and at the next WORK boundary the new personal hunger rule
        // would otherwise block the very first harvest with no local bread source.
        for (Settlement settlement : bootstrap.settlements().subList(1, bootstrap.settlements().size())) {
            String suffix = settlement.id().value().substring("settlement:".length());
            SubjectId bread = new SubjectId("lot:bootstrap-" + suffix + "-bread");
            SubjectId depot = FrontierWorldState.depotId(settlement.id());
            resources = resources.issue(new ResourceLot(bread, settlement.id(), "minecraft:bread", 64,
                    "bootstrap-starter-food", List.of()), new CustodyAccount(
                    new SubjectId("custody:container-" + suffix + "-depot"),
                    new ResourceCustody.Container(depot), Map.of(bread, 64), Map.of()));
        }
        bootstrap.settlements().forEach(settlement -> {
            SubjectId depot = FrontierWorldState.depotId(settlement.id());
            for (int index = 0; index < EngineeringRecoveryTeam.MAX_MEMBERS; index++) {
                SubjectId tool = new SubjectId("item:bootstrap-" + settlement.id().value().substring("settlement:".length()) + "-engineering-tool-" + (index + 1));
                items.put(tool, new ExactItemStack(tool, settlement.id(), EngineeringToolCustody.FIRST_GRAYBOX_TOOL, 1,
                        new InventoryCustody.ContainerSlot(depot, 20 + index)));
            }
        });
        int stockIndex = 0;
        for (var stock : bootstrap.ruleset().initialSettlementStocks().stream()
                .sorted(java.util.Comparator.comparing(InitialSettlementStock::canonicalText)).toList()) {
            var settlement = FrontierWorldStateSupport.settlement(bootstrap, stock.settlementId());
            SubjectId depot = FrontierWorldState.depotId(settlement.id());
            SubjectId lot = new SubjectId("lot:initial-public-stock-" + ++stockIndex);
            SubjectId staging = new SubjectId("custody:initial-public-stock-" + stockIndex);
            resources = resources.issue(new ResourceLot(lot, settlement.id(), stock.itemKind(), stock.quantity(),
                    "bootstrap-initial-public-stock", List.of()), new CustodyAccount(staging,
                    new ResourceCustody.Container(depot), Map.of(lot, stock.quantity()), Map.of()));
            resources = resources.transfer(staging, ReferenceContainerCustody.scopeId(depot), Map.of(lot, stock.quantity()), Map.of());
        }
        SubjectId biomass = new SubjectId("lot:bootstrap-hive-biomass"); SubjectId eastStore = new SubjectId("container:hive-east-store");
        resources = resources.issue(new ResourceLot(biomass, bootstrap.hive().id(), "minecraft:rotten_flesh", 64, "bootstrap", List.of()),
                new CustodyAccount(new SubjectId("custody:container-hive-east-store"), new ResourceCustody.Container(eastStore), Map.of(biomass, 64), Map.of()));
        Map<InfectionCell, FixedRatio> infection = new LinkedHashMap<>();
        bootstrap.hive().seedNests().forEach(nest -> seedInfection(infection, InfectionCell.at(nest.anchor())));
        return new FrontierWorldState(bootstrap, actors, structures, infection, new ExactInventory(containers, items, Map.of(), Map.of(), Map.of(), Map.of(),
                surfaces, EconomicLedger.bootstrap(bootstrap),
                        resources), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), colony, Map.of(), Map.of(), Map.of(),
                        Map.of(), Map.of(), RouteTopology.initial(), StrategicPlanState.initial(bootstrap), population,
                        CompanyRegistry.empty().withGoodsTrade(GoodsTradeState.empty().withParticipants(GoodsParticipantDeclarations.initial(bootstrap))),
                        ResourceSiteState.initial(bootstrap), PhysicalReplicaCustodyState.empty(),
                        DeferredAftermathState.empty(), FencedRecoveryState.empty(), DiagnosticIncidentIndex.empty(),
                        Map.of(), io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionState.empty(),
                        ShipmentState.empty(), io.farfrontier.palemirror.frontier.v3.model.group.UnitGroupState.empty(), fleet);
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
