package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Graybox composition of declared facility capabilities, machine sockets and optional local storage. */
public final class ProductionFacilityLayout {
    private static final ImmutableResultView<String, List<ProductionStationSpec>> STATIONS =
            new ImmutableResultView<>(SimulationCapacity.MAX_SETTLEMENTS * 6);
    private record Definition(ProductionStationSpec.Capability capability, OptionalInt fuelSlot, OptionalInt maintenanceSlot) { }
    private static final Map<ProductionEquipment.Id, Definition> EQUIPMENT = Map.of(
            ProductionEquipment.BENCH, new Definition(ProductionStationSpec.Capability.BAKING, OptionalInt.of(10), OptionalInt.empty()),
            ProductionEquipment.SMELTER, new Definition(ProductionStationSpec.Capability.SMELTING, OptionalInt.of(10), OptionalInt.empty()),
            ProductionEquipment.FORGE, new Definition(ProductionStationSpec.Capability.FABRICATION, OptionalInt.of(10), OptionalInt.of(11)),
            ProductionEquipment.WOODWORKING, new Definition(ProductionStationSpec.Capability.WOODWORKING, OptionalInt.empty(), OptionalInt.empty()),
            ProductionEquipment.SPINNING, new Definition(ProductionStationSpec.Capability.SPINNING, OptionalInt.empty(), OptionalInt.empty()),
            ProductionEquipment.WEAVING, new Definition(ProductionStationSpec.Capability.WEAVING, OptionalInt.empty(), OptionalInt.empty()));
    private static final Map<StructureKind, Set<ProductionEquipment.Id>> FACILITIES = Map.of(
            StructureKind.WORKSHOP, Set.of(ProductionEquipment.BENCH), StructureKind.SMELTERY, Set.of(ProductionEquipment.SMELTER),
            StructureKind.FORGE, Set.of(ProductionEquipment.FORGE), StructureKind.WOODSHOP, Set.of(ProductionEquipment.WOODWORKING),
            StructureKind.TEXTILE_WORKSHOP, Set.of(ProductionEquipment.SPINNING, ProductionEquipment.WEAVING));
    private static final Map<StructureKind, ProductionEquipment.Id> SINGLE_EQUIPMENT = Map.of(
            StructureKind.WORKSHOP, ProductionEquipment.BENCH, StructureKind.SMELTERY, ProductionEquipment.SMELTER,
            StructureKind.FORGE, ProductionEquipment.FORGE, StructureKind.WOODSHOP, ProductionEquipment.WOODWORKING);
    public static ProductionEquipment.Id equipmentType(StructureKind kind) {
        var declared = SINGLE_EQUIPMENT.get(kind);
        if (declared == null) throw new IllegalArgumentException("facility has no explicit single equipment declaration");
        return declared;
    }
    public static boolean supports(StructureKind kind) { return FACILITIES.containsKey(kind); }
    public static List<ProductionStationSpec> stations(SettlementStructure facility) {
        return STATIONS.get(List.of(facility), "stations", () -> facility.definition().workplaces().values().stream()
                .sorted(Comparator.comparing(io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingDefinition.Workplace::id))
                .map(mount -> createStation(facility, mount)).toList());
    }
    public static ProductionStationSpec station(SettlementStructure facility) {
        var declared = stations(facility);
        if (declared.size() != 1) throw new IllegalArgumentException("single station requested for a multi-workplace facility");
        return declared.getFirst();
    }
    public static ProductionStationSpec station(SettlementStructure facility, ProductionStationSpec.WorkplaceId workplace) {
        var mount = facility.definition().workplaces().get(workplace.value());
        if (mount == null) throw new IllegalArgumentException("unknown declared workplace " + workplace.value());
        return createStation(facility, mount);
    }
    private static ProductionStationSpec createStation(SettlementStructure facility,
            io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingDefinition.Workplace mount) {
        if (!Objects.requireNonNull(FACILITIES.get(facility.kind()), "undeclared production facility").contains(mount.equipmentType()))
            throw new IllegalArgumentException("authored equipment differs from facility capability");
        var definition = Objects.requireNonNull(EQUIPMENT.get(mount.equipmentType()), "undeclared equipment");
        String suffix = facility.id().value().replace(':', '-');
        if (facility.kind() == StructureKind.WORKSHOP) suffix += "-bakery";
        else if (facility.definition().workplaces().size() > 1) suffix += "-" + mount.id();
        return new ProductionStationSpec(new SubjectId("station:" + suffix), facility.id(),
                new SubjectId("container:" + suffix + "-station"), mount.equipmentType(), definition.capability(),
                new ProductionStationSpec.WorkplaceId(mount.id()), facility.placement().support(mount.worker()),
                facility.placement().support(mount.socket()), facility.placement().direction(mount.facing()),
                java.util.stream.IntStream.range(0, 9).boxed().toList(), 9, definition.fuelSlot(), definition.maintenanceSlot());
    }
    public static SubjectId metalStorageId(SettlementStructure facility) {
        requireSmeltery(facility); return new SubjectId("container:" + facility.id().value().replace(':', '-') + "-metals");
    }
    public static SurfaceAnchor metalStorageStation(SettlementStructure facility) {
        requireSmeltery(facility);
        if(facility.definition().representation()==io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingDefinition.Representation.AUTHORED)
            return facility.placement().support(facility.definition().anchor("metal_station"));
        return facility.facing().stepRight(SettlementWorkshopServicePort.forWorkshop(facility).exteriorApproach(), 2);
    }
    public static BlockPosition metalStoragePosition(SettlementStructure facility) {
        if(facility.definition().representation()==io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingDefinition.Representation.AUTHORED)
            return facility.placement().world(facility.definition().anchor("metal_storage")).offset(0,1,0);
        return facility.facing().stepRight(metalStorageStation(facility), 1).support().offset(0, 1, 0);
    }
    public static ContainerRecord metalStorage(SettlementStructure facility) {
        var id = metalStorageId(facility); var access = metalStorageStation(facility);
        return ContainerRecord.storage(id, facility.settlementId(), 27, io.farfrontier.palemirror.frontier.v3.model.StorageBlockKind.CHEST, new ResourceStoragePolicy(new ShipmentEndpoint.Storage(facility.settlementId(), id, access,
                        Optional.of(facility.id()), access), new ResourceStorageFilter(false, Set.of("minecraft:coal"),
                        Map.of("pale_mirror:ores", Set.of("minecraft:raw_iron"),
                                "pale_mirror:ingots", Set.of("minecraft:iron_ingot"))), 100, ResourceStorageRole.WAREHOUSE));
    }
    private static void requireSmeltery(SettlementStructure facility) {
        if (facility.kind() != StructureKind.SMELTERY) throw new IllegalArgumentException("not a declared smeltery");
    }
    private ProductionFacilityLayout() { }
}
