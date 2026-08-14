package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.BuildingSlot;
import io.farfrontier.palemirror.api.BuildingSlotKind;
import io.farfrontier.palemirror.api.SettlementBuildingCategory;
import io.farfrontier.palemirror.api.VisualBounds;
import io.farfrontier.palemirror.api.VisualPoint;
import java.util.ArrayList;
import java.util.List;

/** Compiles catalog capacities into stable interior slot identities. */
final class SettlementBuildingSlots {
    private SettlementBuildingSlots() { }

    static List<BuildingSlot> forBuilding(SettlementArchetypeCatalog.Building specification,
                                          VisualBounds footprint) {
        List<BuildingSlot> result = new ArrayList<>();
        for (int index = 0; index < specification.housingCapacity(); index++) {
            result.add(slot("bed_" + index, BuildingSlotKind.BED, footprint, index));
        }
        for (int index = 0; index < specification.workCapacity(); index++) {
            result.add(slot("work_" + index, BuildingSlotKind.WORKSTATION, footprint,
                    specification.housingCapacity() + index));
        }
        int serviceIndex = specification.housingCapacity() + specification.workCapacity();
        if (specification.category() == SettlementBuildingCategory.LOGISTICS) {
            result.add(slot("storage", BuildingSlotKind.STORAGE, footprint, serviceIndex));
            result.add(slot("service", BuildingSlotKind.SERVICE, footprint, serviceIndex + 1));
        }
        if (specification.category() == SettlementBuildingCategory.DEFENCE) {
            result.add(slot("patrol", BuildingSlotKind.PATROL, footprint, serviceIndex));
        }
        if (result.isEmpty()) result.add(slot("gathering", BuildingSlotKind.GATHERING, footprint, 0));
        return List.copyOf(result);
    }

    private static BuildingSlot slot(String id, BuildingSlotKind kind, VisualBounds footprint, int index) {
        int width = Math.max(1, footprint.max().x() - footprint.min().x() - 3);
        int x = footprint.min().x() + 2 + index % width;
        int zSpan = Math.max(1, footprint.max().z() - footprint.min().z() - 3);
        int z = footprint.min().z() + 2 + index / width % zSpan;
        int y = Math.min(footprint.max().y(), footprint.min().y() + 1);
        return new BuildingSlot(id, kind, new VisualPoint(x, y, z), 1);
    }
}
