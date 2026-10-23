package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Graybox composition of declared facility capabilities, machine sockets and optional local storage. */
public final class ProductionFacilityLayout {
    private record Definition(ProductionEquipment.Id equipment, ProductionStationSpec.Capability capability, OptionalInt maintenanceSlot) { }
    private static final Map<StructureKind, Definition> DEFINITIONS = Map.of(
            StructureKind.WORKSHOP, new Definition(ProductionEquipment.BENCH, ProductionStationSpec.Capability.BAKING, OptionalInt.empty()),
            StructureKind.SMELTERY, new Definition(ProductionEquipment.SMELTER, ProductionStationSpec.Capability.SMELTING, OptionalInt.empty()),
            StructureKind.FORGE, new Definition(ProductionEquipment.FORGE, ProductionStationSpec.Capability.FABRICATION, OptionalInt.of(3)));
    public static boolean supports(StructureKind kind) { return DEFINITIONS.containsKey(kind); }
    public static ProductionStationSpec station(SettlementStructure facility) {
        var definition = Optional.ofNullable(DEFINITIONS.get(facility.kind())).orElseThrow(
                () -> new IllegalArgumentException("facility has no declared production capability"));
        if (facility.kind() == StructureKind.WORKSHOP) return ProductionStationSpec.grayboxBakery(facility);
        var port = SettlementWorkshopServicePort.forWorkshop(facility);
        String suffix = facility.id().value().replace(':', '-');
        return new ProductionStationSpec(new SubjectId("station:" + suffix), facility.id(),
                new SubjectId("container:" + suffix + "-station"), definition.equipment(), definition.capability(),
                port.workStation(), facility.facing().step(port.workStation(), -1), 0, 1, 2, definition.maintenanceSlot());
    }
    public static SubjectId metalStorageId(SettlementStructure facility) {
        requireSmeltery(facility); return new SubjectId("container:" + facility.id().value().replace(':', '-') + "-metals");
    }
    public static SurfaceAnchor metalStorageStation(SettlementStructure facility) {
        requireSmeltery(facility);
        return facility.facing().stepRight(SettlementWorkshopServicePort.forWorkshop(facility).exteriorApproach(), 2);
    }
    public static BlockPosition metalStoragePosition(SettlementStructure facility) {
        return facility.facing().stepRight(metalStorageStation(facility), 1).support().offset(0, 1, 0);
    }
    public static ContainerRecord metalStorage(SettlementStructure facility) {
        var id = metalStorageId(facility); var access = metalStorageStation(facility);
        return new ContainerRecord(id, facility.settlementId(), 27, ContainerPurpose.RESOURCE_STORAGE, Optional.empty(),
                Optional.of(new ResourceStoragePolicy(new ShipmentEndpoint.Storage(facility.settlementId(), id, access,
                        Optional.empty(), access), new ResourceStorageFilter(false, Set.of("minecraft:coal"),
                        Map.of("pale_mirror:ores", Set.of("minecraft:raw_iron"),
                                "pale_mirror:ingots", Set.of("minecraft:iron_ingot"))), 100, ResourceStorageRole.WAREHOUSE)));
    }
    private static void requireSmeltery(SettlementStructure facility) {
        if (facility.kind() != StructureKind.SMELTERY) throw new IllegalArgumentException("not a declared smeltery");
    }
    private ProductionFacilityLayout() { }
}
